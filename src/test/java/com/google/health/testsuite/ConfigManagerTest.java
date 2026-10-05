package com.google.health.testsuite;

import com.google.health.testsuite.config.ConfigManager;
import com.google.health.testsuite.config.Preferences;
import com.google.health.testsuite.config.UserAuthorization;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigManagerTest {

    private File tempPrefFile;
    private File tempAuthFile;
    private File tempSecretJsonFile;
    private ConfigManager configManager;

    @BeforeEach
    void setUp() throws Exception {
        tempPrefFile = File.createTempFile("test_preferences", ".yaml");
        tempAuthFile = File.createTempFile("test_user_auth", ".yaml");
        tempSecretJsonFile = File.createTempFile("client_secret", ".json");
        java.nio.file.Files.writeString(tempSecretJsonFile.toPath(),
                "{\"web\":{\"client_id\":\"json-client-123.apps.googleusercontent.com\",\"client_secret\":\"json-secret-456\"}}");
        configManager = new ConfigManager(tempPrefFile, tempAuthFile, tempSecretJsonFile);
    }

    @AfterEach
    void tearDown() {
        if (tempPrefFile.exists()) tempPrefFile.delete();
        if (tempAuthFile.exists()) tempAuthFile.delete();
        if (tempSecretJsonFile.exists()) tempSecretJsonFile.delete();
    }

    @Test
    void testSaveAndLoadPreferences() throws Exception {
        Preferences prefs = new Preferences();
        prefs.setDefaultUserId("user-789");
        prefs.setMockMode(true);
        prefs.setEnableAllEndpoints(true);

        configManager.savePreferences(prefs);

        // Verify preferences.yaml file does NOT contain clientId or clientSecret
        String yamlContent = java.nio.file.Files.readString(tempPrefFile.toPath());
        assertFalse(yamlContent.contains("clientId"));
        assertFalse(yamlContent.contains("clientSecret"));

        // Reload
        ConfigManager reloadMgr = new ConfigManager(tempPrefFile, tempAuthFile, tempSecretJsonFile);
        Preferences loaded = reloadMgr.getPreferences();

        assertEquals("json-client-123.apps.googleusercontent.com", loaded.getClientId());
        assertEquals("json-secret-456", loaded.getClientSecret());
        assertEquals("user-789", loaded.getDefaultUserId());
        assertTrue(loaded.isMockMode());
        assertTrue(loaded.isEnableAllEndpoints());
    }

    @Test
    void testLoadClientSecretFromJson() {
        Preferences prefs = configManager.getPreferences();
        assertEquals("json-client-123.apps.googleusercontent.com", prefs.getClientId());
        assertEquals("json-secret-456", prefs.getClientSecret());
    }

    @Test
    void testSaveAndLoadUserAuthorization() {
        UserAuthorization auth = new UserAuthorization();
        auth.setHealthUserID("health-user-999");
        auth.setAccessToken("ya29.sample_access_token");
        auth.setRefreshToken("1//sample_refresh_token");
        auth.setExpiresAtEpochMs(System.currentTimeMillis() + 3600_000);

        configManager.saveUserAuthorization(auth);

        // Reload
        ConfigManager reloadMgr = new ConfigManager(tempPrefFile, tempAuthFile);
        UserAuthorization loaded = reloadMgr.getUserAuthorization();

        assertEquals("health-user-999", loaded.getHealthUserID());
        assertEquals("ya29.sample_access_token", loaded.getAccessToken());
        assertEquals("1//sample_refresh_token", loaded.getRefreshToken());
        assertFalse(loaded.isExpired(), "Token expiring in 1 hour should not be expired");
        assertTrue(loaded.getRemainingSeconds() > 3000);
    }

    @Test
    void testUpdateTokensAutoPersist() {
        configManager.updateTokens("new-access-token", "new-refresh-token", 1800, "health.scope");

        ConfigManager reloadMgr = new ConfigManager(tempPrefFile, tempAuthFile);
        UserAuthorization loaded = reloadMgr.getUserAuthorization();

        assertEquals("new-access-token", loaded.getAccessToken());
        assertEquals("new-refresh-token", loaded.getRefreshToken());
        assertEquals("health.scope", loaded.getScope());
        assertFalse(loaded.isExpired());
    }
}
