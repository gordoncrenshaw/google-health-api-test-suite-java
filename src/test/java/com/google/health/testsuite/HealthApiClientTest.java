package com.google.health.testsuite;

import com.google.health.testsuite.auth.OAuthService;
import com.google.health.testsuite.client.HealthApiClient;
import com.google.health.testsuite.config.ConfigManager;
import com.google.health.testsuite.config.DataTypeRegistry;
import com.google.health.testsuite.config.Preferences;
import com.google.health.testsuite.model.ApiResponse;
import com.google.health.testsuite.model.DataTypeDefinition;
import com.google.health.testsuite.model.TestResult;
import com.google.health.testsuite.runner.SuiteExecutionEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class HealthApiClientTest {

    private HealthApiClient client;
    private DataTypeRegistry registry;
    private ConfigManager configManager;

    @BeforeEach
    void setUp() throws Exception {
        File tempPref = File.createTempFile("pref_test", ".yaml");
        configManager = new ConfigManager(tempPref);

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

    @Test
    void testEndpointHonorsEndpointUserIdSetting() {
        DataTypeDefinition steps = registry.getDataType("steps").orElseThrow();
        Preferences prefs = configManager.getPreferences();
        prefs.setHealthUserId("8677373576871223311");

        // 1. Setting "me" -> endpoint URL must use /v4/users/me/
        prefs.setEndpointUserId("me");
        configManager.savePreferences(prefs);

        assertEquals("me", client.getEffectiveUserId());
        assertFalse(prefs.isUseHealthUserId());

        ApiResponse respMe = client.listDataPoints(steps, null);
        assertTrue(respMe.getRequestUrl().contains("/v4/users/me/dataTypes/steps/dataPoints"),
                "URL must use 'me' when endpointUserId setting is 'me'");
        assertTrue(respMe.getCurlCommand().contains("/v4/users/me/dataTypes/steps/dataPoints"));

        ApiResponse getMe = client.getDataPoint(steps, "sample-123");
        assertTrue(getMe.getRequestUrl().contains("/v4/users/me/dataTypes/steps/dataPoints/sample-123"));

        ApiResponse createMe = client.createDataPoint(steps, "{}");
        assertTrue(createMe.getRequestUrl().contains("/v4/users/me/dataTypes/steps/dataPoints"));

        ApiResponse rollupMe = client.rollUpDataPoints(steps, "{}");
        assertTrue(rollupMe.getRequestUrl().contains("/v4/users/me/dataTypes/steps/dataPoints:rollUp"));

        ApiResponse dailyRollupMe = client.dailyRollUpDataPoints(steps, "{}");
        assertTrue(dailyRollupMe.getRequestUrl().contains("/v4/users/me/dataTypes/steps/dataPoints:dailyRollUp"));

        ApiResponse batchDelMe = client.batchDeleteDataPoints(steps, java.util.List.of("dp-1"));
        assertTrue(batchDelMe.getRequestUrl().contains("/v4/users/me/dataTypes/steps/dataPoints:batchDelete"));

        ApiResponse profileMe = client.getProfile(null);
        assertTrue(profileMe.getRequestUrl().contains("/v4/users/me/profile"));

        ApiResponse identityMe = client.getIdentity(null);
        assertTrue(identityMe.getRequestUrl().contains("/v4/users/me/identity"));

        ApiResponse devicesMe = client.listPairedDevices(null);
        assertTrue(devicesMe.getRequestUrl().contains("/v4/users/me/pairedDevices"));

        // 2. Setting "healthUserId" -> endpoint URL must use /v4/users/8677373576871223311/
        prefs.setEndpointUserId("healthUserId");
        configManager.savePreferences(prefs);

        assertEquals("8677373576871223311", client.getEffectiveUserId());
        assertTrue(prefs.isUseHealthUserId());

        ApiResponse respHealthUser = client.listDataPoints(steps, null);
        assertTrue(respHealthUser.getRequestUrl().contains("/v4/users/8677373576871223311/dataTypes/steps/dataPoints"),
                "URL must use healthUserId when endpointUserId setting is 'healthUserId'");
        assertTrue(respHealthUser.getCurlCommand().contains("/v4/users/8677373576871223311/dataTypes/steps/dataPoints"));

        ApiResponse getHealthUser = client.getDataPoint(steps, "sample-123");
        assertTrue(getHealthUser.getRequestUrl().contains("/v4/users/8677373576871223311/dataTypes/steps/dataPoints/sample-123"));

        ApiResponse createHealthUser = client.createDataPoint(steps, "{}");
        assertTrue(createHealthUser.getRequestUrl().contains("/v4/users/8677373576871223311/dataTypes/steps/dataPoints"));

        ApiResponse rollupHealthUser = client.rollUpDataPoints(steps, "{}");
        assertTrue(rollupHealthUser.getRequestUrl().contains("/v4/users/8677373576871223311/dataTypes/steps/dataPoints:rollUp"));

        ApiResponse dailyRollupHealthUser = client.dailyRollUpDataPoints(steps, "{}");
        assertTrue(dailyRollupHealthUser.getRequestUrl().contains("/v4/users/8677373576871223311/dataTypes/steps/dataPoints:dailyRollUp"));

        ApiResponse batchDelHealthUser = client.batchDeleteDataPoints(steps, java.util.List.of("dp-1"));
        assertTrue(batchDelHealthUser.getRequestUrl().contains("/v4/users/8677373576871223311/dataTypes/steps/dataPoints:batchDelete"));

        ApiResponse profileHealthUser = client.getProfile(null);
        assertTrue(profileHealthUser.getRequestUrl().contains("/v4/users/8677373576871223311/profile"));

        ApiResponse identityHealthUser = client.getIdentity(null);
        assertTrue(identityHealthUser.getRequestUrl().contains("/v4/users/8677373576871223311/identity"));

        ApiResponse devicesHealthUser = client.listPairedDevices(null);
        assertTrue(devicesHealthUser.getRequestUrl().contains("/v4/users/8677373576871223311/pairedDevices"));

        // 3. Test alias setters
        prefs.setUseHealthUserId(false);
        assertEquals("me", client.getEffectiveUserId());

        prefs.setUseHealthUserId(true);
        assertEquals("8677373576871223311", client.getEffectiveUserId());

        prefs.setEndpointUserSyntax("me");
        assertEquals("me", client.getEffectiveUserId());
    }

    @Test
    void testExtractErrorMessageFromGoogleCloudPayload() {
        String googleErrorJson = "{\n" +
                "  \"error\": {\n" +
                "    \"code\": 400,\n" +
                "    \"message\": \"List is not supported for data type floors, but the following actions are supported: reconcile, rollup, dailyRollup\",\n" +
                "    \"status\": \"INVALID_ARGUMENT\"\n" +
                "  }\n" +
                "}";

        String extracted = ApiResponse.extractErrorMessage(googleErrorJson, 400);
        assertEquals("List is not supported for data type floors, but the following actions are supported: reconcile, rollup, dailyRollup",
                extracted);

        ApiResponse resp = new ApiResponse(400, "HTTP 400", null, googleErrorJson, 50, "http://example.com", "GET", null, "curl");
        assertEquals("List is not supported for data type floors, but the following actions are supported: reconcile, rollup, dailyRollup",
                resp.getErrorMessage());
    }

    @Test
    void testExtractErrorMessageOAuthAndPlainFormats() {
        String oauthErrorJson = "{\"error\": \"invalid_grant\", \"error_description\": \"Token has been expired or revoked.\"}";
        String extractedOAuth = ApiResponse.extractErrorMessage(oauthErrorJson, 401);
        assertEquals("invalid_grant: Token has been expired or revoked.", extractedOAuth);

        String simpleJson = "{\"message\": \"Resource not found\"}";
        String extractedSimple = ApiResponse.extractErrorMessage(simpleJson, 404);
        assertEquals("Resource not found", extractedSimple);

        // Success responses shouldn't have error messages
        assertNull(ApiResponse.extractErrorMessage("{\"steps\": 100}", 200));
    }

    @Test
    void testReconcileExportExerciseTcxAndPatchEndpoints() {
        DataTypeDefinition steps = registry.getDataType("steps").orElseThrow();
        DataTypeDefinition exercise = registry.getDataType("exercise").orElseThrow();

        // 1. Reconcile
        ApiResponse recResp = client.reconcileDataPoints(steps, "{}");
        assertNotNull(recResp);
        assertTrue(recResp.getRequestUrl().contains("/dataPoints:reconcile"));
        assertTrue(recResp.getCurlCommand().contains(":reconcile"));

        // 2. ExportExerciseTcx
        ApiResponse exportResp = client.exportExerciseTcx(exercise, "exercise-dp-1");
        assertNotNull(exportResp);
        assertTrue(exportResp.getRequestUrl().contains("/dataPoints/exercise-dp-1:exportExerciseTcx"));
        assertTrue(exportResp.getCurlCommand().contains(":exportExerciseTcx"));

        // 3. Patch
        ApiResponse patchResp = client.patchDataPoint(steps, "steps-dp-1", "{\"value\": 500}");
        assertNotNull(patchResp);
        assertTrue(patchResp.getRequestUrl().contains("/dataPoints/steps-dp-1"));
        assertTrue(patchResp.getCurlCommand().contains("-X PATCH"));
    }

    @Test
    void testIrnProfileAndSettingsEndpoints() {
        // 1. GET /v4/users/{userId}/irnProfile
        ApiResponse irnResp = client.getIrnProfile(null);
        assertNotNull(irnResp);
        assertEquals(200, irnResp.getStatusCode());
        assertTrue(irnResp.getRequestUrl().contains("/users/me/irnProfile"));
        assertTrue(irnResp.getBody().contains("irnProfile"));
        assertTrue(irnResp.getBody().contains("enrollmentStatus"));

        // 2. GET /v4/users/{userId}/settings
        ApiResponse settingsResp = client.getSettings(null);
        assertNotNull(settingsResp);
        assertEquals(200, settingsResp.getStatusCode());
        assertTrue(settingsResp.getRequestUrl().contains("/users/me/settings"));
        assertTrue(settingsResp.getBody().contains("settings"));
        assertTrue(settingsResp.getBody().contains("timezone"));

        // 3. PATCH /v4/users/{userId}/profile (updateProfile)
        ApiResponse updateProfileResp = client.updateProfile(null, "{\"displayName\": \"Updated User\"}");
        assertNotNull(updateProfileResp);
        assertEquals(200, updateProfileResp.getStatusCode());
        assertTrue(updateProfileResp.getCurlCommand().contains("-X PATCH"));
        assertTrue(updateProfileResp.getBody().contains("Updated User"));

        // 4. PATCH /v4/users/{userId}/settings (updateSettings)
        ApiResponse updateSettingsResp = client.updateSettings(null, "{\"timezone\": \"America/Chicago\"}");
        assertNotNull(updateSettingsResp);
        assertEquals(200, updateSettingsResp.getStatusCode());
        assertTrue(updateSettingsResp.getCurlCommand().contains("-X PATCH"));
        assertTrue(updateSettingsResp.getBody().contains("America/Chicago"));
    }


    @Test
    void testSuiteExecutionEngineEnableAllEndpoints() {
        SuiteExecutionEngine engine = new SuiteExecutionEngine(configManager, registry, client);

        // When enableAllEndpoints is false, patch is not supported by any datatype
        configManager.getPreferences().setEnableAllEndpoints(false);
        List<TestResult> resultsDisabled = engine.executeAllDataTypesTest("patch");
        assertEquals(0, resultsDisabled.size(), "No datatypes support patch by default");

        // When enableAllEndpoints is true, all datatypes must run patch test
        configManager.getPreferences().setEnableAllEndpoints(true);
        List<TestResult> resultsEnabled = engine.executeAllDataTypesTest("patch");
        assertEquals(registry.getAllDataTypes().size(), resultsEnabled.size(),
                "All datatypes must be tested when enableAllEndpoints is true");
        for (TestResult r : resultsEnabled) {
            assertEquals("patch", r.getEndpoint());
        }
    }
}

