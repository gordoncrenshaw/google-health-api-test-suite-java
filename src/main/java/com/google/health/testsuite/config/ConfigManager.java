package com.google.health.testsuite.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

/**
 * Manages reading and atomic saving of preferences.yaml, userAuthorization.yaml,
 * and loading OAuth credentials from client_secret.json.
 */
public class ConfigManager {

    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);

    private static final String DEFAULT_CONFIG_DIR = "config";
    private static final String PREFERENCES_FILE_NAME = "preferences.yaml";
    private static final String USER_AUTH_FILE_NAME = "userAuthorization.yaml";
    private static final String CLIENT_SECRET_FILE_NAME = "client_secret.json";

    private final File preferencesFile;
    private final File userAuthFile;
    private final File clientSecretFile;
    private final ObjectMapper yamlMapper;
    private final ObjectMapper jsonMapper = new ObjectMapper();

    private Preferences preferences;
    private UserAuthorization userAuthorization;

    public ConfigManager() {
        this(new File(DEFAULT_CONFIG_DIR, PREFERENCES_FILE_NAME),
             new File(DEFAULT_CONFIG_DIR, USER_AUTH_FILE_NAME),
             new File(DEFAULT_CONFIG_DIR, CLIENT_SECRET_FILE_NAME));
    }

    public ConfigManager(File preferencesFile, File userAuthFile) {
        this(preferencesFile, userAuthFile, null);
    }

    public ConfigManager(File preferencesFile, File userAuthFile, File clientSecretFile) {
        this.preferencesFile = preferencesFile;
        this.userAuthFile = userAuthFile;
        this.clientSecretFile = clientSecretFile;

        YAMLFactory yamlFactory = new YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);
        this.yamlMapper = new ObjectMapper(yamlFactory);

        ensureDirectories();
        loadPreferences();
        loadUserAuthorization();
    }

    private void ensureDirectories() {
        File parentPref = preferencesFile.getParentFile();
        if (parentPref != null && !parentPref.exists()) {
            parentPref.mkdirs();
        }
        File parentAuth = userAuthFile.getParentFile();
        if (parentAuth != null && !parentAuth.exists()) {
            parentAuth.mkdirs();
        }
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

        // 2. Legacy match: client_secret_2_.json
        File legacy = new File(parentDir, "client_secret_2_.json");
        if (legacy.exists()) {
            return legacy;
        }

        // 3. Any client_secret*.json
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

    public synchronized Preferences loadPreferences() {
        if (!preferencesFile.exists()) {
            logger.info("Preferences file {} does not exist, creating default.", preferencesFile.getAbsolutePath());
            preferences = new Preferences();
            loadClientSecret(preferences);
            savePreferences(preferences);
            return preferences;
        }

        try {
            preferences = yamlMapper.readValue(preferencesFile, Preferences.class);
            logger.debug("Successfully loaded preferences from {}", preferencesFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to read preferences from {}: {}", preferencesFile.getAbsolutePath(), e.getMessage());
            preferences = new Preferences();
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

    public synchronized UserAuthorization loadUserAuthorization() {
        if (!userAuthFile.exists()) {
            logger.info("User authorization file {} does not exist, creating default.", userAuthFile.getAbsolutePath());
            userAuthorization = new UserAuthorization();
            saveUserAuthorization(userAuthorization);
            return userAuthorization;
        }

        try {
            userAuthorization = yamlMapper.readValue(userAuthFile, UserAuthorization.class);
            logger.debug("Successfully loaded user authorization from {}", userAuthFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to read user authorization from {}: {}", userAuthFile.getAbsolutePath(), e.getMessage());
            userAuthorization = new UserAuthorization();
        }
        return userAuthorization;
    }

    public synchronized void saveUserAuthorization(UserAuthorization auth) {
        this.userAuthorization = auth;
        if (auth.getUpdatedAt() == null || auth.getUpdatedAt().isEmpty()) {
            auth.setUpdatedAt(Instant.now().toString());
        }

        try {
            File tempFile = new File(userAuthFile.getAbsolutePath() + ".tmp");
            yamlMapper.writeValue(tempFile, auth);
            Files.move(tempFile.toPath(), userAuthFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            logger.info("Saved user authorization to {} (healthUserID={}, remainingSeconds={})",
                    userAuthFile.getAbsolutePath(), auth.getHealthUserID(), auth.getRemainingSeconds());
        } catch (IOException e) {
            logger.error("Failed to save user authorization to {}: {}", userAuthFile.getAbsolutePath(), e.getMessage(), e);
        }
    }

    public synchronized void updateTokens(String accessToken, String refreshToken, long expiresInSeconds, String scope) {
        if (userAuthorization == null) {
            userAuthorization = new UserAuthorization();
        }
        userAuthorization.setAccessToken(accessToken);
        if (refreshToken != null && !refreshToken.trim().isEmpty()) {
            userAuthorization.setRefreshToken(refreshToken);
        }
        if (expiresInSeconds > 0) {
            userAuthorization.setExpiresAtEpochMs(System.currentTimeMillis() + (expiresInSeconds * 1000));
        }
        if (scope != null && !scope.trim().isEmpty()) {
            userAuthorization.setScope(scope);
        }
        userAuthorization.setUpdatedAt(Instant.now().toString());
        saveUserAuthorization(userAuthorization);
    }

    public Preferences getPreferences() {
        return preferences;
    }

    public UserAuthorization getUserAuthorization() {
        return userAuthorization;
    }

    public File getPreferencesFile() {
        return preferencesFile;
    }

    public File getUserAuthFile() {
        return userAuthFile;
    }
}
