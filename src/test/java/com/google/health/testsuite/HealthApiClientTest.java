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

    @Test
    void testGetDevices() {
        ApiResponse resp = client.getDevices("me");
        assertNotNull(resp);
        assertEquals(200, resp.getStatusCode());
        assertTrue(resp.getBody().contains("pairedDevices"));
        // Response should be formatted as pretty JSON with indentation
        assertTrue(resp.getBody().contains("\n"), "Response must be in pretty JSON format");
    }

    @Test
    void testGetIdentityAndPersistHealthUserIdWhenMissing() {
        Preferences prefsBefore = configManager.getPreferences();
        assertEquals("", prefsBefore.getHealthUserId(), "healthUserId should initially be empty in preferences");

        ApiResponse resp = client.getIdentity(null);
        assertNotNull(resp);
        assertEquals(200, resp.getStatusCode());
        assertTrue(resp.isSuccess());
        assertTrue(resp.getBody().contains("8677373576871223311"));
        assertTrue(resp.getBody().contains("\n"), "Response must be in pretty JSON format");

        Preferences prefsAfter = configManager.getPreferences();
        assertEquals("8677373576871223311", prefsAfter.getHealthUserId(),
                "healthUserId must be automatically persisted to preferences when missing");
    }

    @Test
    void testEndpointsQueryVersionFromDataType() {
        DataTypeDefinition steps = registry.getDataType("steps").orElseThrow();
        assertEquals("v4", steps.getEndpointVersion());

        // Default steps queries v4
        ApiResponse respV4 = client.listDataPoints(steps, Map.of("pageSize", "10"));
        assertNotNull(respV4);
        assertTrue(respV4.getRequestUrl().contains("/v4/users/"), "Default request URL should query v4 version");
        assertTrue(respV4.getCurlCommand().contains("/v4/users/"));

        // Custom datatype with updated version queries that version
        DataTypeDefinition customDef = new DataTypeDefinition("custom_metric", "Custom Metric", "v5",
                "https://www.googleapis.com/auth/googlehealth.custom",
                java.util.List.of("list", "get", "create", "batchDelete", "rollUp", "dailyRollUp"),
                "custom.sample_time", false, 0.0, 100.0, "units");

        ApiResponse listResp = client.listDataPoints(customDef, null);
        assertTrue(listResp.getRequestUrl().contains("/v5/users/"), "Endpoint must query v5 version from DataTypeDefinition");
        assertTrue(listResp.getCurlCommand().contains("/v5/users/"));

        ApiResponse getResp = client.getDataPoint(customDef, "dp-123");
        assertTrue(getResp.getRequestUrl().contains("/v5/users/"), "getDataPoint must query v5 version");

        ApiResponse createResp = client.createDataPoint(customDef, "{}");
        assertTrue(createResp.getRequestUrl().contains("/v5/users/"), "createDataPoint must query v5 version");

        ApiResponse rollupResp = client.rollUpDataPoints(customDef, "{}");
        assertTrue(rollupResp.getRequestUrl().contains("/v5/users/"), "rollUpDataPoints must query v5 version");

        ApiResponse dailyRollupResp = client.dailyRollUpDataPoints(customDef, "{}");
        assertTrue(dailyRollupResp.getRequestUrl().contains("/v5/users/"), "dailyRollUpDataPoints must query v5 version");

        ApiResponse batchDelResp = client.batchDeleteDataPoints(customDef, java.util.List.of("dp-1"));
        assertTrue(batchDelResp.getRequestUrl().contains("/v5/users/"), "batchDeleteDataPoints must query v5 version");
    }
}
