package com.google.health.testsuite.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.health.testsuite.model.TestResult;
import com.google.health.testsuite.model.TestStep;
import com.google.health.testsuite.model.TestSuiteManifest;
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

        List<TestStep> steps = script.getExpandedSteps();
        int total = steps.size();
        logger.info("Executing test script: {} ({} steps)", script.getName(), total);
        List<TestResult> results = new ArrayList<>();

        for (int i = 0; i < total; i++) {
            TestStep step = steps.get(i);
            TestResult result = engine.executeStep(step);
            result.setScriptFile(scriptFile.getName());
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
     * Executes the given test suite manifest file and returns all TestResult items.
     */
    public List<TestResult> executeManifest(File manifestFile) throws IOException {
        return executeManifest(manifestFile, null);
    }

    /**
     * Executes the given test suite manifest file with a real-time listener for each step.
     */
    public List<TestResult> executeManifest(File manifestFile, TestStepListener listener) throws IOException {
        if (!manifestFile.exists()) {
            throw new IOException("Manifest file does not exist: " + manifestFile.getAbsolutePath());
        }
        TestSuiteManifest manifest;
        if (manifestFile.getName().endsWith(".json")) {
            manifest = jsonMapper.readValue(manifestFile, TestSuiteManifest.class);
        } else {
            manifest = yamlMapper.readValue(manifestFile, TestSuiteManifest.class);
        }
        return executeManifest(manifest, listener);
    }

    /**
     * Executes the given TestSuiteManifest model with a real-time listener across all scripts.
     */
    public List<TestResult> executeManifest(TestSuiteManifest manifest, TestStepListener listener) throws IOException {
        if (manifest == null || manifest.getScripts() == null || manifest.getScripts().isEmpty()) {
            logger.warn("Test suite manifest is empty, no scripts to execute.");
            return java.util.Collections.emptyList();
        }

        List<File> scriptFiles = new ArrayList<>();
        for (String path : manifest.getScripts()) {
            if (path == null || path.trim().isEmpty()) continue;
            File f = new File(path.trim());
            if (!f.exists()) {
                f = new File("scripts", path.trim());
            }
            if (f.exists()) {
                scriptFiles.add(f);
            } else {
                logger.warn("Script referenced in manifest not found: {}", path);
            }
        }

        if (scriptFiles.isEmpty()) {
            logger.warn("No valid script files found in manifest: {}", manifest.getName());
            return java.util.Collections.emptyList();
        }

        List<TestSuiteScript> loadedScripts = new ArrayList<>();
        List<File> validScriptFiles = new ArrayList<>();
        int totalStepsAcrossAll = 0;

        for (File f : scriptFiles) {
            try {
                TestSuiteScript s = f.getName().endsWith(".json")
                        ? jsonMapper.readValue(f, TestSuiteScript.class)
                        : yamlMapper.readValue(f, TestSuiteScript.class);
                loadedScripts.add(s);
                validScriptFiles.add(f);
                totalStepsAcrossAll += s.getExpandedSteps().size();
            } catch (Exception e) {
                logger.error("Failed to parse script file {}: {}", f.getName(), e.getMessage());
            }
        }

        logger.info("Executing manifest '{}' across {} scripts (total {} steps)",
                manifest.getName(), loadedScripts.size(), totalStepsAcrossAll);

        List<TestResult> allResults = new ArrayList<>();
        int globalIndex = 0;

        for (int sIdx = 0; sIdx < loadedScripts.size(); sIdx++) {
            TestSuiteScript script = loadedScripts.get(sIdx);
            File sFile = validScriptFiles.get(sIdx);
            List<TestStep> steps = script.getExpandedSteps();

            for (int i = 0; i < steps.size(); i++) {
                TestStep step = steps.get(i);
                globalIndex++;
                TestResult result = engine.executeStep(step);
                result.setScriptFile(sFile.getName());
                allResults.add(result);

                if (listener != null) {
                    try {
                        listener.onStepExecuted(globalIndex, totalStepsAcrossAll, step, result);
                    } catch (Exception e) {
                        logger.warn("Listener error on step {}: {}", globalIndex, e.getMessage());
                    }
                }

                if (!result.isPassed() && script.isStopOnError()) {
                    logger.warn("Stopping script {} early due to failure (stopOnError: true)", script.getName());
                    break;
                }
            }
        }

        return allResults;
    }

    /**
     * Execution summary for a script execution run.
     */
    public static class ScriptRunResult {
        private final int exitCode;
        private final boolean success;
        private final int totalSteps;
        private final int passedCount;
        private final int failedCount;
        private final long durationMs;
        private final List<TestResult> allResults;
        private final List<TestResult> failedTests;
        private final List<String> failedTestNames;
        private final String errorMessage;

        public ScriptRunResult(int exitCode, int totalSteps, int passedCount, int failedCount,
                               long durationMs, List<TestResult> allResults,
                               List<TestResult> failedTests, List<String> failedTestNames,
                               String errorMessage) {
            this.exitCode = exitCode;
            this.success = exitCode == 0;
            this.totalSteps = totalSteps;
            this.passedCount = passedCount;
            this.failedCount = failedCount;
            this.durationMs = durationMs;
            this.allResults = allResults != null ? allResults : java.util.Collections.emptyList();
            this.failedTests = failedTests != null ? failedTests : java.util.Collections.emptyList();
            this.failedTestNames = failedTestNames != null ? failedTestNames : java.util.Collections.emptyList();
            this.errorMessage = errorMessage;
        }

        public int getExitCode() {
            return exitCode;
        }

        public boolean isSuccess() {
            return success;
        }

        public int getTotalSteps() {
            return totalSteps;
        }

        public int getPassedCount() {
            return passedCount;
        }

        public int getFailedCount() {
            return failedCount;
        }

        public long getDurationMs() {
            return durationMs;
        }

        public List<TestResult> getAllResults() {
            return allResults;
        }

        public List<TestResult> getFailedTests() {
            return failedTests;
        }

        public List<String> getFailedTestNames() {
            return failedTestNames;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * Executes the given script file and returns exit code (0 = success, 1 = failure).
     */
    public int runScript(File scriptFile) {
        return runScriptWithDetails(scriptFile).getExitCode();
    }

    /**
     * Executes the given script file, prints progress and summary to stdout,
     * and returns a detailed ScriptRunResult containing failed test names and results.
     */
    public ScriptRunResult runScriptWithDetails(File scriptFile) {
        if (!scriptFile.exists()) {
            System.err.println("Error: Script file does not exist: " + scriptFile.getAbsolutePath());
            return new ScriptRunResult(1, 0, 0, 1, 0, java.util.Collections.emptyList(),
                    java.util.Collections.emptyList(), java.util.Collections.emptyList(),
                    "Script file does not exist: " + scriptFile.getAbsolutePath());
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
            return new ScriptRunResult(1, 0, 0, 1, 0, java.util.Collections.emptyList(),
                    java.util.Collections.emptyList(), java.util.Collections.emptyList(),
                    "Error parsing script file: " + e.getMessage());
        }

        System.out.println("================================================================================");
        System.out.println(" RUNNING TEST SCRIPT: " + script.getName());
        if (script.getDescription() != null && !script.getDescription().isEmpty()) {
            System.out.println(" Description: " + script.getDescription());
        }
        List<TestStep> steps = script.getExpandedSteps();
        System.out.println(" Script File: " + scriptFile.getAbsolutePath());
        System.out.println(" Steps:       " + steps.size());
        System.out.println("================================================================================");

        List<TestResult> results = new ArrayList<>();
        List<TestResult> failedTests = new ArrayList<>();
        List<String> failedTestNames = new ArrayList<>();
        long totalStartTime = System.currentTimeMillis();

        int passedCount = 0;
        int failedCount = 0;

        for (int i = 0; i < steps.size(); i++) {
            TestStep step = steps.get(i);
            System.out.printf("[%2d/%2d] Running: %-35s ... ", (i + 1), steps.size(), step.getName());

            TestResult result = engine.executeStep(step);
            result.setScriptFile(scriptFile.getName());
            results.add(result);

            if (result.isPassed()) {
                passedCount++;
                System.out.printf("\u001B[32mPASS\u001B[0m (HTTP %d, %dms)\n", result.getStatusCode(), result.getLatencyMs());
            } else {
                failedCount++;
                failedTests.add(result);
                failedTestNames.add(result.getTestName());
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
        if (failedCount > 0) {
            System.out.println("\n\u001B[31mFAILED TESTS (" + failedCount + "):\u001B[0m");
            for (TestResult r : failedTests) {
                System.out.printf("  - \u001B[31m%s\u001B[0m (HTTP %d): %s\n",
                        r.getTestName(), r.getStatusCode(), r.getMessage());
            }
        }
        System.out.println("================================================================================");

        int exitCode = failedCount == 0 ? 0 : 1;
        return new ScriptRunResult(exitCode, steps.size(), passedCount, failedCount,
                totalDuration, results, failedTests, failedTestNames, null);
    }

    /**
     * Executes the given test suite manifest, printing progress and summary to stdout.
     */
    public ScriptRunResult runManifestWithDetails(File manifestFile) {
        if (!manifestFile.exists()) {
            System.err.println("Error: Manifest file does not exist: " + manifestFile.getAbsolutePath());
            return new ScriptRunResult(1, 0, 0, 1, 0, java.util.Collections.emptyList(),
                    java.util.Collections.emptyList(), java.util.Collections.emptyList(),
                    "Manifest file does not exist: " + manifestFile.getAbsolutePath());
        }

        TestSuiteManifest manifest;
        try {
            if (manifestFile.getName().endsWith(".json")) {
                manifest = jsonMapper.readValue(manifestFile, TestSuiteManifest.class);
            } else {
                manifest = yamlMapper.readValue(manifestFile, TestSuiteManifest.class);
            }
        } catch (IOException e) {
            System.err.println("Error parsing manifest file " + manifestFile.getName() + ": " + e.getMessage());
            return new ScriptRunResult(1, 0, 0, 1, 0, java.util.Collections.emptyList(),
                    java.util.Collections.emptyList(), java.util.Collections.emptyList(),
                    "Error parsing manifest file: " + e.getMessage());
        }

        System.out.println("================================================================================");
        System.out.println(" RUNNING FULL TEST SUITE MANIFEST: " + manifest.getName());
        if (manifest.getDescription() != null && !manifest.getDescription().isEmpty()) {
            System.out.println(" Description: " + manifest.getDescription());
        }
        System.out.println(" Manifest:    " + manifestFile.getAbsolutePath());
        System.out.println(" Scripts:     " + manifest.getScripts().size());
        System.out.println("================================================================================");

        List<File> scriptFiles = new ArrayList<>();
        for (String path : manifest.getScripts()) {
            if (path == null || path.trim().isEmpty()) continue;
            File f = new File(path.trim());
            if (!f.exists()) {
                f = new File("scripts", path.trim());
            }
            if (f.exists()) {
                scriptFiles.add(f);
            } else {
                System.err.println("Warning: Script file not found: " + path);
            }
        }

        List<TestSuiteScript> loadedScripts = new ArrayList<>();
        List<File> validScriptFiles = new ArrayList<>();
        int totalSteps = 0;
        for (File f : scriptFiles) {
            try {
                TestSuiteScript s = f.getName().endsWith(".json")
                        ? jsonMapper.readValue(f, TestSuiteScript.class)
                        : yamlMapper.readValue(f, TestSuiteScript.class);
                loadedScripts.add(s);
                validScriptFiles.add(f);
                totalSteps += s.getExpandedSteps().size();
            } catch (Exception e) {
                System.err.println("Error parsing script " + f.getName() + ": " + e.getMessage());
            }
        }

        List<TestResult> allResults = new ArrayList<>();
        List<TestResult> failedTests = new ArrayList<>();
        List<String> failedTestNames = new ArrayList<>();
        long totalStartTime = System.currentTimeMillis();

        int passedCount = 0;
        int failedCount = 0;
        int globalIndex = 0;

        for (int sIdx = 0; sIdx < loadedScripts.size(); sIdx++) {
            TestSuiteScript script = loadedScripts.get(sIdx);
            File sFile = validScriptFiles.get(sIdx);
            List<TestStep> steps = script.getExpandedSteps();

            System.out.println("\n--- [" + (sIdx + 1) + "/" + loadedScripts.size() + "] Script: " + sFile.getName() + " (" + steps.size() + " steps) ---");

            for (int i = 0; i < steps.size(); i++) {
                TestStep step = steps.get(i);
                globalIndex++;
                System.out.printf("[%2d/%2d] Running: %-35s ... ", globalIndex, totalSteps, step.getName());

                TestResult result = engine.executeStep(step);
                result.setScriptFile(sFile.getName());
                allResults.add(result);

                if (result.isPassed()) {
                    passedCount++;
                    System.out.printf("\u001B[32mPASS\u001B[0m (HTTP %d, %dms)\n", result.getStatusCode(), result.getLatencyMs());
                } else {
                    failedCount++;
                    failedTests.add(result);
                    failedTestNames.add(result.getTestName());
                    System.out.printf("\u001B[31mFAIL\u001B[0m (HTTP %d, %dms) - %s\n", result.getStatusCode(), result.getLatencyMs(), result.getMessage());
                    if (result.getApiResponse() != null) {
                        System.out.println("       URL: " + result.getApiResponse().getRequestUrl());
                    }
                }

                if (!result.isPassed() && script.isStopOnError()) {
                    System.out.println("\n\u001B[33mStopping script early due to failure (stopOnError: true)\u001B[0m");
                    break;
                }
            }
        }

        long totalDuration = System.currentTimeMillis() - totalStartTime;

        System.out.println("================================================================================");
        System.out.printf(" MANIFEST EXECUTION SUMMARY: Total: %d | \u001B[32mPassed: %d\u001B[0m | \u001B[31mFailed: %d\u001B[0m | Time: %dms\n",
                allResults.size(), passedCount, failedCount, totalDuration);
        if (failedCount > 0) {
            System.out.println("\n\u001B[31mFAILED TESTS (" + failedCount + "):\u001B[0m");
            for (TestResult r : failedTests) {
                System.out.printf("  - \u001B[31m[%s] %s\u001B[0m (HTTP %d): %s\n",
                        r.getScriptFile() != null ? r.getScriptFile() : "unknown",
                        r.getTestName(), r.getStatusCode(), r.getMessage());
            }
        }
        System.out.println("================================================================================");

        int exitCode = failedCount == 0 ? 0 : 1;
        return new ScriptRunResult(exitCode, totalSteps, passedCount, failedCount,
                totalDuration, allResults, failedTests, failedTestNames, null);
    }
}
