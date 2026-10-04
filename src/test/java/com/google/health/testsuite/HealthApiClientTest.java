package com.google.health.testsuite;

import com.google.health.testsuite.auth.OAuthService;
import com.google.health.testsuite.client.HealthApiClient;
import com.google.health.testsuite.config.ConfigManager;
import com.google.health.testsuite.config.DataTypeRegistry;
import com.google.health.testsuite.config.Preferences;
import com.google.health.testsuite.model.ApiResponse;
import com.google.health.testsuite.model.DataTypeDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class HealthApiClientTest {

    private HealthApiClient client;
    private DataTypeRegistry registry;
    private ConfigManager configManager;

    @BeforeEach
    void setUp() throws Exception {
        File tempPref = File.createTempFile("pref_test", ".yaml");
        File tempAuth = File.createTempFile("auth_test", ".yaml");
        configManager = new ConfigManager(tempPref, tempAuth);

        Preferences prefs = configManager.getPreferences();
        prefs.setMockMode(true); // Run in mock mode for unit test
        configManager.savePreferences(prefs);

        OAuthService oAuthService = new OAuthService(configManager);
        client = new HealthApiClient(configManager, oAuthService);
        registry = new DataTypeRegistry(new File("config/datatypes.yaml"));
    }

    @Test
    void testListStepsDataPoints() {
        DataTypeDefinition steps = registry.getDataType("steps").orElseThrow();
        ApiResponse resp = client.listDataPoints(steps, Map.of("pageSize", "10"));

        assertNotNull(resp);
        assertEquals(200, resp.getStatusCode());
        assertTrue(resp.isSuccess());
        assertTrue(resp.getBody().contains("dataPoints"), "Response must contain dataPoints");
        assertTrue(resp.getCurlCommand().contains("curl -X GET"));
    }

    @Test
    void testCreateStepsDataPoint() {
        DataTypeDefinition steps = registry.getDataType("steps").orElseThrow();
        String payload = "{\"steps\": {\"count\": 5000}}";
        ApiResponse resp = client.createDataPoint(steps, payload);

        assertNotNull(resp);
        assertEquals(201, resp.getStatusCode());
        assertTrue(resp.isSuccess());
    }

    @Test
    void testGetProfile() {
        ApiResponse resp = client.getProfile("me");
        assertNotNull(resp);
        assertEquals(200, resp.getStatusCode());
        assertTrue(resp.getBody().contains("healthUserId"));
    }

    @Test
    void testListPairedDevices() {
        ApiResponse resp = client.listPairedDevices("me");
        assertNotNull(resp);
        assertEquals(200, resp.getStatusCode());
        assertTrue(resp.getBody().contains("pairedDevices"));
    }
}
