package com.google.health.testsuite;

import com.google.health.testsuite.auth.OAuthService;
import com.google.health.testsuite.client.HealthApiClient;
import com.google.health.testsuite.config.ConfigManager;
import com.google.health.testsuite.config.DataTypeRegistry;
import com.google.health.testsuite.config.Preferences;
import com.google.health.testsuite.model.ApiResponse;
import com.google.health.testsuite.model.TestResult;
import com.google.health.testsuite.model.TestStep;
import com.google.health.testsuite.model.ValidationResult;
import com.google.health.testsuite.runner.ErrorReportGenerator;
import com.google.health.testsuite.runner.SuiteExecutionEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ErrorReportGeneratorTest {

    private File tempFailedDir;
    private ErrorReportGenerator generator;

    @BeforeEach
    void setUp() throws Exception {
        tempFailedDir = Files.createTempDirectory("failed_tests_gen_").toFile();
        generator = new ErrorReportGenerator(tempFailedDir.getAbsolutePath());
    }

    @AfterEach
    void tearDown() {
        if (generator != null) {
            generator.deleteFailedTestFiles();
        }
    }

    @Test
    void testSaveHttpStatusFailureToMarkdown() throws Exception {
        String requestUrl = "https://health.googleapis.com/v4/users/me/dataTypes/heart-rate/dataPoints";
        String curlCmd = "curl -X GET \"" + requestUrl + "\" \\\n  -H \"Authorization: Bearer mock-token\"";
        String respBody = "{\n  \"error\": {\n    \"code\": 403,\n    \"message\": \"Request had insufficient authentication scopes.\"\n  }\n}";

        ApiResponse resp = new ApiResponse(
                403, "Forbidden", Collections.emptyMap(),
                respBody, 145L, requestUrl, "GET", null, curlCmd);

        TestStep step = new TestStep();
        step.setName("Standard: Heart Rate List (me)");
        step.setAction("list");
        step.setDataType("heart-rate");
        step.setAssertStatus(List.of(200));

        TestResult result = new TestResult(
                step.getName(), "heart-rate", "list", false,
                403, 145L, "Expected status [200] but got 403 - Request had insufficient authentication scopes.",
                ValidationResult.skipped("Range validation disabled"), resp);
        result.setScriptFile("standard_tests.yaml");

        String path = generator.saveFailedTestToMarkdown(result, step);
        assertNotNull(path);
        assertEquals(path, result.getErrorReportPath());

        File savedFile = new File(path);
        assertTrue(savedFile.exists(), "Markdown report file must exist");
        assertEquals("403", savedFile.getParentFile().getName(), "Report should be saved in status code subfolder 403");
        assertTrue(savedFile.getName().startsWith("Standard_ Heart Rate List (me) - "));
        assertTrue(savedFile.getName().endsWith(".md"));

        String content = Files.readString(savedFile.toPath(), StandardCharsets.UTF_8);
        assertTrue(content.contains("# Google Health API Test Failure - Standard: Heart Rate List (me)"));
        assertTrue(content.contains("- **Test Name:** Standard: Heart Rate List (me)"));
        assertTrue(content.contains("- **Script File:** standard_tests.yaml"));
        assertTrue(content.contains("- **Target Endpoint:** list"));
        assertTrue(content.contains("- **Data Type:** heart-rate"));
        assertTrue(content.contains("- **HTTP Status Code:** 403 (Expected: [200])"));
        assertTrue(content.contains("- **Execution Duration:** 145 ms"));
        assertTrue(content.contains("## Failure Summary"));
        assertTrue(content.contains("Expected status [200] but got 403"));
        assertTrue(content.contains("## Execution Sequence"));
        assertTrue(content.contains("This test executed 1 HTTP request(s)."));
        assertTrue(content.contains("### Request 1 of 1: list"));
        assertTrue(content.contains("- **Endpoint Syntax:** `GET " + requestUrl + "`"));
        assertTrue(content.contains("#### cURL Command"));
        assertTrue(content.contains(curlCmd));
        assertTrue(content.contains("#### Request Body\n```json\n[None]\n```"));
        assertTrue(content.contains("#### Response Body"));
        assertTrue(content.contains("Request had insufficient authentication scopes."));
    }

    @Test
    void testSaveLatencyFailureToMarkdown() throws Exception {
        String requestUrl = "https://health.googleapis.com/v4/users/me/dataTypes/steps/dataPoints";
        String curlCmd = "curl -X GET \"" + requestUrl + "\"";
        String respBody = "{\"dataPoints\":[]}";

        ApiResponse resp = new ApiResponse(
                200, "OK", Collections.emptyMap(),
                respBody, 1450L, requestUrl, "GET", null, curlCmd);

        TestStep step = new TestStep();
        step.setName("Latency: Steps List (me)");
        step.setAction("list");
        step.setDataType("steps");
        step.setAssertStatus(List.of(200));
        step.setMaxLatencyMs(1000L);

        TestResult result = new TestResult(
                step.getName(), "steps", "list", false,
                200, 1450L, "Latency 1450ms exceeded max allowed 1000ms.",
                ValidationResult.pass("Valid", null, null, null), resp);
        result.setMaxLatencyMs(1000L);
        result.setScriptFile("latency_tests.yaml");

        String path = generator.saveFailedTestToMarkdown(result, step);
        assertNotNull(path);

        File savedFile = new File(path);
        assertTrue(savedFile.exists());
        assertEquals("latency", savedFile.getParentFile().getName(), "Latency failure should be saved in latency/ subfolder");

        String content = Files.readString(savedFile.toPath(), StandardCharsets.UTF_8);
        assertTrue(content.contains("# Google Health API Test Failure - Latency: Steps List (me)"));
        assertTrue(content.contains("- **Acceptable Latency Threshold:** 1000 ms"));
        assertTrue(content.contains("- **Actual Execution Duration:** 1450 ms"));
        assertTrue(content.contains("## Latency Failure Summary"));
        assertTrue(content.contains("exceeded the maximum acceptable latency threshold of **1000 ms**"));
    }

    @Test
    void testSaveVerifyWithGetValidationFailureWithTwoRequests() throws Exception {
        String patchUrl = "https://health.googleapis.com/v4/users/me/dataTypes/body-fat/dataPoints/dp-1";
        String patchBody = "{\"bodyFat\":{\"percentage\":150.0}}";
        ApiResponse patchResp = new ApiResponse(
                400, "Bad Request", Collections.emptyMap(),
                "{\"error\":{\"code\":400,\"message\":\"percentage above upper bound\"}}",
                52L, patchUrl, "PATCH", patchBody, "curl -X PATCH \"" + patchUrl + "\"");

        String getUrl = "https://health.googleapis.com/v4/users/me/dataTypes/body-fat/dataPoints/dp-1";
        ApiResponse getResp = new ApiResponse(
                200, "OK", Collections.emptyMap(),
                "{\"bodyFat\":{\"percentage\":150.0}}",
                38L, getUrl, "GET", null, "curl -X GET \"" + getUrl + "\"");

        TestStep step = new TestStep();
        step.setName("Negative: Body Fat Update Above Upper Bound (> 100%) (me)");
        step.setAction("patch");
        step.setDataType("body-fat");
        step.setVerifyWithGet(true);
        step.setAssertStatus(List.of(400));

        TestResult result = new TestResult(
                step.getName(), "body-fat", "patch", false,
                400, 52L, "get (body-fat) verification failed for field 'bodyFat.percentage': expected 22.8 but got 150.0.",
                ValidationResult.skipped("Range validation disabled"), patchResp);
        result.setVerificationResponse(getResp);
        result.setScriptFile("negative_tests_updateBodyFat.yaml");

        String path = generator.saveFailedTestToMarkdown(result, step);
        assertNotNull(path);

        File savedFile = new File(path);
        assertTrue(savedFile.exists());
        assertEquals("validation", savedFile.getParentFile().getName(),
                "When HTTP status matched expected (400) but verifyWithGet failed, report goes into validation/");

        String content = Files.readString(savedFile.toPath(), StandardCharsets.UTF_8);
        assertTrue(content.contains("This test executed 2 HTTP request(s)."));
        assertTrue(content.contains("### Request 1 of 2: patch"));
        assertTrue(content.contains("### Request 2 of 2: GET (Verify)"));
    }

    @Test
    void testDeleteFailedTestFilesRemovesFilesAndDirectory() throws Exception {
        ApiResponse resp = new ApiResponse(
                500, "Internal Server Error", Collections.emptyMap(),
                "{\"error\":\"server failure\"}", 90L, "https://health.googleapis.com/v4/users/me/profile",
                "GET", null, "curl -X GET \"https://health.googleapis.com/v4/users/me/profile\"");

        TestResult r1 = TestResult.failure("Fail Test 1", "profile", "getProfile", resp, null, "HTTP 500");
        TestResult r2 = TestResult.failure("Fail Test 2", "settings", "getSettings", resp, null, "HTTP 500");

        generator.saveFailedTestToMarkdown(r1);
        generator.saveFailedTestToMarkdown(r2);

        assertEquals(2, generator.countFailedTestFiles());
        assertEquals(2, generator.listFailedTestFiles().size());

        int deleted = generator.deleteFailedTestFiles();
        assertEquals(2, deleted);
        assertEquals(0, generator.countFailedTestFiles());
        assertFalse(tempFailedDir.exists(), "Base failed-tests directory should be removed after deleteFailedTestFiles()");
    }

    @Test
    void testEngineRespectsRecordFailedTestsPreference() throws Exception {
        File tempPref = File.createTempFile("pref_err_test", ".yaml");
        tempPref.deleteOnExit();
        ConfigManager cm = new ConfigManager(tempPref);
        Preferences prefs = cm.getPreferences();
        prefs.setMockMode(true);
        prefs.setRecordFailedTests(false);
        cm.savePreferences(prefs);

        DataTypeRegistry reg = new DataTypeRegistry(new File("config/datatypes.yaml"));
        OAuthService auth = new OAuthService(cm);
        HealthApiClient client = new HealthApiClient(cm, auth);
        SuiteExecutionEngine engine = new SuiteExecutionEngine(cm, reg, client);
        engine.setErrorReportGenerator(generator);

        TestStep failingStep = new TestStep();
        failingStep.setName("Failing Step With Logging Disabled");
        failingStep.setAction("get_identity");
        failingStep.setAssertStatus(List.of(999));

        TestResult res1 = engine.executeStep(failingStep, "test.yaml");
        assertFalse(res1.isPassed());
        assertNull(res1.getErrorReportPath(), "No report should be generated when recordFailedTests is false");
        assertEquals(0, generator.countFailedTestFiles());

        // Now enable recordFailedTests
        prefs.setRecordFailedTests(true);
        cm.savePreferences(prefs);

        TestResult res2 = engine.executeStep(failingStep, "test.yaml");
        assertFalse(res2.isPassed());
        assertNotNull(res2.getErrorReportPath(), "Report path must be populated when recordFailedTests is true");
        assertEquals(1, generator.countFailedTestFiles());
    }

    @Test
    void testBugIdPrintedInFailedTestReportLog() throws Exception {
        File tempPref = File.createTempFile("pref_bugid_test", ".yaml");
        tempPref.deleteOnExit();
        ConfigManager cm = new ConfigManager(tempPref);
        Preferences prefs = cm.getPreferences();
        prefs.setMockMode(true);
        prefs.setRecordFailedTests(true);
        cm.savePreferences(prefs);

        DataTypeRegistry reg = new DataTypeRegistry(new File("config/datatypes.yaml"));
        OAuthService auth = new OAuthService(cm);
        HealthApiClient client = new HealthApiClient(cm, auth);
        SuiteExecutionEngine engine = new SuiteExecutionEngine(cm, reg, client);
        engine.setErrorReportGenerator(generator);

        TestStep failingStep = new TestStep();
        failingStep.setName("Custom: Activity Level List (userId: dummyValue)");
        failingStep.setAction("list");
        failingStep.setDataType("activity-level");
        failingStep.setUserId("dummyValue");
        failingStep.setBugId("b/398765432");
        failingStep.setAssertStatus(List.of(999));

        TestResult result = engine.executeStep(failingStep, "custom_tests-activityLevel.yaml");
        assertFalse(result.isPassed());
        assertEquals("b/398765432", result.getBugId());
        assertNotNull(result.getErrorReportPath());

        String content = Files.readString(new File(result.getErrorReportPath()).toPath(), StandardCharsets.UTF_8);
        assertTrue(content.contains("- **Bug ID:** b/398765432"), "Failed test report must print Bug ID: " + content);
    }
}

