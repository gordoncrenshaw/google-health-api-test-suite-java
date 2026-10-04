package com.google.health.testsuite.config;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Manages reading and atomic saving of preferences.yaml and userAuthorization.yaml.
 */
public class ConfigManager {

    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);

    private static final String DEFAULT_CONFIG_DIR = "config";
    private static final String PREFERENCES_FILE_NAME = "preferences.yaml";
    private static final String USER_AUTH_FILE_NAME = "userAuthorization.yaml";

    private final File preferencesFile;
    private final File userAuthFile;
    private final ObjectMapper yamlMapper;

    private Preferences preferences;
    private UserAuthorization userAuthorization;

    public ConfigManager() {
        this(new File(DEFAULT_CONFIG_DIR, PREFERENCES_FILE_NAME),
             new File(DEFAULT_CONFIG_DIR, USER_AUTH_FILE_NAME));
    }

    public ConfigManager(File preferencesFile, File userAuthFile) {
        this.preferencesFile = preferencesFile;
        this.userAuthFile = userAuthFile;

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

    public synchronized Preferences loadPreferences() {
        if (!preferencesFile.exists()) {
            logger.info("Preferences file {} does not exist, creating default.", preferencesFile.getAbsolutePath());
            preferences = new Preferences();
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
