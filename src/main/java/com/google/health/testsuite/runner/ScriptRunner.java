package com.google.health.testsuite.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.health.testsuite.model.TestResult;
import com.google.health.testsuite.model.TestStep;
import com.google.health.testsuite.model.TestSuiteScript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Mode 3: Test Suite Runner executing non-interactive script files (YAML or JSON).
 */
public class ScriptRunner {

    private static final Logger logger = LoggerFactory.getLogger(ScriptRunner.class);

    private final SuiteExecutionEngine engine;
    private final ObjectMapper yamlMapper;
    private final ObjectMapper jsonMapper;

    public ScriptRunner(SuiteExecutionEngine engine) {
        this.engine = engine;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.jsonMapper = new ObjectMapper();
    }

    @FunctionalInterface
    public interface TestStepListener {
        void onStepExecuted(int index, int total, TestStep step, TestResult result);
    }

    /**
     * Executes the given script file and returns the list of TestResult items.
     */
    public List<TestResult> executeScript(File scriptFile) throws IOException {
        return executeScript(scriptFile, null);
    }

    /**
     * Executes the given script file with a real-time listener for each step.
     */
    public List<TestResult> executeScript(File scriptFile, TestStepListener listener) throws IOException {
        if (!scriptFile.exists()) {
            throw new IOException("Script file does not exist: " + scriptFile.getAbsolutePath());
        }

        TestSuiteScript script;
        if (scriptFile.getName().endsWith(".json")) {
            script = jsonMapper.readValue(scriptFile, TestSuiteScript.class);
        } else {
            script = yamlMapper.readValue(scriptFile, TestSuiteScript.class);
        }

        int total = script.getSteps().size();
        logger.info("Executing test script: {} ({} steps)", script.getName(), total);
        List<TestResult> results = new ArrayList<>();

        for (int i = 0; i < total; i++) {
            TestStep step = script.getSteps().get(i);
            TestResult result = engine.executeStep(step);
            results.add(result);

            if (listener != null) {
                try {
                    listener.onStepExecuted(i + 1, total, step, result);
                } catch (Exception e) {
                    logger.warn("Listener error during step {}: {}", (i + 1), e.getMessage());
                }
            }

            if (!result.isPassed() && script.isStopOnError()) {
                logger.warn("Stopping script execution early due to failure (stopOnError: true)");
                break;
            }
        }
        return results;
    }

    /**
     * Executes the given script file and returns exit code (0 = success, 1 = failure).
     */
    public int runScript(File scriptFile) {
        if (!scriptFile.exists()) {
            System.err.println("Error: Script file does not exist: " + scriptFile.getAbsolutePath());
            return 1;
        }

        TestSuiteScript script;
        try {
            if (scriptFile.getName().endsWith(".json")) {
                script = jsonMapper.readValue(scriptFile, TestSuiteScript.class);
            } else {
                script = yamlMapper.readValue(scriptFile, TestSuiteScript.class);
            }
        } catch (IOException e) {
            System.err.println("Error parsing script file " + scriptFile.getName() + ": " + e.getMessage());
            return 1;
        }

        System.out.println("================================================================================");
        System.out.println(" RUNNING TEST SCRIPT: " + script.getName());
        if (script.getDescription() != null && !script.getDescription().isEmpty()) {
            System.out.println(" Description: " + script.getDescription());
        }
        System.out.println(" Script File: " + scriptFile.getAbsolutePath());
        System.out.println(" Steps:       " + script.getSteps().size());
        System.out.println("================================================================================");

        List<TestResult> results = new ArrayList<>();
        long totalStartTime = System.currentTimeMillis();

        int passedCount = 0;
        int failedCount = 0;

        for (int i = 0; i < script.getSteps().size(); i++) {
            TestStep step = script.getSteps().get(i);
            System.out.printf("[%2d/%2d] Running: %-35s ... ", (i + 1), script.getSteps().size(), step.getName());

            TestResult result = engine.executeStep(step);
            results.add(result);

            if (result.isPassed()) {
                passedCount++;
                System.out.printf("\u001B[32mPASS\u001B[0m (HTTP %d, %dms)\n", result.getStatusCode(), result.getLatencyMs());
            } else {
                failedCount++;
                System.out.printf("\u001B[31mFAIL\u001B[0m (HTTP %d, %dms) - %s\n", result.getStatusCode(), result.getLatencyMs(), result.getMessage());
                if (result.getApiResponse() != null) {
                    System.out.println("       URL: " + result.getApiResponse().getRequestUrl());
                }
            }

            if (!result.isPassed() && script.isStopOnError()) {
                System.out.println("\n\u001B[33mStopping execution early due to failure (stopOnError: true)\u001B[0m");
                break;
            }
        }

        long totalDuration = System.currentTimeMillis() - totalStartTime;

        System.out.println("================================================================================");
        System.out.printf(" EXECUTION SUMMARY: Total: %d | \u001B[32mPassed: %d\u001B[0m | \u001B[31mFailed: %d\u001B[0m | Time: %dms\n",
                results.size(), passedCount, failedCount, totalDuration);
        System.out.println("================================================================================");

        return failedCount == 0 ? 0 : 1;
    }
}
