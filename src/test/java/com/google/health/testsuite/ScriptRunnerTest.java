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
        assertEquals(8, results.size(), "Should have executed 8 test steps for userId: both (4 for me, 4 for healthUserId)");
    }

    @Test
    void testRunStandardPairedDevicesScript() throws Exception {
        File scriptFile = new File("scripts/standard_tests_pairedDevices.yaml");
        assertTrue(scriptFile.exists(), "standard_tests_pairedDevices.yaml script must exist");

        int exitCode = runner.runScript(scriptFile);
        assertEquals(0, exitCode, "standard_tests_pairedDevices.yaml should pass with exit code 0 in mock mode");

        var results = runner.executeScript(scriptFile);
        assertEquals(2, results.size());
        assertTrue(results.get(0).isPassed());
        assertTrue(results.get(1).isPassed());
        assertEquals("Standard: User Paired Devices (me)", results.get(0).getTestName());
        assertEquals("Standard: User Paired Devices (healthUserId)", results.get(1).getTestName());
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
        File manifestFile = new File("config/test_suite_manifest.yaml");
        assertTrue(manifestFile.exists(), "Manifest file should exist");

        var results = runner.executeManifest(manifestFile);
        assertNotNull(results);
        assertFalse(results.isEmpty(), "Should execute steps from manifest");
        var failed = results.stream().filter(r -> !r.isPassed()).toList();
        for (var f : failed) {
            System.err.println("FAILED STEP: [" + f.getScriptFile() + "] " + f.getTestName() + " -> " + f.getMessage());
        }
        assertTrue(failed.isEmpty(), "All manifest test steps should pass in mock mode, but failed: " + failed.size());
        assertNotNull(results.get(0).getScriptFile(), "Script file name should be populated on test result");
    }

    @Test
    void testRunManifestWithDetails() {
        File manifestFile = new File("config/test_suite_manifest.yaml");
        ScriptRunner.ScriptRunResult runResult = runner.runManifestWithDetails(manifestFile);

        assertEquals(0, runResult.getExitCode());
        assertTrue(runResult.isSuccess());
        assertTrue(runResult.getTotalSteps() > 0);
        assertEquals(0, runResult.getFailedCount());
        assertFalse(runResult.getAllResults().isEmpty());
    }
}
