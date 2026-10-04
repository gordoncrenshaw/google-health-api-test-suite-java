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
    private ConfigManager configManager;

    @BeforeEach
    void setUp() throws Exception {
        tempPrefFile = File.createTempFile("test_preferences", ".yaml");
        tempAuthFile = File.createTempFile("test_user_auth", ".yaml");
        configManager = new ConfigManager(tempPrefFile, tempAuthFile);
    }

    @AfterEach
    void tearDown() {
        if (tempPrefFile.exists()) tempPrefFile.delete();
        if (tempAuthFile.exists()) tempAuthFile.delete();
    }

    @Test
    void testSaveAndLoadPreferences() {
        Preferences prefs = new Preferences();
        prefs.setClientId("test-client-id-123.apps.googleusercontent.com");
        prefs.setClientSecret("test-secret-456");
        prefs.setDefaultUserId("user-789");
        prefs.setMockMode(true);

        configManager.savePreferences(prefs);

        // Reload
        ConfigManager reloadMgr = new ConfigManager(tempPrefFile, tempAuthFile);
        Preferences loaded = reloadMgr.getPreferences();

        assertEquals("test-client-id-123.apps.googleusercontent.com", loaded.getClientId());
        assertEquals("test-secret-456", loaded.getClientSecret());
        assertEquals("user-789", loaded.getDefaultUserId());
        assertTrue(loaded.isMockMode());
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
