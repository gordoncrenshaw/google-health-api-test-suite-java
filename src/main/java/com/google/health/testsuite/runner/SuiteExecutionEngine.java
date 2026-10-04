package com.google.health.testsuite.runner;

import com.google.health.testsuite.client.HealthApiClient;
import com.google.health.testsuite.client.ValidationEngine;
import com.google.health.testsuite.config.ConfigManager;
import com.google.health.testsuite.config.DataTypeRegistry;
import com.google.health.testsuite.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Core test execution engine shared across CLI Menu, Web UX, and Script Runner.
 */
public class SuiteExecutionEngine {

    private static final Logger logger = LoggerFactory.getLogger(SuiteExecutionEngine.class);

    private final ConfigManager configManager;
    private final DataTypeRegistry dataTypeRegistry;
    private final HealthApiClient apiClient;
    private final ValidationEngine validationEngine;

    public SuiteExecutionEngine(ConfigManager configManager, DataTypeRegistry dataTypeRegistry,
                                HealthApiClient apiClient) {
        this.configManager = configManager;
        this.dataTypeRegistry = dataTypeRegistry;
        this.apiClient = apiClient;
        this.validationEngine = new ValidationEngine();
    }

    /**
     * Executes a single test for a specific data type and operation.
     */
    public TestResult executeSingleTest(String dataTypeName, String endpoint,
                                        Map<String, String> queryParams, String requestBody) {
        Optional<DataTypeDefinition> defOpt = dataTypeRegistry.getDataType(dataTypeName);
        if (defOpt.isEmpty()) {
            return TestResult.failure("Execute " + endpoint, dataTypeName, endpoint, null, null,
                    "Unknown data type: " + dataTypeName);
        }
        DataTypeDefinition def = defOpt.get();

        String op = (endpoint != null) ? endpoint.toLowerCase().trim() : "list";
        ApiResponse response;

        try {
            switch (op) {
                case "list" -> response = apiClient.listDataPoints(def, queryParams);
                case "get" -> {
                    String dpId = (queryParams != null) ? queryParams.getOrDefault("dataPointId", "sample-dp-1") : "sample-dp-1";
                    response = apiClient.getDataPoint(def, dpId);
                }
                case "create" -> response = apiClient.createDataPoint(def, requestBody);
                case "rollup" -> response = apiClient.rollUpDataPoints(def, requestBody);
                case "dailyrollup" -> response = apiClient.dailyRollUpDataPoints(def, requestBody);
                case "batchdelete" -> {
                    List<String> names = (queryParams != null && queryParams.containsKey("names")) ?
                            Arrays.asList(queryParams.get("names").split(",")) : List.of("users/me/dataTypes/" + def.getName() + "/dataPoints/dp-1");
                    response = apiClient.batchDeleteDataPoints(def, names);
                }
                default -> {
                    return TestResult.failure("Execute " + op, def.getName(), op, null, null,
                            "Unsupported endpoint operation: " + op);
                }
            }

            ValidationResult valResult = validationEngine.validate(def, response.getBody());
            boolean passed = response.isSuccess() && valResult.isValid();
            String msg = (passed ? "Success" : "Failed") + ": HTTP " + response.getStatusCode() + " - " + valResult.getMessage();

            return new TestResult("Test " + def.getName() + " [" + op + "]", def.getName(), op,
                    passed, response.getStatusCode(), response.getLatencyMs(), msg, valResult, response);

        } catch (Exception e) {
            logger.error("Error executing test {} {}: {}", def.getName(), op, e.getMessage(), e);
            return TestResult.failure("Test " + def.getName() + " [" + op + "]", def.getName(), op,
                    null, null, "Execution error: " + e.getMessage());
        }
    }

    /**
     * Runs tests across all configured data types in datatypes.yaml that support the given operation.
     */
    public List<TestResult> executeAllDataTypesTest(String operation) {
        String op = (operation != null && !operation.isEmpty()) ? operation.toLowerCase().trim() : "list";
        List<DataTypeDefinition> allTypes = dataTypeRegistry.getAllDataTypes();
        List<TestResult> results = new ArrayList<>();

        for (DataTypeDefinition def : allTypes) {
            if (def.supportsEndpoint(op)) {
                logger.info("Executing {} test for {}", op, def.getName());
                TestResult res = executeSingleTest(def.getName(), op, null, null);
                results.add(res);
            } else {
                logger.debug("Skipping {} for {} (not supported)", op, def.getName());
            }
        }
        return results;
    }

    /**
     * Executes a single TestStep from a script file (Mode 3).
     */
    public TestResult executeStep(TestStep step) {
        String action = step.getAction().toLowerCase().trim();
        String dtName = step.getDataType();
        DataTypeDefinition def = (dtName != null) ? dataTypeRegistry.getDataType(dtName).orElse(null) : null;

        ApiResponse response;
        try {
            switch (action) {
                case "list", "list_datapoints" -> {
                    if (def == null) return errorResult(step, "Data type is required for action: " + action);
                    response = apiClient.listDataPoints(def, step.getQueryParams());
                }
                case "get", "get_datapoint" -> {
                    if (def == null) return errorResult(step, "Data type is required for action: " + action);
                    String dpId = step.getDataPointId() != null ? step.getDataPointId() : "default-dp";
                    response = apiClient.getDataPoint(def, dpId);
                }
                case "create", "create_datapoint" -> {
                    if (def == null) return errorResult(step, "Data type is required for action: " + action);
                    response = apiClient.createDataPoint(def, step.getBody());
                }
                case "rollup" -> {
                    if (def == null) return errorResult(step, "Data type is required for action: " + action);
                    response = apiClient.rollUpDataPoints(def, step.getBody());
                }
                case "dailyrollup" -> {
                    if (def == null) return errorResult(step, "Data type is required for action: " + action);
                    response = apiClient.dailyRollUpDataPoints(def, step.getBody());
                }
                case "profile", "get_profile" -> response = apiClient.getProfile(step.getUserId());
                case "devices", "list_devices" -> response = apiClient.listPairedDevices(step.getUserId());
                case "check_auth" -> {
                    boolean hasTokens = configManager.getUserAuthorization().hasAccessToken() ||
                            configManager.getPreferences().isMockMode();
                    if (hasTokens) {
                        return TestResult.success(step.getName(), "auth", "check", null,
                                ValidationResult.pass("Authorization token is present", null, null, null),
                                "Authorization verified.");
                    } else {
                        return TestResult.failure(step.getName(), "auth", "check", null,
                                ValidationResult.fail("No access token found in userAuthorization.yaml", null, null, null),
                                "Missing authorization credentials.");
                    }
                }
                default -> {
                    return errorResult(step, "Unknown action: " + action);
                }
            }

            // Evaluate Assertions
            boolean statusPassed = step.getAssertStatus().isEmpty() || step.getAssertStatus().contains(response.getStatusCode());
            ValidationResult valResult = ValidationResult.skipped("Range validation disabled");
            if (step.isValidateRange() && def != null) {
                valResult = validationEngine.validate(def, response.getBody());
            }

            boolean containsPassed = true;
            String missingText = null;
            if (step.getAssertContains() != null) {
                for (String expected : step.getAssertContains()) {
                    if (!response.getBody().contains(expected)) {
                        containsPassed = false;
                        missingText = expected;
                        break;
                    }
                }
            }

            boolean overallPassed = statusPassed && valResult.isValid() && containsPassed;
            StringBuilder msg = new StringBuilder();
            if (!statusPassed) {
                msg.append("Expected status ").append(step.getAssertStatus())
                        .append(" but got ").append(response.getStatusCode()).append(". ");
            }
            if (!valResult.isValid()) {
                msg.append(valResult.getMessage()).append(" ");
            }
            if (!containsPassed) {
                msg.append("Response missing expected substring: '").append(missingText).append("'. ");
            }
            if (overallPassed) {
                msg.append("Passed (HTTP ").append(response.getStatusCode()).append(")");
            }

            return new TestResult(step.getName(), dtName != null ? dtName : "api", action,
                    overallPassed, response.getStatusCode(), response.getLatencyMs(), msg.toString().trim(),
                    valResult, response);

        } catch (Exception e) {
            return errorResult(step, "Exception during step execution: " + e.getMessage());
        }
    }

    private TestResult errorResult(TestStep step, String message) {
        return TestResult.failure(step.getName(), step.getDataType() != null ? step.getDataType() : "unknown",
                step.getAction(), null, null, message);
    }

    public HealthApiClient getApiClient() {
        return apiClient;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DataTypeRegistry getDataTypeRegistry() {
        return dataTypeRegistry;
    }

    public ValidationEngine getValidationEngine() {
        return validationEngine;
    }
}
