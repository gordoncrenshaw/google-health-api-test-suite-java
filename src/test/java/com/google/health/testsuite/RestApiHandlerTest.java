package com.google.health.testsuite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.health.testsuite.auth.OAuthService;
import com.google.health.testsuite.client.HealthApiClient;
import com.google.health.testsuite.common.AppVersion;
import com.google.health.testsuite.config.ConfigManager;
import com.google.health.testsuite.config.DataTypeRegistry;
import com.google.health.testsuite.config.Preferences;
import com.google.health.testsuite.model.TestResult;
import com.google.health.testsuite.runner.ScriptRunner;
import com.google.health.testsuite.runner.SuiteExecutionEngine;
import com.google.health.testsuite.server.RestApiHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RestApiHandlerTest {

    private File tempPrefFile;
    private File tempSecretJsonFile;
    private File tempTokensDir;
    private ConfigManager configManager;
    private OAuthService oAuthService;
    private SuiteExecutionEngine engine;
    private HttpServer server;
    private int port;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper jsonMapper = new ObjectMapper();

    @BeforeEach
    public void setUp() throws Exception {
        tempPrefFile = File.createTempFile("test_preferences", ".yaml");
        tempSecretJsonFile = File.createTempFile("client_secret", ".json");
        tempTokensDir = Files.createTempDirectory("test_tokens_").toFile();

        Files.writeString(tempSecretJsonFile.toPath(),
                "{\"web\":{\"client_id\":\"test-client-id.apps.googleusercontent.com\",\"client_secret\":\"test-secret\"}}");

        configManager = new ConfigManager(tempPrefFile, tempSecretJsonFile);
        Preferences prefs = configManager.getPreferences();
        prefs.setMockMode(true);
        File tempManifest = File.createTempFile("test_manifest", ".yaml");
        tempManifest.deleteOnExit();
        prefs.setSuiteManifestPath(tempManifest.getAbsolutePath());
        configManager.savePreferences(prefs);

        oAuthService = new OAuthService(configManager, tempTokensDir);
        HealthApiClient apiClient = new HealthApiClient(configManager, oAuthService);
        DataTypeRegistry registry = new DataTypeRegistry(new File("config/datatypes.yaml"));
        engine = new SuiteExecutionEngine(configManager, registry, apiClient);

        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api", new RestApiHandler(engine, oAuthService));
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (server != null) {
            server.stop(0);
        }
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
    void testGetScopesEndpoint() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/scopes"))
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());

        JsonNode root = jsonMapper.readTree(resp.body());
        assertTrue(root.has("scopes"));
        assertTrue(root.get("scopes").isArray());
        assertTrue(root.get("scopes").size() >= 10);
    }

    @Test
    void testDynamicScopeAdditionPresentedByApi() throws Exception {
        // Add a brand new scope to the preferences.yaml file
        File prefFile = configManager.getPreferencesFile();
        String currentContent = Files.exists(prefFile.toPath()) ? Files.readString(prefFile.toPath()) : "scopes:\n";
        String customScope = "https://www.googleapis.com/auth/googlehealth.custom_symptom.readonly";
        Files.writeString(prefFile.toPath(), currentContent + "  - \"" + customScope + "\"\n");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/scopes"))
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());

        JsonNode root = jsonMapper.readTree(resp.body());
        boolean foundCustom = false;
        for (JsonNode s : root.get("scopes")) {
            if (customScope.equals(s.asText())) {
                foundCustom = true;
                break;
            }
        }
        assertTrue(foundCustom, "Newly added scope in preferences file must be presented by GET /api/scopes");
    }

    @Test
    void testAuthUrlWithSelectedScopes() throws Exception {
        String jsonPayload = """
                {
                  "scopes": [
                    "https://www.googleapis.com/auth/googlehealth.activity_and_fitness.readonly",
                    "https://www.googleapis.com/auth/googlehealth.sleep.readonly"
                  ]
                }
                """;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/auth/url"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());

        JsonNode root = jsonMapper.readTree(resp.body());
        assertTrue(root.has("authUrl"));
        String url = root.get("authUrl").asText();
        assertTrue(url.contains("scope="));
        assertTrue(url.contains("activity_and_fitness.readonly"));
        assertTrue(url.contains("sleep.readonly"));
    }

    @Test
    void testRunAllTestsQueriesStandardTestsYaml() throws Exception {
        // Configure mock mode
        Preferences prefs = configManager.getPreferences();
        prefs.setMockMode(true);
        configManager.savePreferences(prefs);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/test/run-all"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"scriptPath\":\"scripts/standard_tests-steps.yaml\"}"))
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());

        JsonNode root = jsonMapper.readTree(resp.body());
        assertFalse(root.isEmpty(), "Should return results from test script");
        for (JsonNode item : root) {
            assertTrue(item.get("passed").asBoolean(), "Step should pass: " + item.get("testName").asText());
        }
    }

    @Test
    void testRunAllTestsFailsWhenStandardTestsYamlIsMissing() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/test/run-all"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"scriptPath\":\"scripts/non_existent_tests.yaml\"}"))
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, resp.statusCode());

        JsonNode root = jsonMapper.readTree(resp.body());
        assertTrue(root.has("error"));
        assertTrue(root.get("error").asText().contains("file is not found"));
    }

    @Test
    void testRunStreamTestsRealTimeEvents() throws Exception {
        Preferences prefs = configManager.getPreferences();
        prefs.setMockMode(true);
        configManager.savePreferences(prefs);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/test/run-stream"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"scriptPath\":\"scripts/standard_tests-steps.yaml\"}"))
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());

        String[] lines = resp.body().split("\n");
        assertTrue(lines.length >= 3, "Should contain start, step events, and complete event");

        JsonNode firstEvent = jsonMapper.readTree(lines[0]);
        assertEquals("start", firstEvent.get("type").asText());
        int totalExpected = firstEvent.get("total").asInt();
        assertTrue(totalExpected > 0);

        JsonNode secondEvent = jsonMapper.readTree(lines[1]);
        assertEquals("step", secondEvent.get("type").asText());
        assertEquals(1, secondEvent.get("index").asInt());
        assertTrue(secondEvent.has("result"));

        JsonNode lastEvent = jsonMapper.readTree(lines[lines.length - 1]);
        assertEquals("complete", lastEvent.get("type").asText());
        assertEquals(totalExpected, lastEvent.get("total").asInt());
        assertEquals(totalExpected, lastEvent.get("passed").asInt());
    }

    @Test
    void testApiVersionReturnsAppVersion() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/version"))
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());

        JsonNode root = jsonMapper.readTree(resp.body());
        assertEquals(AppVersion.VERSION, root.get("version").asText());
        assertEquals("v" + AppVersion.VERSION, root.get("formattedVersion").asText());
        assertEquals("v4", root.get("apiTarget").asText());
    }

    @Test
    void testRunScriptEndpointSuccess() throws Exception {
        String reqBody = "{\"scriptPath\":\"scripts/sample_suite.yaml\"}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/test/script"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(reqBody))
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());

        JsonNode root = jsonMapper.readTree(resp.body());
        assertEquals(0, root.get("exitCode").asInt());
        assertTrue(root.get("success").asBoolean());
        assertEquals(12, root.get("total").asInt());
        assertEquals(12, root.get("passed").asInt());
        assertEquals(0, root.get("failed").asInt());
        assertTrue(root.get("failedTestNames").isArray());
        assertEquals(0, root.get("failedTestNames").size());
        assertTrue(root.get("failedTests").isArray());
        assertEquals(0, root.get("failedTests").size());
    }

    @Test
    void testRunScriptEndpointFailureWithFailedTestNames() throws Exception {
        File tempScript = File.createTempFile("api_failing_script", ".yaml");
        tempScript.deleteOnExit();

        String yaml = """
            name: "API Failing Test Suite"
            stopOnError: false
            steps:
              - name: "Step A Passed"
                action: "get_identity"
                assertStatus: [200]
              - name: "Step B Failed Status"
                action: "get_identity"
                assertStatus: [404]
            """;
        Files.writeString(tempScript.toPath(), yaml);

        String reqBody = "{\"scriptPath\":\"" + tempScript.getAbsolutePath() + "\"}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/test/script"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(reqBody))
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());

        JsonNode root = jsonMapper.readTree(resp.body());
        assertEquals(1, root.get("exitCode").asInt());
        assertFalse(root.get("success").asBoolean());
        assertEquals(2, root.get("total").asInt());
        assertEquals(1, root.get("passed").asInt());
        assertEquals(1, root.get("failed").asInt());

        // Verify Issue #14: failedTestNames array
        assertTrue(root.has("failedTestNames"));
        assertTrue(root.get("failedTestNames").isArray());
        assertEquals(1, root.get("failedTestNames").size());
        assertEquals("Step B Failed Status", root.get("failedTestNames").get(0).asText());

        // Verify Issue #14: failedTests detailed array
        assertTrue(root.has("failedTests"));
        assertTrue(root.get("failedTests").isArray());
        assertEquals(1, root.get("failedTests").size());
        JsonNode failedItem = root.get("failedTests").get(0);
        assertEquals("Step B Failed Status", failedItem.get("testName").asText());
        assertEquals(200, failedItem.get("statusCode").asInt());
        assertTrue(failedItem.has("message"));

        // Verify message summary explicitly mentions failed test name
        assertTrue(root.get("message").asText().contains("Step B Failed Status"));
    }

    @Test
    void testRunScriptEndpointFileNotFound() throws Exception {
        String reqBody = "{\"scriptPath\":\"scripts/non_existent_file.yaml\"}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/test/script"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(reqBody))
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, resp.statusCode());
    }

    @Test
    void testRunScriptEndpointWithYamlSyntaxErrorReturnsErrorMessage() throws Exception {
        File tempScript = File.createTempFile("syntax_error_script", ".yaml");
        tempScript.deleteOnExit();

        String badYaml = """
            name: "Syntax Error Suite"
            steps:
              - name: "Bad Step"
                queryParams:
                  filter: 'bad',
            """;
        Files.writeString(tempScript.toPath(), badYaml);

        String reqBody = "{\"scriptPath\":\"" + tempScript.getAbsolutePath() + "\"}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/test/script"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(reqBody))
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());

        JsonNode root = jsonMapper.readTree(resp.body());
        assertEquals(1, root.get("exitCode").asInt());
        assertFalse(root.get("success").asBoolean());
        assertTrue(root.has("errorMessage"));
        assertTrue(root.get("errorMessage").asText().toLowerCase().contains("error parsing script file"));
        assertTrue(root.get("message").asText().toLowerCase().contains("error parsing script file"));
    }

    @Test
    void testGetManifestEndpoint() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/manifest"))
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());

        JsonNode root = jsonMapper.readTree(resp.body());
        assertTrue(root.has("name"));
        assertTrue(root.has("scripts"));
        assertTrue(root.get("scripts").isArray());
        assertTrue(root.has("manifestPath"));
        assertTrue(root.has("totalTests"));
    }

    @Test
    void testSaveManifestEndpoint() throws Exception {
        String body = """
            {
              "name": "Custom Suite",
              "description": "My custom tests",
              "scripts": ["scripts/steps_tests.yaml", "scripts/distance_tests.yaml"]
            }
            """;
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/manifest"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());

        JsonNode root = jsonMapper.readTree(resp.body());
        assertTrue(root.get("success").asBoolean());
        assertEquals("Custom Suite", root.get("name").asText());
        assertEquals(2, root.get("scripts").size());
        assertTrue(root.has("totalTests"));
    }

    @Test
    void testManifestCalculatesUserIdBothAsTwoTests() throws Exception {
        File tempScript = new File("scripts/temp_manifest_both_test.yaml");
        tempScript.deleteOnExit();

        String scriptYaml = """
            name: "Temp Manifest Both Users Script"
            userId: both
            stopOnError: false
            steps:
              - name: "Step 1"
                action: "profile"
              - name: "Step 2"
                action: "devices"
            """;
        Files.writeString(tempScript.toPath(), scriptYaml);

        try {
            String saveBody = """
                {
                  "name": "Manifest Both Test",
                  "description": "Testing userId both counted as 2 tests",
                  "scripts": ["scripts/temp_manifest_both_test.yaml"]
                }
                """;
            HttpRequest postReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/manifest"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(saveBody))
                    .build();

            HttpResponse<String> postResp = httpClient.send(postReq, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, postResp.statusCode());
            JsonNode postRoot = jsonMapper.readTree(postResp.body());
            assertEquals(4, postRoot.get("totalTests").asInt(), "2 steps with userId: both should calculate as 4 tests");

            HttpRequest getReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/manifest"))
                    .GET()
                    .build();
            HttpResponse<String> getResp = httpClient.send(getReq, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, getResp.statusCode());
            JsonNode getRoot = jsonMapper.readTree(getResp.body());
            assertEquals(4, getRoot.get("totalTests").asInt(), "GET manifest should return totalTests = 4");

            // Also verify GET /api/scripts returns totalSteps = 4 for temp_manifest_both_test.yaml
            HttpRequest scriptsReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/scripts"))
                    .GET()
                    .build();
            HttpResponse<String> scriptsResp = httpClient.send(scriptsReq, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, scriptsResp.statusCode());
            JsonNode scriptsArr = jsonMapper.readTree(scriptsResp.body());
            boolean found = false;
            for (JsonNode sn : scriptsArr) {
                if ("temp_manifest_both_test.yaml".equals(sn.path("filename").asText())) {
                    found = true;
                    assertEquals(4, sn.path("totalSteps").asInt());
                    break;
                }
            }
            assertTrue(found, "temp_manifest_both_test.yaml should appear in GET /api/scripts");
        } finally {
            if (tempScript.exists()) {
                tempScript.delete();
            }
        }
    }

    @Test
    void testSaveCustomTest_CreateAndAppend_UserIdOnStepNotRoot() throws Exception {
        File customFile = new File("scripts/custom_tests_junit_unit_test.yaml");
        if (customFile.exists()) {
            customFile.delete();
        }

        try {
            // 1. Create mode
            String createBody = """
                {
                  "fileName": "junit_unit_test",
                  "mode": "create",
                  "suiteName": "JUnit Custom Steps",
                  "description": "Custom tests created by JUnit",
                  "autoAddToManifest": false,
                  "step": {
                    "name": "Custom Steps List Step",
                    "action": "list",
                    "dataType": "steps",
                    "userId": "me",
                    "ticketId": "b/12345678",
                    "queryParams": {
                      "pageSize": "10"
                    },
                    "assertStatus": [200],
                    "validateRange": true
                  }
                }
                """;
            HttpRequest req1 = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/custom-tests"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(createBody))
                    .build();

            HttpResponse<String> resp1 = httpClient.send(req1, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, resp1.statusCode());
            JsonNode root1 = jsonMapper.readTree(resp1.body());
            assertTrue(root1.get("success").asBoolean());
            assertEquals(1, root1.get("totalSteps").asInt());
            assertEquals("custom_tests_junit_unit_test.yaml", root1.get("fileName").asText());

            assertTrue(customFile.exists());
            String yamlContent = Files.readString(customFile.toPath());

            int stepsIdx = yamlContent.indexOf("steps:");
            assertTrue(stepsIdx > 0);
            String beforeSteps = yamlContent.substring(0, stepsIdx);
            assertFalse(beforeSteps.contains("userId:"), "Root YAML should NOT contain userId! Found:\n" + yamlContent);

            // userId MUST appear on the step:
            String afterSteps = yamlContent.substring(stepsIdx);
            assertTrue(afterSteps.contains("userId: \"me\"") || afterSteps.contains("userId: me"), "Step must contain userId!");
            assertTrue(afterSteps.contains("ticketId: \"b/12345678\"") || afterSteps.contains("ticketId: b/12345678"));

            // 2. Append mode
            String appendBody = """
                {
                  "fileName": "custom_tests_junit_unit_test.yaml",
                  "mode": "append",
                  "autoAddToManifest": false,
                  "step": {
                    "name": "Custom Steps Reconcile Step",
                    "action": "reconcile",
                    "dataType": "steps",
                    "userId": "both",
                    "queryParams": {
                      "pageSize": "5"
                    },
                    "assertStatus": [200],
                    "validateRange": true
                  }
                }
                """;
            HttpRequest req2 = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/custom-tests"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(appendBody))
                    .build();

            HttpResponse<String> resp2 = httpClient.send(req2, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, resp2.statusCode());
            JsonNode root2 = jsonMapper.readTree(resp2.body());
            assertTrue(root2.get("success").asBoolean());
            assertEquals(2, root2.get("totalSteps").asInt());

            // 3. Test list endpoint
            HttpRequest listReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/custom-tests"))
                    .GET()
                    .build();
            HttpResponse<String> listResp = httpClient.send(listReq, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, listResp.statusCode());
            JsonNode listRoot = jsonMapper.readTree(listResp.body());
            assertTrue(listRoot.isArray());
            boolean found = false;
            for (JsonNode item : listRoot) {
                if ("custom_tests_junit_unit_test.yaml".equals(item.path("filename").asText())) {
                    found = true;
                    assertEquals(2, item.path("totalSteps").asInt());
                    break;
                }
            }
            assertTrue(found, "Newly created custom test file should appear in GET /api/custom-tests");

            // 4. Test overwrite endpoint
            String overwriteBody = """
                {
                  "mode": "overwrite",
                  "fileName": "junit_unit_test",
                  "suiteName": "JUnit Custom Overwritten Suite",
                  "description": "Overwritten suite with 1 step",
                  "autoAddToManifest": false,
                  "step": {
                    "name": "Custom Steps Overwritten Single Step",
                    "action": "list",
                    "dataType": "steps",
                    "userId": "me",
                    "assertStatus": [200]
                  }
                }
                """;
            HttpRequest req3 = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/custom-tests"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(overwriteBody))
                    .build();
            HttpResponse<String> resp3 = httpClient.send(req3, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, resp3.statusCode());
            JsonNode root3 = jsonMapper.readTree(resp3.body());
            assertTrue(root3.get("success").asBoolean());
            assertEquals(1, root3.get("totalSteps").asInt(), "Overwritten file should only have 1 step");

        } finally {
            if (customFile.exists()) {
                customFile.delete();
            }
        }
    }

    @Test
    void testSaveCustomTest_BodyPrettyFormattedInYaml() throws Exception {
        File customFile = new File("scripts/custom_tests_junit_body_pretty.yaml");
        if (customFile.exists()) {
            customFile.delete();
        }

        try {
            String payload = """
                {
                  "fileName": "junit_body_pretty",
                  "mode": "create",
                  "suiteName": "JUnit Pretty Body Test",
                  "autoAddToManifest": false,
                  "step": {
                    "name": "Custom: Steps dailyRollUp Pretty",
                    "action": "dailyRollUp",
                    "dataType": "steps",
                    "userId": "me",
                    "body": "{\\"range\\":{\\"start\\":{\\"date\\":{\\"year\\":2026,\\"month\\":10,\\"day\\":10},\\"time\\":{\\"hours\\":0,\\"minutes\\":0,\\"seconds\\":0}},\\"end\\":{\\"date\\":{\\"year\\":2026,\\"month\\":10,\\"day\\":17},\\"time\\":{\\"hours\\":23,\\"minutes\\":59,\\"seconds\\":59}}},\\"windowSizeDays\\":1}",
                    "assertStatus": [200],
                    "validateRange": true
                  }
                }
                """;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/custom-tests"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, resp.statusCode());

            assertTrue(customFile.exists());
            String yamlContent = Files.readString(customFile.toPath());

            // Verify body is formatted using literal block scalar (body: | or body: |-)
            assertTrue(yamlContent.contains("body: |-") || yamlContent.contains("body: |"),
                    "YAML must use literal block scalar for body! Found:\n" + yamlContent);
            assertFalse(yamlContent.contains("\\n"),
                    "YAML should not contain escaped newlines in body! Found:\n" + yamlContent);
            assertTrue(yamlContent.contains("\"year\" : 2026") || yamlContent.contains("\"year\": 2026"),
                    "YAML must contain formatted JSON with year field! Found:\n" + yamlContent);

            // Verify it can be read back and executed cleanly
            ScriptRunner runner = new ScriptRunner(engine);
            List<TestResult> results = runner.executeScript(customFile);
            assertEquals(1, results.size());
            assertTrue(results.get(0).isPassed(), "Pretty JSON body step must execute successfully: " + results.get(0).getMessage());

        } finally {
            if (customFile.exists()) {
                customFile.delete();
            }
        }
    }

    @Test
    void testListScriptsReturnsAlphabeticallySortedFiles() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/scripts"))
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());

        JsonNode scriptsNode = jsonMapper.readTree(resp.body());
        assertTrue(scriptsNode.isArray());
        assertTrue(scriptsNode.size() > 1, "There should be multiple scripts returned");

        String prevName = null;
        for (JsonNode script : scriptsNode) {
            String currentName = script.get("filename").asText();
            if (prevName != null) {
                assertTrue(currentName.compareToIgnoreCase(prevName) >= 0,
                        "Scripts must be in alphabetical order! Expected '" + currentName + "' to be >= '" + prevName + "'");
            }
            prevName = currentName;
        }
    }

    @Test
    void testGetDataTypesReturnsAlphabeticallySortedDataTypes() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/datatypes"))
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());

        JsonNode typesNode = jsonMapper.readTree(resp.body());
        assertTrue(typesNode.isArray());
        assertTrue(typesNode.size() > 1, "There should be multiple data types returned");

        String prevName = null;
        for (JsonNode dt : typesNode) {
            String currentName = dt.get("name").asText();
            if (prevName != null) {
                assertTrue(currentName.compareToIgnoreCase(prevName) >= 0,
                        "Data types must be in alphabetical order! Expected '" + currentName + "' to be >= '" + prevName + "'");
            }
            prevName = currentName;
        }
    }

    @Test
    void testUpdateAndDeleteDataTypeEndpoints() throws Exception {
        String testDtName = "test-endpoint-crud-dt";

        // 1. POST a temporary data type
        String createJson = """
            {
              "name": "%s",
              "displayName": "Initial Temp DT",
              "endpointVersion": "v4",
              "unit": "count",
              "endpointsSupported": {
                "list": true,
                "get": true
              }
            }
            """.formatted(testDtName);

        HttpRequest createReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/datatypes"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(createJson))
                .build();

        HttpResponse<String> createResp = httpClient.send(createReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, createResp.statusCode());

        // 2. PUT to update the data type
        String updateJson = """
            {
              "name": "%s",
              "displayName": "Updated Temp DT Display",
              "endpointVersion": "v4",
              "unit": "samples",
              "endpointsSupported": {
                "list": true,
                "get": true,
                "create": true
              }
            }
            """.formatted(testDtName);

        HttpRequest updateReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/datatypes"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(updateJson))
                .build();

        HttpResponse<String> updateResp = httpClient.send(updateReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, updateResp.statusCode());
        JsonNode updateBody = jsonMapper.readTree(updateResp.body());
        assertTrue(updateBody.get("success").asBoolean());
        assertEquals("Updated Temp DT Display", updateBody.get("dataType").get("displayName").asText());

        // 3. DELETE query parameter
        HttpRequest deleteReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/datatypes?name=" + testDtName))
                .DELETE()
                .build();

        HttpResponse<String> deleteResp = httpClient.send(deleteReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, deleteResp.statusCode());
        JsonNode deleteBody = jsonMapper.readTree(deleteResp.body());
        assertTrue(deleteBody.get("success").asBoolean());

        // 4. DELETE again should return 404
        HttpResponse<String> deleteAgainResp = httpClient.send(deleteReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, deleteAgainResp.statusCode());

        // 5. PUT non-existent should return 404
        HttpResponse<String> updateNonExistent = httpClient.send(updateReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, updateNonExistent.statusCode());
    }
}


