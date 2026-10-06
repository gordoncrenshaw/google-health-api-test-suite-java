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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigManagerTest {

    private File tempPrefFile;
    private File tempSecretJsonFile;
    private File tempTokensDir;
    private ConfigManager configManager;
    private OAuthService oAuthService;

    @BeforeEach
    void setUp() throws Exception {
        tempPrefFile = File.createTempFile("test_preferences", ".yaml");
        tempSecretJsonFile = File.createTempFile("client_secret", ".json");
        tempTokensDir = Files.createTempDirectory("test_tokens_").toFile();

        Files.writeString(tempSecretJsonFile.toPath(),
                "{\"web\":{\"client_id\":\"json-client-123.apps.googleusercontent.com\",\"client_secret\":\"json-secret-456\"}}");
        configManager = new ConfigManager(tempPrefFile, tempSecretJsonFile);
        oAuthService = new OAuthService(configManager, tempTokensDir);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (tempPrefFile.exists()) tempPrefFile.delete();
        if (tempSecretJsonFile.exists()) tempSecretJsonFile.delete();
        if (tempTokensDir != null && tempTokensDir.exists()) {
            Files.walk(tempTokensDir.toPath())
                    .sorted(Comparator.reverseOrder())
                    .map(java.nio.file.Path::toFile)
                    .forEach(File::delete);
        }
    }

    @Test
    void testSaveAndLoadPreferences() throws Exception {
        Preferences prefs = new Preferences();
        prefs.setDefaultUserId("user-789");
        prefs.setMockMode(true);
        prefs.setEnableAllEndpoints(true);

        configManager.savePreferences(prefs);

        // Verify preferences.yaml file does NOT contain clientId or clientSecret
        String yamlContent = Files.readString(tempPrefFile.toPath());
        assertFalse(yamlContent.contains("clientId"));
        assertFalse(yamlContent.contains("clientSecret"));

        // Reload
        ConfigManager reloadMgr = new ConfigManager(tempPrefFile, tempSecretJsonFile);
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
    void testCredentialStorageWithOAuthService() {
        oAuthService.storeCredential("ya29.sample_access_token", "1//sample_refresh_token", 3600, "health.scope");

        Credential credential = oAuthService.getCredential();
        assertNotNull(credential, "Credential must be loaded from data store");
        assertEquals("ya29.sample_access_token", credential.getAccessToken());
        assertEquals("1//sample_refresh_token", credential.getRefreshToken());
        assertNotNull(credential.getExpiresInSeconds());
        assertTrue(credential.getExpiresInSeconds() > 3000);

        // Check that UserAuthorization view accurately reflects the Credential
        UserAuthorization auth = configManager.getUserAuthorization();
        assertEquals("ya29.sample_access_token", auth.getAccessToken());
        assertEquals("1//sample_refresh_token", auth.getRefreshToken());
        assertFalse(auth.isExpired());
    }

    @Test
    void testMockModeExchangeAndRefresh() {
        Preferences prefs = configManager.getPreferences();
        prefs.setMockMode(true);

        boolean exchanged = oAuthService.exchangeCodeForTokens("mock_auth_code");
        assertTrue(exchanged);

        Credential credential = oAuthService.getCredential();
        assertNotNull(credential);
        assertNotNull(credential.getAccessToken());
        assertTrue(credential.getAccessToken().startsWith("mock_access_token_"));

        boolean refreshed = oAuthService.refreshAccessToken();
        assertTrue(refreshed);

        Credential refreshedCred = oAuthService.getCredential();
        assertNotNull(refreshedCred.getAccessToken());
    }

    @Test
    void testAvailableScopesLoadingAndDynamicUpdates() throws Exception {
        // Initial scopes loaded should contain default scopes
        List<String> scopes = configManager.getAvailableScopes();
        assertNotNull(scopes);
        assertFalse(scopes.isEmpty());
        assertTrue(scopes.contains("https://www.googleapis.com/auth/googlehealth.activity_and_fitness.readonly"));

        // Save a customized list of scopes to preferences.yaml
        List<String> customScopes = List.of(
                "https://www.googleapis.com/auth/googlehealth.activity_and_fitness.readonly",
                "https://www.googleapis.com/auth/googlehealth.custom_scope.readwrite"
        );
        configManager.saveAvailableScopes(customScopes);

        // Reload scopes from file and verify the new custom scope is present
        List<String> reloaded = configManager.getAvailableScopes();
        assertEquals(2, reloaded.size());
        assertTrue(reloaded.contains("https://www.googleapis.com/auth/googlehealth.custom_scope.readwrite"));

        // Append a new scope directly to the preferences file to simulate manual file editing
        File prefFile = configManager.getPreferencesFile();
        Files.writeString(prefFile.toPath(),
                Files.readString(prefFile.toPath()) + "  - \"https://www.googleapis.com/auth/googlehealth.new_scope.read\"\n");

        List<String> dynamicallyLoaded = configManager.getAvailableScopes();
        assertEquals(3, dynamicallyLoaded.size());
        assertTrue(dynamicallyLoaded.contains("https://www.googleapis.com/auth/googlehealth.new_scope.read"));
    }
}
