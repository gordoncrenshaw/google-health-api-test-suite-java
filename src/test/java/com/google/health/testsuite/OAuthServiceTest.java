package com.google.health.testsuite;

import com.google.api.client.auth.oauth2.Credential;
import com.google.health.testsuite.auth.OAuthService;
import com.google.health.testsuite.config.ConfigManager;
import com.google.health.testsuite.config.Preferences;
import com.google.health.testsuite.config.UserAuthorization;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

public class OAuthServiceTest {

    private File tempPrefFile;
    private File tempTokensDir;
    private ConfigManager configManager;
    private OAuthService oAuthService;

    @BeforeEach
    void setUp() throws Exception {
        tempPrefFile = File.createTempFile("pref_test", ".yaml");
        tempTokensDir = Files.createTempDirectory("tokens_test_").toFile();

        configManager = new ConfigManager(tempPrefFile);
        oAuthService = new OAuthService(configManager, tempTokensDir);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (tempPrefFile.exists()) tempPrefFile.delete();
        if (tempTokensDir != null && tempTokensDir.exists()) {
            Files.walk(tempTokensDir.toPath())
                    .sorted(Comparator.reverseOrder())
                    .map(java.nio.file.Path::toFile)
                    .forEach(File::delete);
        }
    }

    @Test
    void testCredentialStorageAndPersistence() {
        assertFalse(oAuthService.hasValidCredential());

        oAuthService.storeCredential("token_abc_123", "refresh_xyz_789", 3600, "test.scope");
        assertTrue(oAuthService.hasValidCredential());

        Credential credential = oAuthService.getCredential();
        assertNotNull(credential);
        assertEquals("token_abc_123", credential.getAccessToken());
        assertEquals("refresh_xyz_789", credential.getRefreshToken());
        assertFalse(oAuthService.isExpired(credential));

        // Create a new instance pointing to same tokens directory to verify disk persistence
        OAuthService reloadedService = new OAuthService(configManager, tempTokensDir);
        assertTrue(reloadedService.hasValidCredential());
        assertEquals("token_abc_123", reloadedService.getCredential().getAccessToken());
        assertEquals("refresh_xyz_789", reloadedService.getCredential().getRefreshToken());
    }

    @Test
    void testUserAuthorizationSnapshot() {
        Preferences prefs = configManager.getPreferences();
        prefs.setHealthUserId("1234567890");
        configManager.savePreferences(prefs);

        oAuthService.storeCredential("access_val", "refresh_val", 1800, "scope_val");
        UserAuthorization userAuth = oAuthService.getUserAuthorization();

        assertEquals("1234567890", userAuth.getHealthUserID());
        assertEquals("access_val", userAuth.getAccessToken());
        assertEquals("refresh_val", userAuth.getRefreshToken());
        assertTrue(userAuth.hasAccessToken());
        assertTrue(userAuth.hasRefreshToken());
        assertFalse(userAuth.isExpired());
    }

    @Test
    void testMockModeOperations() {
        Preferences prefs = configManager.getPreferences();
        prefs.setMockMode(true);

        assertTrue(oAuthService.exchangeCodeForTokens("any_code"));
        assertTrue(oAuthService.hasValidCredential());
        assertTrue(oAuthService.getCredential().getAccessToken().startsWith("mock_access_token_"));

        assertTrue(oAuthService.refreshAccessToken());
        assertTrue(oAuthService.getCredential().getAccessToken().startsWith("mock_access_token_"));
    }
}
