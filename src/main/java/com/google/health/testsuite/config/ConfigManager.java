package com.google.health.testsuite.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.google.api.client.auth.oauth2.Credential;
import com.google.health.testsuite.auth.OAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages reading and saving of preferences.yaml, resolving OAuth client credentials
 * from client_secret.json, and coordinating with OAuthService for Credential management.
 */
public final class ConfigManager {

    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);

    private static final String DEFAULT_CONFIG_DIR = "config";
    private static final String PREFERENCES_FILE_NAME = "preferences.yaml";
    private static final String CLIENT_SECRET_FILE_NAME = "client_secret.json";

    public static final List<String> DEFAULT_AVAILABLE_SCOPES = List.of(
            "https://www.googleapis.com/auth/googlehealth.activity_and_fitness.readonly",
            "https://www.googleapis.com/auth/googlehealth.activity_and_fitness.writeonly",
            "https://www.googleapis.com/auth/googlehealth.ecg.readonly",
            "https://www.googleapis.com/auth/googlehealth.health_metrics_and_measurements.readonly",
            "https://www.googleapis.com/auth/googlehealth.health_metrics_and_measurements.writeonly",
            "https://www.googleapis.com/auth/googlehealth.irn.readonly",
            "https://www.googleapis.com/auth/googlehealth.location.readonly",
            "https://www.googleapis.com/auth/googlehealth.logged_symptoms.readonly",
            "https://www.googleapis.com/auth/googlehealth.logged_symptoms.writeonly",
            "https://www.googleapis.com/auth/googlehealth.mindfulness.readonly",
            "https://www.googleapis.com/auth/googlehealth.mindfulness.writeonly",
            "https://www.googleapis.com/auth/googlehealth.profile.readonly",
            "https://www.googleapis.com/auth/googlehealth.profile.writeonly",
            "https://www.googleapis.com/auth/googlehealth.reproductive_health.readonly",
            "https://www.googleapis.com/auth/googlehealth.reproductive_health.writeonly",
            "https://www.googleapis.com/auth/googlehealth.settings.readonly",
            "https://www.googleapis.com/auth/googlehealth.settings.writeonly",
            "https://www.googleapis.com/auth/googlehealth.sleep.readonly",
            "https://www.googleapis.com/auth/googlehealth.sleep.writeonly"
    );

    private final File preferencesFile;
    private final File clientSecretFile;
    private final ObjectMapper yamlMapper;
    private final ObjectMapper jsonMapper = new ObjectMapper();

    private Preferences preferences;
    private OAuthService oAuthService;
    private UserAuthorization fallbackUserAuthorization = new UserAuthorization();

    public ConfigManager() {
        this(new File(DEFAULT_CONFIG_DIR, PREFERENCES_FILE_NAME),
             new File(DEFAULT_CONFIG_DIR, CLIENT_SECRET_FILE_NAME));
    }

    public ConfigManager(File preferencesFile) {
        this(preferencesFile, new File(DEFAULT_CONFIG_DIR, CLIENT_SECRET_FILE_NAME));
    }

    public ConfigManager(File preferencesFile, File secondFile) {
        this.preferencesFile = preferencesFile;
        if (secondFile != null && secondFile.getName().endsWith(".json")) {
            this.clientSecretFile = secondFile;
        } else {
            this.clientSecretFile = new File(DEFAULT_CONFIG_DIR, CLIENT_SECRET_FILE_NAME);
        }

        YAMLFactory yamlFactory = new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                .enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR);
        this.yamlMapper = new ObjectMapper(yamlFactory);

        ensureDirectories();
        loadPreferences();
    }

    /**
     * Backward-compatible constructor for callers passing an optional third file argument.
     */
    public ConfigManager(File preferencesFile, File secondFile, File ignoredThirdFile) {
        this(preferencesFile, secondFile);
    }


    private void ensureDirectories() {
        File parentPref = preferencesFile.getParentFile();
        if (parentPref != null && !parentPref.exists()) {
            parentPref.mkdirs();
        }
    }

    public void setOAuthService(OAuthService oAuthService) {
        this.oAuthService = oAuthService;
    }

    public OAuthService getOAuthService() {
        return oAuthService;
    }

    public File resolveClientSecretFile() {
        if (clientSecretFile != null && clientSecretFile.exists()) {
            return clientSecretFile;
        }

        File parentDir = preferencesFile != null && preferencesFile.getParentFile() != null ?
                preferencesFile.getParentFile() : new File(DEFAULT_CONFIG_DIR);

        // 1. Direct match: client_secret.json
        File direct = new File(parentDir, CLIENT_SECRET_FILE_NAME);
        if (direct.exists()) {
            return direct;
        }

        // 2. Any client_secret*.json
        File[] matchesAny = parentDir.listFiles((dir, name) -> name.startsWith("client_secret") && name.endsWith(".json"));
        if (matchesAny != null && matchesAny.length > 0) {
            return matchesAny[0];
        }

        return direct;
    }

    public synchronized void loadClientSecret(Preferences prefs) {
        if (prefs == null) return;
        File secretFile = resolveClientSecretFile();
        if (secretFile != null && secretFile.exists() && secretFile.length() > 0) {
            try {
                JsonNode root = jsonMapper.readTree(secretFile);
                JsonNode clientNode = root.has("web") ? root.path("web") :
                                     (root.has("installed") ? root.path("installed") : root);

                String cid = clientNode.path("client_id").asText("").trim();
                if (cid.isEmpty()) {
                    cid = clientNode.path("clientId").asText("").trim();
                }

                String csec = clientNode.path("client_secret").asText("").trim();
                if (csec.isEmpty()) {
                    csec = clientNode.path("clientSecret").asText("").trim();
                }

                if (!cid.isEmpty()) {
                    prefs.setClientId(cid);
                }
                if (!csec.isEmpty()) {
                    prefs.setClientSecret(csec);
                }

                String authUri = clientNode.path("auth_uri").asText("").trim();
                if (!authUri.isEmpty() && (prefs.getAuthUri() == null || prefs.getAuthUri().isEmpty())) {
                    prefs.setAuthUri(authUri);
                }

                String tokenUri = clientNode.path("token_uri").asText("").trim();
                if (!tokenUri.isEmpty() && (prefs.getTokenUri() == null || prefs.getTokenUri().isEmpty())) {
                    prefs.setTokenUri(tokenUri);
                }

                logger.info("Loaded OAuth client credentials from {} (clientId: {})",
                        secretFile.getName(), cid.isEmpty() ? "none" : cid);
            } catch (Exception e) {
                logger.error("Failed to parse client secret JSON from {}: {}", secretFile.getAbsolutePath(), e.getMessage());
            }
        } else {
            logger.debug("No client secret JSON file found at {}", (secretFile != null ? secretFile.getAbsolutePath() : "null"));
        }
    }

    public synchronized void saveClientSecret(String clientId, String clientSecret) {
        File file = resolveClientSecretFile();
        try {
            ObjectNode root = jsonMapper.createObjectNode();
            ObjectNode web = root.putObject("web");
            web.put("client_id", clientId);
            web.put("client_secret", clientSecret);
            web.put("auth_uri", "https://accounts.google.com/o/oauth2/auth");
            web.put("token_uri", "https://oauth2.googleapis.com/token");
            ArrayNode uris = web.putArray("redirect_uris");
            uris.add("http://localhost:8888/callback");

            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();

            File tempFile = new File(file.getAbsolutePath() + ".tmp");
            jsonMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile, root);
            Files.move(tempFile.toPath(), file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            logger.info("Saved OAuth client credentials to {}", file.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to save client secret JSON to {}: {}", file.getAbsolutePath(), e.getMessage(), e);
        }
    }

    public final synchronized Preferences loadPreferences() {
        if (!preferencesFile.exists() || preferencesFile.length() == 0) {
            logger.info("Preferences file {} does not exist or is empty, creating default.", preferencesFile.getAbsolutePath());
            preferences = new Preferences();
            preferences.setScopes(new ArrayList<>(DEFAULT_AVAILABLE_SCOPES));
            loadClientSecret(preferences);
            savePreferences(preferences);
            return preferences;
        }

        try {
            preferences = yamlMapper.readValue(preferencesFile, Preferences.class);
            if (preferences.getScopes() == null || preferences.getScopes().isEmpty()) {
                preferences.setScopes(new ArrayList<>(DEFAULT_AVAILABLE_SCOPES));
            }
            logger.debug("Successfully loaded preferences from {}", preferencesFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to read preferences from {}: {}", preferencesFile.getAbsolutePath(), e.getMessage());
            preferences = new Preferences();
            preferences.setScopes(new ArrayList<>(DEFAULT_AVAILABLE_SCOPES));
        }

        loadClientSecret(preferences);
        return preferences;
    }

    public synchronized void savePreferences(Preferences prefs) {
        this.preferences = prefs;
        try {
            File tempFile = new File(preferencesFile.getAbsolutePath() + ".tmp");
            yamlMapper.writeValue(tempFile, prefs);
            Files.move(tempFile.toPath(), preferencesFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            logger.info("Saved preferences to {}", preferencesFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to save preferences to {}: {}", preferencesFile.getAbsolutePath(), e.getMessage(), e);
        }
    }

    /**
     * Returns the active Credential managed by com.google.api.client.auth.oauth2.Credential.
     */
    public Credential getCredential() {
        return oAuthService != null ? oAuthService.getCredential() : null;
    }

    /**
     * Returns an in-memory view of authorization status.
     * Backed by com.google.api.client.auth.oauth2.Credential when OAuthService is available.
     */
    public UserAuthorization getUserAuthorization() {
        if (oAuthService != null) {
            return oAuthService.getUserAuthorization();
        }
        if (fallbackUserAuthorization != null) {
            String hId = (preferences != null && preferences.getHealthUserId() != null && !preferences.getHealthUserId().isEmpty())
                    ? preferences.getHealthUserId() : "me";
            fallbackUserAuthorization.setHealthUserID(hId);
            return fallbackUserAuthorization;
        }
        UserAuthorization auth = new UserAuthorization();
        String hId = (preferences != null && preferences.getHealthUserId() != null && !preferences.getHealthUserId().isEmpty())
                ? preferences.getHealthUserId() : "me";
        auth.setHealthUserID(hId);
        return auth;
    }

    /**
     * In-memory or Credential store update for tokens.
     */
    public synchronized void updateTokens(String accessToken, String refreshToken, long expiresInSeconds, String scope) {
        if (oAuthService != null) {
            oAuthService.storeCredential(accessToken, refreshToken, expiresInSeconds, scope);
        } else {
            fallbackUserAuthorization.setAccessToken(accessToken);
            if (refreshToken != null && !refreshToken.trim().isEmpty()) {
                fallbackUserAuthorization.setRefreshToken(refreshToken);
            }
            if (expiresInSeconds > 0) {
                fallbackUserAuthorization.setExpiresAtEpochMs(System.currentTimeMillis() + (expiresInSeconds * 1000));
            }
            if (scope != null && !scope.trim().isEmpty()) {
                fallbackUserAuthorization.setScope(scope);
            }
        }
    }

    /**
     * Backward-compatible in-memory store method for UserAuthorization.
     * Tokens are persisted into Credential store if OAuthService is active.
     */
    public synchronized void saveUserAuthorization(UserAuthorization auth) {
        if (auth == null) return;
        this.fallbackUserAuthorization = auth;
        if (auth.getHealthUserID() != null && !auth.getHealthUserID().isEmpty() && !"me".equalsIgnoreCase(auth.getHealthUserID())) {
            if (preferences != null) {
                preferences.setHealthUserId(auth.getHealthUserID());
                savePreferences(preferences);
            }
        }
        if (oAuthService != null && auth.hasAccessToken()) {
            oAuthService.storeCredential(auth.getAccessToken(), auth.getRefreshToken(),
                    auth.getRemainingSeconds(), auth.getScope());
        }
    }

    public Preferences getPreferences() {
        return preferences;
    }

    public File getPreferencesFile() {
        return preferencesFile;
    }

    public File getScopesFile() {
        return preferencesFile;
    }

    public synchronized List<String> getAvailableScopes() {
        loadPreferences();
        if (preferences != null && preferences.getScopes() != null && !preferences.getScopes().isEmpty()) {
            return new ArrayList<>(preferences.getScopes());
        }
        if (preferences != null) {
            preferences.setScopes(new ArrayList<>(DEFAULT_AVAILABLE_SCOPES));
            savePreferences(preferences);
        }
        return new ArrayList<>(DEFAULT_AVAILABLE_SCOPES);
    }

    public synchronized void saveAvailableScopes(List<String> scopes) {
        if (preferences == null) {
            preferences = new Preferences();
        }
        preferences.setScopes(scopes != null ? scopes : new ArrayList<>());
        savePreferences(preferences);
        logger.info("Saved {} available scopes to preferences file {}",
                (scopes != null ? scopes.size() : 0), preferencesFile.getAbsolutePath());
    }
}
