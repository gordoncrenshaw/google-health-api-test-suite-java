package com.google.health.testsuite.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.health.testsuite.config.ConfigManager;
import com.google.health.testsuite.config.Preferences;
import com.google.health.testsuite.config.UserAuthorization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Manages Google OAuth 2.0 authorization, credential persistence,
 * and automatic token refreshing using com.google.api.client.auth.oauth2.Credential.
 */
public final class OAuthService {

    private static final Logger logger = LoggerFactory.getLogger(OAuthService.class);
    private static final String DEFAULT_CREDENTIALS_DIR = "tokens";
    private static final String FALLBACK_CREDENTIALS_DIR = ".credentials";
    private static final String USER_ID = "me";

    private final ConfigManager configManager;
    private final File credentialsDir;
    private GoogleAuthorizationCodeFlow flow;
    private Credential credential;

    public OAuthService(ConfigManager configManager) {
        this(configManager, resolveCredentialsDir());
    }

    public OAuthService(ConfigManager configManager, File credentialsDir) {
        this.configManager = configManager;
        this.credentialsDir = credentialsDir;
        if (configManager != null) {
            configManager.setOAuthService(this);
        }
        initFlow();
        migrateLegacyUserAuthorization();
    }

    private static File resolveCredentialsDir() {
        File dotCreds = new File(FALLBACK_CREDENTIALS_DIR);
        if (dotCreds.exists() && dotCreds.isDirectory()) {
            return dotCreds;
        }
        return new File(DEFAULT_CREDENTIALS_DIR);
    }

    /**
     * Initializes the GoogleAuthorizationCodeFlow backed by FileDataStoreFactory.
     */
    public synchronized void initFlow() {
        Preferences prefs = configManager != null ? configManager.getPreferences() : new Preferences();
        List<String> scopes = (prefs.getScopes() != null && !prefs.getScopes().isEmpty())
                ? prefs.getScopes()
                : List.of("https://www.googleapis.com/auth/googlehealth.activity_and_fitness.readonly");

        GoogleClientSecrets clientSecrets = null;
        File secretFile = configManager != null ? configManager.resolveClientSecretFile() : null;
        if (secretFile != null && secretFile.exists() && secretFile.length() > 0) {
            try (FileReader reader = new FileReader(secretFile)) {
                clientSecrets = GoogleClientSecrets.load(GsonFactory.getDefaultInstance(), reader);
                logger.debug("Loaded GoogleClientSecrets from {}", secretFile.getAbsolutePath());
            } catch (Exception e) {
                logger.warn("Could not load GoogleClientSecrets directly from {}: {}", secretFile.getName(), e.getMessage());
            }
        }

        if (clientSecrets == null || clientSecrets.getDetails() == null) {
            GoogleClientSecrets.Details details = new GoogleClientSecrets.Details();
            String cid = prefs.getClientId();
            if (cid == null || cid.trim().isEmpty()) {
                cid = "mock-client-id.apps.googleusercontent.com";
            }
            String csec = prefs.getClientSecret();
            if (csec == null || csec.trim().isEmpty()) {
                csec = "mock-client-secret";
            }
            details.setClientId(cid);
            details.setClientSecret(csec);
            if (prefs.getAuthUri() != null && !prefs.getAuthUri().isEmpty()) {
                details.setAuthUri(prefs.getAuthUri());
            }
            if (prefs.getTokenUri() != null && !prefs.getTokenUri().isEmpty()) {
                details.setTokenUri(prefs.getTokenUri());
            }
            clientSecrets = new GoogleClientSecrets();
            clientSecrets.setWeb(details);
        }

        try {
            if (!credentialsDir.exists()) {
                credentialsDir.mkdirs();
            }
            FileDataStoreFactory dataStoreFactory = new FileDataStoreFactory(credentialsDir);
            this.flow = new GoogleAuthorizationCodeFlow.Builder(
                    new NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    clientSecrets,
                    scopes)
                    .setDataStoreFactory(dataStoreFactory)
                    .setAccessType("offline")
                    .setApprovalPrompt("force")
                    .build();

            // Try to load any existing stored credential
            this.credential = flow.loadCredential(USER_ID);
            logger.info("Initialized GoogleAuthorizationCodeFlow with Credential store in '{}'. Existing credential: {}",
                    credentialsDir.getName(), (credential != null && credential.getAccessToken() != null ? "Found" : "None"));
        } catch (IOException e) {
            logger.error("Failed to initialize GoogleAuthorizationCodeFlow: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize GoogleAuthorizationCodeFlow", e);
        }
    }

    /**
     * One-time automatic migration of any legacy userAuthorization.yaml file to Credential store.
     * Deletes the legacy file upon successful migration.
     */
    private synchronized void migrateLegacyUserAuthorization() {
        File legacyFile = new File("config", "userAuthorization.yaml");
        if (!legacyFile.exists()) {
            return;
        }

        try {
            Credential existing = flow.loadCredential(USER_ID);
            if (existing == null || existing.getAccessToken() == null || existing.getAccessToken().isEmpty()) {
                ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
                JsonNode node = yamlMapper.readTree(legacyFile);
                if (node != null && node.has("accessToken")) {
                    String accessToken = node.path("accessToken").asText("");
                    String refreshToken = node.path("refreshToken").asText("");
                    long expiresAtEpochMs = node.path("expiresAtEpochMs").asLong(0);
                    String scope = node.path("scope").asText("");
                    String healthUserId = node.path("healthUserID").asText("");

                    if (!accessToken.isEmpty()) {
                        long remainingSec = 3600;
                        if (expiresAtEpochMs > 0) {
                            remainingSec = Math.max(0, (expiresAtEpochMs - System.currentTimeMillis()) / 1000);
                        }
                        TokenResponse tokenResponse = new TokenResponse()
                                .setAccessToken(accessToken)
                                .setRefreshToken(refreshToken)
                                .setExpiresInSeconds(remainingSec)
                                .setTokenType("Bearer")
                                .setScope(scope);

                        this.credential = flow.createAndStoreCredential(tokenResponse, USER_ID);
                        logger.info("Migrated legacy credentials into com.google.api.client.auth.oauth2.Credential store.");

                        // Preserve healthUserId in preferences.yaml if present
                        if (configManager != null && healthUserId != null && !healthUserId.isEmpty() && !"me".equalsIgnoreCase(healthUserId)) {
                            Preferences prefs = configManager.getPreferences();
                            if (prefs.getHealthUserId() == null || prefs.getHealthUserId().isEmpty()) {
                                prefs.setHealthUserId(healthUserId);
                                configManager.savePreferences(prefs);
                            }
                        }
                    }
                }
            }
            if (legacyFile.delete()) {
                logger.info("Deleted legacy {} from disk.", legacyFile.getPath());
            }
            File exampleFile = new File("config", "userAuthorization.example.yaml");
            if (exampleFile.exists()) {
                exampleFile.delete();
            }
        } catch (Exception e) {
            logger.warn("Could not migrate legacy {}: {}", legacyFile.getPath(), e.getMessage());
        }
    }

    /**
     * Builds the Google OAuth 2.0 authorization URL using default configured scopes.
     */
    public String buildAuthorizationUrl(String state) {
        return buildAuthorizationUrl(state, null);
    }

    /**
     * Builds the Google OAuth 2.0 authorization URL with explicitly specified scopes.
     * If scopes is null, uses the flow's configured scopes.
     * If scopes is provided, the authorization URL will request only those scopes.
     */
    public String buildAuthorizationUrl(String state, List<String> scopes) {
        Preferences prefs = configManager != null ? configManager.getPreferences() : new Preferences();
        String stateParam = (state != null && !state.isEmpty()) ? state : UUID.randomUUID().toString();

        var authUrl = flow.newAuthorizationUrl()
                .setRedirectUri(prefs.getRedirectUri())
                .setState(stateParam);

        if (scopes != null) {
            authUrl.setScopes(scopes);
        }
        return authUrl.build();
    }

    /**
     * Interactive OAuth authorization using LocalServerReceiver and AuthorizationCodeInstalledApp.
     */
    public synchronized Credential authorizeInteractive(int port, String path) throws IOException {
        Preferences prefs = configManager != null ? configManager.getPreferences() : new Preferences();
        if (prefs.isMockMode()) {
            exchangeCodeForTokens("mock_auth_code");
            return getCredential();
        }

        LocalServerReceiver receiver = new LocalServerReceiver.Builder()
                .setHost("localhost")
                .setPort(port > 0 ? port : 8888)
                .setCallbackPath((path != null && !path.isEmpty()) ? path : "/callback")
                .build();

        AuthorizationCodeInstalledApp app = new AuthorizationCodeInstalledApp(flow, receiver);
        this.credential = app.authorize(USER_ID);
        logger.info("Interactive OAuth authorization successful. Credential saved to: {}",
                credentialsDir.getAbsolutePath());
        return this.credential;
    }

    /**
     * Exchanges an authorization code for tokens and persists them into the Credential store.
     */
    private String lastAuthError = "";

    public String getLastAuthError() {
        return lastAuthError;
    }

    public synchronized boolean exchangeCodeForTokens(String authorizationCode) {
        Preferences prefs = configManager != null ? configManager.getPreferences() : new Preferences();

        if (prefs.isMockMode()) {
            logger.info("[Mock Mode] Simulating authorization code exchange with Credential.");
            TokenResponse mockResponse = new TokenResponse()
                    .setAccessToken("mock_access_token_" + UUID.randomUUID().toString().substring(0, 8))
                    .setRefreshToken("mock_refresh_token_" + UUID.randomUUID().toString().substring(0, 8))
                    .setExpiresInSeconds(3600L)
                    .setTokenType("Bearer")
                    .setScope(prefs.getJoinedScopes());
            try {
                this.credential = flow.createAndStoreCredential(mockResponse, USER_ID);
                this.lastAuthError = "";
                return true;
            } catch (IOException e) {
                logger.error("Failed to store mock Credential: {}", e.getMessage(), e);
                this.lastAuthError = e.getMessage();
                return false;
            }
        }

        try {
            this.lastAuthError = "";
            TokenResponse response = flow.newTokenRequest(authorizationCode)
                    .setRedirectUri(prefs.getRedirectUri())
                    .execute();

            this.credential = flow.createAndStoreCredential(response, USER_ID);
            logger.info("Successfully exchanged authorization code for Credential!");
            return true;
        } catch (com.google.api.client.auth.oauth2.TokenResponseException tre) {
            String err = (tre.getDetails() != null && tre.getDetails().getError() != null)
                    ? tre.getDetails().getError() : "HTTP " + tre.getStatusCode();
            String desc = (tre.getDetails() != null && tre.getDetails().getErrorDescription() != null)
                    ? tre.getDetails().getErrorDescription() : tre.getMessage();
            this.lastAuthError = "Google OAuth error (" + err + "): " + desc;
            logger.error("TokenResponseException during authorization code exchange (HTTP {}): {} - {}",
                    tre.getStatusCode(), err, desc);
            return false;
        } catch (Exception e) {
            this.lastAuthError = "Exception: " + e.getMessage();
            logger.error("Exception during authorization code exchange: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Refreshes the access token using com.google.api.client.auth.oauth2.Credential.
     */
    public synchronized boolean refreshAccessToken() {
        Preferences prefs = configManager != null ? configManager.getPreferences() : new Preferences();

        if (prefs.isMockMode()) {
            logger.info("[Mock Mode] Simulating access token refresh with Credential.");
            Credential cred = getCredential();
            try {
                if (cred != null) {
                    cred.setAccessToken("mock_access_token_" + UUID.randomUUID().toString().substring(0, 8));
                    cred.setExpiresInSeconds(3600L);
                    flow.getCredentialDataStore().set(USER_ID, new StoredCredential(cred));
                } else {
                    TokenResponse mockResponse = new TokenResponse()
                            .setAccessToken("mock_access_token_" + UUID.randomUUID().toString().substring(0, 8))
                            .setRefreshToken("mock_refresh_token_" + UUID.randomUUID().toString().substring(0, 8))
                            .setExpiresInSeconds(3600L)
                            .setTokenType("Bearer")
                            .setScope(prefs.getJoinedScopes());
                    this.credential = flow.createAndStoreCredential(mockResponse, USER_ID);
                }
                return true;
            } catch (IOException e) {
                logger.error("Failed to update mock Credential: {}", e.getMessage(), e);
                return false;
            }
        }

        Credential cred = getCredential();
        if (cred == null || cred.getRefreshToken() == null || cred.getRefreshToken().trim().isEmpty()) {
            logger.warn("Cannot refresh access token: No refresh token present in stored Credential.");
            return false;
        }

        try {
            logger.info("Refreshing access token via com.google.api.client.auth.oauth2.Credential...");
            boolean refreshed = cred.refreshToken();
            if (refreshed) {
                logger.info("Credential refreshed successfully! New expiry: {}s remaining.", cred.getExpiresInSeconds());
                return true;
            } else {
                logger.warn("Credential.refreshToken() returned false.");
                return false;
            }
        } catch (IOException e) {
            logger.error("Exception during Credential refresh: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Programmatically stores credentials directly into the Credential store.
     */
    public synchronized void storeCredential(String accessToken, String refreshToken, long expiresInSeconds, String scope) {
        TokenResponse response = new TokenResponse()
                .setAccessToken(accessToken)
                .setRefreshToken(refreshToken)
                .setExpiresInSeconds(expiresInSeconds > 0 ? expiresInSeconds : 3600L)
                .setTokenType("Bearer")
                .setScope(scope);
        try {
            this.credential = flow.createAndStoreCredential(response, USER_ID);
            logger.info("Saved credentials into Credential store ('{}').", USER_ID);
        } catch (IOException e) {
            logger.error("Failed to store Credential: {}", e.getMessage(), e);
        }
    }

    /**
     * Returns the loaded com.google.api.client.auth.oauth2.Credential.
     */
    public synchronized Credential getCredential() {
        try {
            if (credential == null) {
                credential = flow.loadCredential(USER_ID);
            }
            return credential;
        } catch (IOException e) {
            logger.error("Failed to load Credential: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Checks if a valid saved token exists in the Credential store.
     */
    public synchronized boolean hasValidCredential() {
        Credential cred = getCredential();
        return cred != null && cred.getAccessToken() != null && !cred.getAccessToken().trim().isEmpty();
    }

    /**
     * Checks if the Credential access token is expired or within 60 seconds of expiring.
     */
    public boolean isExpired(Credential cred) {
        if (cred == null || cred.getAccessToken() == null || cred.getAccessToken().trim().isEmpty()) {
            return true;
        }
        Long expiresIn = cred.getExpiresInSeconds();
        if (expiresIn != null) {
            return expiresIn < 60;
        }
        Long expMs = cred.getExpirationTimeMilliseconds();
        if (expMs != null && expMs > 0) {
            return System.currentTimeMillis() >= (expMs - 60_000);
        }
        return false;
    }

    /**
     * Builds an in-memory UserAuthorization snapshot from the current Credential and Preferences.
     */
    public synchronized UserAuthorization getUserAuthorization() {
        Credential cred = getCredential();
        Preferences prefs = configManager != null ? configManager.getPreferences() : new Preferences();
        String healthUserId = prefs.getHealthUserId();
        if (healthUserId == null || healthUserId.trim().isEmpty()) {
            healthUserId = "me";
        }

        UserAuthorization auth = new UserAuthorization();
        auth.setHealthUserID(healthUserId);

        if (cred != null) {
            auth.setAccessToken(cred.getAccessToken() != null ? cred.getAccessToken() : "");
            auth.setRefreshToken(cred.getRefreshToken() != null ? cred.getRefreshToken() : "");
            Long expiresInSec = cred.getExpiresInSeconds();
            if (expiresInSec != null && expiresInSec > 0) {
                auth.setExpiresAtEpochMs(System.currentTimeMillis() + (expiresInSec * 1000));
            } else if (cred.getExpirationTimeMilliseconds() != null) {
                auth.setExpiresAtEpochMs(cred.getExpirationTimeMilliseconds());
            }
        }
        return auth;
    }

    /**
     * Creates an HttpRequestFactory pre-configured with the current Credential.
     */
    public HttpRequestFactory createHttpRequestFactory() {
        Credential cred = getCredential();
        if (cred != null) {
            return new NetHttpTransport().createRequestFactory(cred);
        }
        return new NetHttpTransport().createRequestFactory();
    }

    /**
     * Clears all stored credentials (sign out).
     */
    public synchronized void resetCredentials() throws IOException {
        Credential cred = getCredential();
        if (cred != null) {
            String tokenToRevoke = cred.getRefreshToken();
            if (tokenToRevoke == null || tokenToRevoke.trim().isEmpty()) {
                tokenToRevoke = cred.getAccessToken();
            }
            if (tokenToRevoke != null && !tokenToRevoke.trim().isEmpty()) {
                revokeToken(tokenToRevoke);
            }
        }
        flow.getCredentialDataStore().clear();
        this.credential = null;
        logger.info("Cleared stored Credentials.");
    }

    private void revokeToken(String token) {
        try {
            logger.info("Revoking OAuth token on Google servers...");
            GenericUrl url = new GenericUrl("https://oauth2.googleapis.com/revoke");
            String bodyString = "token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
            HttpContent content = ByteArrayContent.fromString("application/x-www-form-urlencoded", bodyString);
            HttpRequestFactory requestFactory = new NetHttpTransport().createRequestFactory();
            HttpRequest request = requestFactory.buildPostRequest(url, content);
            HttpResponse response = request.execute();
            if (response.isSuccessStatusCode()) {
                logger.info("OAuth token revoked successfully.");
            } else {
                logger.warn("OAuth token revocation status: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            logger.warn("Failed to revoke OAuth token: {}", e.getMessage());
        }
    }

    public File getCredentialsDir() {
        return credentialsDir;
    }
}
