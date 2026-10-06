package com.google.health.testsuite;

import com.google.health.testsuite.auth.OAuthService;
import com.google.health.testsuite.client.HealthApiClient;
import com.google.health.testsuite.config.ConfigManager;
import com.google.health.testsuite.config.DataTypeRegistry;
import com.google.health.testsuite.config.Preferences;
import com.google.health.testsuite.runner.ScriptRunner;
import com.google.health.testsuite.runner.SuiteExecutionEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

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
}
