package com.google.health.testsuite;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.health.testsuite.auth.OAuthService;
import com.google.health.testsuite.client.HealthApiClient;
import com.google.health.testsuite.config.ConfigManager;
import com.google.health.testsuite.config.DataTypeRegistry;
import com.google.health.testsuite.config.Preferences;
import com.google.health.testsuite.runner.ScriptRunner;
import com.google.health.testsuite.runner.SuiteExecutionEngine;

public class ScriptRunnerTest {

    private ScriptRunner runner;

    @BeforeEach
    void setUp() throws Exception {
        File tempPref = File.createTempFile("pref_test", ".yaml");
        ConfigManager configManager = new ConfigManager(tempPref);

        Preferences prefs = configManager.getPreferences();
        prefs.setMockMode(true); // Mock mode for offline unit testing
        configManager.savePreferences(prefs);

        DataTypeRegistry registry = new DataTypeRegistry(new File("config/datatypes.yaml"));
        OAuthService oAuthService = new OAuthService(configManager);
        HealthApiClient apiClient = new HealthApiClient(configManager, oAuthService);
        SuiteExecutionEngine engine = new SuiteExecutionEngine(configManager, registry, apiClient);

        runner = new ScriptRunner(engine);
    }

    @Test
    void testRunSampleSuiteScript() {
        File scriptFile = new File("scripts/sample_suite.yaml");
        assertTrue(scriptFile.exists(), "Sample suite script must exist");

        int exitCode = runner.runScript(scriptFile);
        assertEquals(0, exitCode, "Sample suite script should pass with exit code 0 in mock mode");
    }

    @Test
    void testRunSmokeTestScript() {
        File scriptFile = new File("scripts/smoke_test.yaml");
        assertTrue(scriptFile.exists(), "Smoke test script must exist");

        int exitCode = runner.runScript(scriptFile);
        assertEquals(0, exitCode, "Smoke test script should pass with exit code 0");
    }

    @Test
    void testRunStandardTestsScript() throws Exception {
        File scriptFile = new File("scripts/standard_tests-steps.yaml");
        assertTrue(scriptFile.exists(), "standard_tests-steps.yaml script must exist");

        int exitCode = runner.runScript(scriptFile);
        assertEquals(0, exitCode, "standard_tests-steps.yaml should pass with exit code 0 in mock mode");

        var results = runner.executeScript(scriptFile);
        assertFalse(results.isEmpty(), "Should have test steps");
        assertTrue(results.stream().allMatch(com.google.health.testsuite.model.TestResult::isPassed),
                "All standard tests should pass");
    }

    @Test
    void testRunStandardUserTestsScript() throws Exception {
        File scriptFile = new File("scripts/standard_tests_user.yaml");
        assertTrue(scriptFile.exists(), "standard_tests_user.yaml script must exist");

        int exitCode = runner.runScript(scriptFile);
        assertEquals(0, exitCode, "standard_tests_user.yaml should pass with exit code 0 in mock mode");

        var results = runner.executeScript(scriptFile);
        assertFalse(results.isEmpty(), "Should have test steps");
        assertTrue(results.stream().allMatch(com.google.health.testsuite.model.TestResult::isPassed),
                "All user standard tests should pass");
        assertTrue(results.size() >= 8, "Should have executed at least 8 test steps");
    }

    @Test
    void testRunStandardPairedDevicesScript() throws Exception {
        File scriptFile = new File("scripts/standard_tests_pairedDevices.yaml");
        assertTrue(scriptFile.exists(), "standard_tests_pairedDevices.yaml script must exist");

        int exitCode = runner.runScript(scriptFile);
        assertEquals(0, exitCode, "standard_tests_pairedDevices.yaml should pass with exit code 0 in mock mode");

        var results = runner.executeScript(scriptFile);
        assertTrue(results.size() >= 2);
        assertTrue(results.stream().allMatch(com.google.health.testsuite.model.TestResult::isPassed));
        assertEquals("Standard: User Paired Devices (me)", results.get(0).getTestName());
        assertEquals("Standard: User Paired Devices (healthUserId)", results.get(1).getTestName());
    }

    @Test
    void testRunStandardUpdateProfileScript() throws Exception {
        File scriptFile = new File("scripts/standard_tests_updateProfile.yaml");
        assertTrue(scriptFile.exists(), "standard_tests_updateProfile.yaml script must exist");

        int exitCode = runner.runScript(scriptFile);
        assertEquals(0, exitCode, "standard_tests_updateProfile.yaml should pass with exit code 0 in mock mode");

        var results = runner.executeScript(scriptFile);
        assertEquals(14, results.size(), "7 updateProfile steps with userId: both should expand to 14 steps");
        assertTrue(results.stream().allMatch(com.google.health.testsuite.model.TestResult::isPassed),
                "All updateProfile + getProfile verification tests should pass");
        assertTrue(results.get(0).getMessage().contains("verified via GET"));
    }

    @Test
    void testRunStandardUpdateSettingsScript() throws Exception {
        File scriptFile = new File("scripts/standard_tests_updateSettings.yaml");
        assertTrue(scriptFile.exists(), "standard_tests_updateSettings.yaml script must exist");

        int exitCode = runner.runScript(scriptFile);
        assertEquals(0, exitCode, "standard_tests_updateSettings.yaml should pass with exit code 0 in mock mode");

        var results = runner.executeScript(scriptFile);
        assertEquals(50, results.size(), "25 updateSettings steps with userId: both should expand to 50 steps");
        assertTrue(results.stream().allMatch(com.google.health.testsuite.model.TestResult::isPassed),
                "All updateSettings + getSettings verification tests should pass");
        assertTrue(results.get(0).getMessage().contains("verified via GET"));
    }

    @Test
    void testRunStandardUpdateBodyFatScript() throws Exception {
        File scriptFile = new File("scripts/standard_tests_updatebodyFat.yaml");
        assertTrue(scriptFile.exists(), "standard_tests_updatebodyFat.yaml script must exist");

        var results = runner.executeScript(scriptFile);
        assertEquals(30, results.size(), "15 body-fat lifecycle steps with userId: both should expand to 30 steps");
        assertEquals("Standard: Body Fat Create (me)", results.get(0).getTestName());
        assertTrue(results.get(0).isPassed(), "Step 0 (Create) should pass");
        assertEquals("Standard: Body Fat Get (Verify Create) (me)", results.get(1).getTestName());
        assertTrue(results.get(1).isPassed(), "Step 1 (Get Verify Create) should pass");
        assertEquals("Standard: Body Fat Update (Patch) (me)", results.get(2).getTestName());
        assertTrue(results.get(2).isPassed(), "Step 2 (Update Patch) should pass");
        assertEquals("Standard: Body Fat Get (Verify Update) (me)", results.get(3).getTestName());
        assertTrue(results.get(3).isPassed(), "Step 3 (Get Verify Update) should pass");
    }

    @Test
    void testRunStandardUpdateDataTypeScripts() throws Exception {
        String[] updateScripts = new String[]{
                "scripts/standard_tests_updateExercise.yaml",
                "scripts/standard_tests_updateHeight.yaml",
                "scripts/standard_tests_updateHydrationLog.yaml",
                "scripts/standard_tests_updateNutritionLog.yaml",
                "scripts/standard_tests_updateSleep.yaml",
                "scripts/standard_tests_updateWeight.yaml"
        };
        for (String path : updateScripts) {
            File scriptFile = new File(path);
            assertTrue(scriptFile.exists(), path + " script must exist");
            int exitCode = runner.runScript(scriptFile);
            assertEquals(0, exitCode, path + " should pass with exit code 0 in mock mode");
            var results = runner.executeScript(scriptFile);
            assertEquals(30, results.size(), "15 lifecycle steps in " + path + " with userId: both should expand to 30 steps");
            assertTrue(results.stream().allMatch(com.google.health.testsuite.model.TestResult::isPassed),
                    "All steps in " + path + " should pass");
        }

        String[] writeOnlyUpdateScripts = new String[]{
                "scripts/standard_tests_updateMenstrualPeriod.yaml",
                "scripts/standard_tests_updateMoods.yaml",
                "scripts/standard_tests_updateOvulationTest.yaml",
                "scripts/standard_tests_updateSymptoms.yaml"
        };
        for (String path : writeOnlyUpdateScripts) {
            File scriptFile = new File(path);
            assertTrue(scriptFile.exists(), path + " script must exist");
            int exitCode = runner.runScript(scriptFile);
            assertEquals(0, exitCode, path + " should pass with exit code 0 in mock mode");
            var results = runner.executeScript(scriptFile);
            assertEquals(18, results.size(), "9 lifecycle steps in " + path + " with userId: both should expand to 18 steps");
            assertTrue(results.stream().allMatch(com.google.health.testsuite.model.TestResult::isPassed),
                    "All steps in " + path + " should pass");
        }
    }

    @Test
    void testRunStandardExportExerciseTcxScript() throws Exception {
        File scriptFile = new File("scripts/standard_tests_exportExerciseTcx.yaml");
        assertTrue(scriptFile.exists(), "standard_tests_exportExerciseTcx.yaml script must exist");

        int exitCode = runner.runScript(scriptFile);
        assertEquals(0, exitCode, "standard_tests_exportExerciseTcx.yaml should pass with exit code 0 in mock mode");

        var results = runner.executeScript(scriptFile);
        assertEquals(4, results.size(), "2 steps with userId: both should expand to 4 steps");
        assertTrue(results.stream().allMatch(com.google.health.testsuite.model.TestResult::isPassed),
                "All exercise list + exportExerciseTcx steps should pass");
        assertEquals("Standard: Exercise List (Retrieve Unique Name) (me)", results.get(0).getTestName());
        assertEquals("Standard: Exercise Export TCX (me)", results.get(1).getTestName());
        assertEquals("Standard: Exercise List (Retrieve Unique Name) (healthUserId)", results.get(2).getTestName());
        assertEquals("Standard: Exercise Export TCX (healthUserId)", results.get(3).getTestName());
    }

    @Test
    void testRunStandardLatencyTestsScript() throws Exception {
        File scriptFile = new File("scripts/latency_tests.yaml");
        assertTrue(scriptFile.exists(), "latency_tests.yaml script must exist");

        int exitCode = runner.runScript(scriptFile);
        assertEquals(0, exitCode, "latency_tests.yaml should pass with exit code 0 in mock mode");

        var results = runner.executeScript(scriptFile);
        assertEquals(1884, results.size(), "942 standard test steps with userId: both should expand to 1884 steps");
        assertTrue(results.stream().allMatch(com.google.health.testsuite.model.TestResult::isPassed),
                "All latency tests should pass when latency <= 1000ms");
    }

    @Test
    void testGlobalAndEndpointSpecificLatencyCriteria() throws Exception {
        File tempScript = File.createTempFile("latency_criteria_test", ".yaml");
        tempScript.deleteOnExit();

        // Mock backend simulates ~35ms latency.
        // Global maxLatencyMs is 10ms (so default steps fail),
        // Step 2 overrides with maxLatencyMs: 1000 (passes),
        // Step 3 overrides with maxLatencyMs: 5 (fails).
        String yaml = """
            name: "Latency Override Test"
            userId: me
            stopOnError: false
            maxLatencyMs: 10
            steps:
              - name: "Step Inheriting Global 10ms (Should Fail)"
                action: "list"
                dataType: "active-energy-burned"
                assertStatus: [200]
              - name: "Step With Override 1000ms (Should Pass)"
                action: "list"
                dataType: "active-minutes"
                assertStatus: [200]
                maxLatencyMs: 1000
              - name: "Step With Override 5ms (Should Fail)"
                action: "list"
                dataType: "active-minutes"
                assertStatus: [200]
                maxLatencyMs: 5
            """;
        java.nio.file.Files.writeString(tempScript.toPath(), yaml);

        var results = runner.executeScript(tempScript);
        assertEquals(3, results.size());

        assertFalse(results.get(0).isPassed(), "Step inheriting 10ms global limit should fail when mock latency >= 35ms");
        assertTrue(results.get(0).getMessage().contains("exceeded max allowed 10ms"));

        assertTrue(results.get(1).isPassed(), "Step overriding with 1000ms limit should pass");
        assertTrue(results.get(1).getMessage().contains("<= 1000ms"));

        assertFalse(results.get(2).isPassed(), "Step overriding with 5ms limit should fail");
        assertTrue(results.get(2).getMessage().contains("exceeded max allowed 5ms"));
    }

    @Test
    void testScriptWithUserIdBothExpandsSteps() throws Exception {
        File tempScript = File.createTempFile("both_users_test", ".yaml");
        tempScript.deleteOnExit();

        String yaml = """
            name: "Both Users Test"
            userId: both
            stopOnError: false
            steps:
              - name: "User Profile Test"
                action: "getProfile"
                assertStatus: [200]
              - name: "User Settings Test"
                action: "getSettings"
                assertStatus: [200]
            """;
        java.nio.file.Files.writeString(tempScript.toPath(), yaml);

        var results = runner.executeScript(tempScript);
        assertEquals(4, results.size(), "2 steps with userId: both must expand to 4 executed steps");
        assertTrue(results.stream().allMatch(com.google.health.testsuite.model.TestResult::isPassed));

        assertEquals("User Profile Test (me)", results.get(0).getTestName());
        assertEquals("User Profile Test (healthUserId)", results.get(1).getTestName());
        assertEquals("User Settings Test (me)", results.get(2).getTestName());
        assertEquals("User Settings Test (healthUserId)", results.get(3).getTestName());
    }

    @Test
    void testScriptWithUserIdHealthUserId() throws Exception {
        File tempScript = File.createTempFile("health_user_test", ".yaml");
        tempScript.deleteOnExit();

        String yaml = """
            name: "Health User ID Test"
            userId: healthUserId
            stopOnError: false
            steps:
              - name: "User Identity Test"
                action: "getIdentity"
                assertStatus: [200]
            """;
        java.nio.file.Files.writeString(tempScript.toPath(), yaml);

        var results = runner.executeScript(tempScript);
        assertEquals(1, results.size());
        assertTrue(results.get(0).isPassed());
    }

    @Test
    void testRunScriptWithDetailsSuccess() {
        File scriptFile = new File("scripts/sample_suite.yaml");
        ScriptRunner.ScriptRunResult result = runner.runScriptWithDetails(scriptFile);

        assertEquals(0, result.getExitCode());
        assertTrue(result.isSuccess());
        assertEquals(12, result.getTotalSteps());
        assertEquals(12, result.getPassedCount());
        assertEquals(0, result.getFailedCount());
        assertTrue(result.getFailedTestNames().isEmpty());
        assertTrue(result.getFailedTests().isEmpty());
    }

    @Test
    void testRunScriptWithDetailsFailureReportsFailedTestNames() throws Exception {
        // Create a temporary script containing a step expected to fail status assertion
        File tempScript = File.createTempFile("failing_test_script", ".yaml");
        tempScript.deleteOnExit();

        String yaml = """
            name: "Failing Test Suite"
            description: "Script with an intentional failure"
            stopOnError: false
            steps:
              - name: "Passing Step 1"
                action: "get_identity"
                assertStatus: [200]
              - name: "Expected Failing Step"
                action: "get_identity"
                assertStatus: [999]
            """;
        java.nio.file.Files.writeString(tempScript.toPath(), yaml);

        ScriptRunner.ScriptRunResult result = runner.runScriptWithDetails(tempScript);

        assertEquals(1, result.getExitCode());
        assertFalse(result.isSuccess());
        assertEquals(2, result.getTotalSteps());
        assertEquals(1, result.getPassedCount());
        assertEquals(1, result.getFailedCount());
        assertEquals(1, result.getFailedTestNames().size());
        assertEquals("Expected Failing Step", result.getFailedTestNames().get(0));
        assertEquals(1, result.getFailedTests().size());
        assertEquals("Expected Failing Step", result.getFailedTests().get(0).getTestName());
        assertFalse(result.getFailedTests().get(0).isPassed());
    }

    @Test
    void testRunManifestExecution() throws Exception {
        File tempManifest = File.createTempFile("test_manifest_exec", ".yaml");
        tempManifest.deleteOnExit();
        String manifestYaml = """
            name: "Unit Test Manifest"
            description: "Standard positive scripts for manifest test"
            scripts:
              - "scripts/standard_tests-steps.yaml"
              - "scripts/standard_tests_updateWeight.yaml"
            """;
        java.nio.file.Files.writeString(tempManifest.toPath(), manifestYaml);

        var results = runner.executeManifest(tempManifest);
        assertNotNull(results);
        assertFalse(results.isEmpty(), "Should execute steps from manifest");
        var failed = results.stream().filter(r -> !r.isPassed()).toList();
        assertTrue(failed.isEmpty(), "All manifest test steps should pass in mock mode, but failed: " + failed.size());
        assertNotNull(results.get(0).getScriptFile(), "Script file name should be populated on test result");
    }

    @Test
    void testRunManifestWithDetails() throws Exception {
        File tempManifest = File.createTempFile("test_manifest_details", ".yaml");
        tempManifest.deleteOnExit();
        String manifestYaml = """
            name: "Unit Test Manifest Details"
            description: "Standard positive scripts for manifest details test"
            scripts:
              - "scripts/standard_tests-steps.yaml"
              - "scripts/standard_tests_updateWeight.yaml"
            """;
        java.nio.file.Files.writeString(tempManifest.toPath(), manifestYaml);

        ScriptRunner.ScriptRunResult runResult = runner.runManifestWithDetails(tempManifest);

        assertEquals(0, runResult.getExitCode());
        assertTrue(runResult.isSuccess());
        assertTrue(runResult.getTotalSteps() > 0);
        assertEquals(0, runResult.getFailedCount());
        assertFalse(runResult.getAllResults().isEmpty());
    }

    @Test
    void testVerifyWithGetOnCreateAndPatch() throws Exception {
        File tempScript = File.createTempFile("verify_with_get_test", ".yaml");
        tempScript.deleteOnExit();

        String yaml = """
            name: "Verify With Get On Create And Patch"
            stopOnError: false
            steps:
              - name: "Create Body Fat With VerifyWithGet"
                action: "create"
                verifyWithGet: true
                dataType: "body-fat"
                body: |
                  {
                    "bodyFat": {
                      "sampleTime": {
                        "physicalTime": "2026-10-15T12:00:00Z",
                        "utcOffset": "-14400s"
                      },
                      "percentage": 21.5
                    }
                  }
                assertStatus: [200, 201]
                validateRange: true
              - name: "Patch Body Fat With VerifyWithGet"
                action: "patch"
                verifyWithGet: true
                dataType: "body-fat"
                dataPointId: "${uniqueName}"
                body: |
                  {
                    "bodyFat": {
                      "sampleTime": {
                        "physicalTime": "2026-10-15T12:00:00Z",
                        "utcOffset": "-14400s"
                      },
                      "percentage": 22.8
                    }
                  }
                assertStatus: [200]
                validateRange: true
            """;
        java.nio.file.Files.writeString(tempScript.toPath(), yaml);

        var results = runner.executeScript(tempScript);
        assertEquals(2, results.size());
        assertTrue(results.get(0).isPassed(), "Create with verifyWithGet should pass: " + results.get(0).getMessage());
        assertTrue(results.get(0).getMessage().contains("verified via GET"));
        assertTrue(results.get(1).isPassed(), "Patch with verifyWithGet should pass: " + results.get(1).getMessage());
        assertTrue(results.get(1).getMessage().contains("verified via GET"));
    }
}
