package com.google.health.testsuite.runner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.health.testsuite.client.HealthApiClient;
import com.google.health.testsuite.client.ValidationEngine;
import com.google.health.testsuite.config.ConfigManager;
import com.google.health.testsuite.config.DataTypeRegistry;
import com.google.health.testsuite.model.ApiResponse;
import com.google.health.testsuite.model.DataTypeDefinition;
import com.google.health.testsuite.model.TestResult;
import com.google.health.testsuite.model.TestStep;
import com.google.health.testsuite.model.ValidationResult;

/**
 * Core test execution engine shared across CLI Menu, Web UX, and Script Runner.
 */
public class SuiteExecutionEngine {

    private static final Logger logger = LoggerFactory.getLogger(SuiteExecutionEngine.class);

    private final ConfigManager configManager;
    private final DataTypeRegistry dataTypeRegistry;
    private final HealthApiClient apiClient;
    private final ValidationEngine validationEngine;
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final Map<String, String> lastCreatedUniqueNameByKey = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, String> lastCreatedDataPointIdByKey = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, String> lastValidBodyByKey = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile String lastCreatedUniqueName = null;
    private volatile String lastCreatedDataPointId = null;

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
        boolean enableAll = configManager.getPreferences().isEnableAllEndpoints();
        Optional<DataTypeDefinition> defOpt = dataTypeRegistry.getDataType(dataTypeName, enableAll);
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
                    if (requestBody != null && !requestBody.isBlank()) {
                        response = apiClient.batchDeleteDataPoints(def, requestBody);
                    } else if (queryParams != null && queryParams.containsKey("names")) {
                        List<String> names = Arrays.asList(queryParams.get("names").split(","));
                        response = apiClient.batchDeleteDataPoints(def, names);
                    } else {
                        response = apiClient.batchDeleteDataPoints(def, "{\"names\":[]}");
                    }
                }
                case "reconcile" -> response = apiClient.reconcileDataPoints(def, queryParams);
                case "exportexercisetcx" -> {
                    String dpId = (queryParams != null) ? queryParams.getOrDefault("dataPointId", "sample-dp-1") : "sample-dp-1";
                    response = apiClient.exportExerciseTcx(def, dpId);
                }
                case "patch" -> {
                    String dpId = (queryParams != null) ? queryParams.getOrDefault("dataPointId", "sample-dp-1") : "sample-dp-1";
                    response = apiClient.patchDataPoint(def, dpId, requestBody);
                }
                default -> {
                    return TestResult.failure("Execute " + op, def.getName(), op, null, null,
                            "Unsupported endpoint operation: " + op);
                }
            }

            ValidationResult valResult = validationEngine.validate(def, response.getBody());
            boolean passed = response.isSuccess() && valResult.isValid();
            String msg;
            if (!response.isSuccess()) {
                String errorDetail = response.getErrorMessage();
                if (errorDetail == null || errorDetail.isBlank()) {
                    String statusMsg = response.getStatusMessage();
                    if (statusMsg != null && !statusMsg.isBlank() && !statusMsg.equals("HTTP " + response.getStatusCode())) {
                        errorDetail = statusMsg;
                    }
                }
                if (errorDetail != null && !errorDetail.isBlank()) {
                    if (errorDetail.startsWith("HTTP " + response.getStatusCode())) {
                        msg = "Failed: " + errorDetail;
                    } else {
                        msg = "Failed: HTTP " + response.getStatusCode() + " - " + errorDetail;
                    }
                } else {
                    msg = "Failed: HTTP " + response.getStatusCode();
                }
            } else if (!valResult.isValid()) {
                msg = "Failed range validation: " + valResult.getMessage();
            } else {
                msg = "Success: HTTP " + response.getStatusCode() + " (" + valResult.getMessage() + ")";
            }

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
        boolean enableAll = configManager.getPreferences().isEnableAllEndpoints();
        List<DataTypeDefinition> allTypes = dataTypeRegistry.getAllDataTypes(enableAll);
        List<TestResult> results = new ArrayList<>();

        for (DataTypeDefinition def : allTypes) {
            if (enableAll || def.supportsEndpoint(op)) {
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
        boolean enableAll = configManager.getPreferences().isEnableAllEndpoints();
        DataTypeDefinition def = (dtName != null) ? dataTypeRegistry.getDataType(dtName, enableAll).orElse(null) : null;
        if (def == null && dtName != null && !dtName.trim().isEmpty()) {
            def = new DataTypeDefinition();
            def.setName(dtName.trim());
            def.setDisplayName(dtName.trim());
            def.setScopeRequired("https://www.googleapis.com/auth/googlehealth.activity_and_fitness.readonly");
            def.setWriteScopeRequired("https://www.googleapis.com/auth/googlehealth.activity_and_fitness.writeonly");
            def = def.withAllEndpointsEnabled();
        }

        ApiResponse response;
        try {
            String resolvedDpId = resolvePlaceholders(step.getDataPointId(), step.getUserId(), dtName);
            String resolvedBody = resolvePlaceholders(step.getBody(), step.getUserId(), dtName);
            Map<String, String> resolvedQueryParams = resolveQueryParamPlaceholders(step.getQueryParams(), step.getUserId(), dtName);

            switch (action) {
                case "list", "list_datapoints" -> {
                    if (def == null) return errorResult(step, "Data type is required for action: " + action);
                    response = apiClient.listDataPoints(def, resolvedQueryParams, step.getUserId());
                    if (response.isSuccess()) {
                        captureCreatedResourceName(response.getBody(), step.getUserId(), def.getName());
                    }
                }
                case "get", "get_datapoint" -> {
                    if (def == null) return errorResult(step, "Data type is required for action: " + action);
                    String dpId = (resolvedDpId != null && !resolvedDpId.isBlank())
                            ? resolvedDpId
                            : getStoredUniqueName(step.getUserId(), dtName, "default-dp");
                    response = apiClient.getDataPoint(def, dpId, step.getUserId());
                }
                case "create", "create_datapoint" -> {
                    if (def == null) return errorResult(step, "Data type is required for action: " + action);
                    response = apiClient.createDataPoint(def, resolvedBody, step.getUserId());
                    if (response.isSuccess()) {
                        captureCreatedResourceName(response.getBody(), step.getUserId(), def.getName());
                    }
                }
                case "rollup", "roll_up" -> {
                    if (def == null) return errorResult(step, "Data type is required for action: " + action);
                    response = apiClient.rollUpDataPoints(def, resolvedBody, step.getUserId());
                }
                case "dailyrollup", "daily_rollup" -> {
                    if (def == null) return errorResult(step, "Data type is required for action: " + action);
                    response = apiClient.dailyRollUpDataPoints(def, resolvedBody, step.getUserId());
                }
                case "reconcile" -> {
                    if (def == null) return errorResult(step, "Data type is required for action: " + action);
                    response = apiClient.reconcileDataPoints(def, resolvedQueryParams, step.getUserId());
                }
                case "exportexercisetcx", "export_exercise_tcx" -> {
                    if (def == null) return errorResult(step, "Data type is required for action: " + action);
                    String dpId = (resolvedDpId != null && !resolvedDpId.isBlank())
                            ? resolvedDpId
                            : getStoredUniqueName(step.getUserId(), dtName, "sample-dp-1");
                    response = apiClient.exportExerciseTcx(def, dpId, resolvedQueryParams, step.getUserId());
                }
                case "patch", "patch_datapoint", "update", "update_datapoint" -> {
                    if (def == null) return errorResult(step, "Data type is required for action: " + action);
                    String dpId = (resolvedDpId != null && !resolvedDpId.isBlank())
                            ? resolvedDpId
                            : getStoredUniqueName(step.getUserId(), dtName, "sample-dp-1");
                    response = apiClient.patchDataPoint(def, dpId, resolvedBody, step.getUserId());
                    if (response.isSuccess()) {
                        captureCreatedResourceName(response.getBody(), step.getUserId(), def.getName());
                    }
                }
                case "batchdelete", "batch_delete", "batchdelete_datapoints", "delete", "delete_datapoint" -> {
                    if (def == null) return errorResult(step, "Data type is required for action: " + action);
                    if (resolvedBody != null && !resolvedBody.isBlank()) {
                        response = apiClient.batchDeleteDataPoints(def, resolvedBody, step.getUserId());
                    } else if (resolvedQueryParams != null && resolvedQueryParams.containsKey("names")) {
                        List<String> names = Arrays.asList(resolvedQueryParams.get("names").split(","));
                        response = apiClient.batchDeleteDataPoints(def, names, step.getUserId());
                    } else {
                        String fallbackName = getStoredUniqueName(step.getUserId(), dtName, "string");
                        response = apiClient.batchDeleteDataPoints(def, List.of(fallbackName), step.getUserId());
                    }
                }
                case "profile", "get_profile", "getprofile" -> {
                    response = apiClient.getProfile(step.getUserId());
                }
                case "update_profile", "patch_profile", "updateprofile", "patchprofile" -> {
                    response = apiClient.updateProfile(step.getUserId(), resolvedQueryParams, resolvedBody);
                }
                case "irn_profile", "get_irn_profile", "irnprofile", "getirnprofile" -> {
                    response = apiClient.getIrnProfile(step.getUserId());
                }
                case "settings", "get_settings", "getsettings" -> {
                    response = apiClient.getSettings(step.getUserId());
                }
                case "update_settings", "patch_settings", "updatesettings", "patchsettings" -> {
                    response = apiClient.updateSettings(step.getUserId(), resolvedQueryParams, resolvedBody);
                }
                case "devices", "list_devices", "listdevices", "get_devices", "getdevices" -> {
                    response = apiClient.listPairedDevices(step.getUserId());
                }
                case "identity", "get_identity", "getidentity" -> {
                    response = apiClient.getIdentity(step.getUserId());
                }
                case "check_auth" -> {
                    boolean hasTokens = configManager.getUserAuthorization().hasAccessToken() ||
                            configManager.getPreferences().isMockMode();
                    if (hasTokens) {
                        return TestResult.success(step.getName(), "auth", "check", null,
                                ValidationResult.pass("Authorization token is present", null, null, null),
                                "Authorization verified.");
                    } else {
                        return TestResult.failure(step.getName(), "auth", "check", null,
                                ValidationResult.fail("No access token found in Credential store", null, null, null),
                                "Missing authorization credentials.");
                    }
                }
                default -> {
                    return errorResult(step, "Unknown action: " + action);
                }
            }

            // Evaluate Assertions
            boolean statusPassed = step.getAssertStatus().isEmpty()
                    || step.getAssertStatus().contains(response.getStatusCode())
                    || (action.startsWith("create") && response.getStatusCode() == 201 && step.getAssertStatus().contains(200))
                    || (action.startsWith("create") && response.getStatusCode() == 200 && step.getAssertStatus().contains(201));
            ValidationResult valResult = ValidationResult.skipped("Range validation disabled");
            if (step.isValidateRange() && def != null && response.isSuccess()) {
                valResult = validationEngine.validate(def, response.getBody());
            }

            boolean verifyGetPassed = true;
            String verifyGetError = null;
            String resourceKey = buildStorageKey(step.getUserId(),
                    def != null ? def.getName() : (action.contains("profile") ? "profile" : (action.contains("settings") ? "settings" : dtName)));
            if (response.isSuccess() && resolvedBody != null && !resolvedBody.isBlank()
                    && (action.startsWith("create") || action.startsWith("patch") || action.startsWith("update"))) {
                lastValidBodyByKey.put(resourceKey, resolvedBody);
            }
            if (action.startsWith("batchdelete") || action.startsWith("batch_delete") || action.startsWith("delete")) {
                if (response.isSuccess()) {
                    lastValidBodyByKey.remove(resourceKey);
                }
            }

            if (step.isVerifyWithGet() && statusPassed) {
                ApiResponse getResponse = null;
                String getEndpointName = "GET";
                String expectedJsonForGet = response.isSuccess() ? resolvedBody : lastValidBodyByKey.get(resourceKey);

                if (action.contains("profile")) {
                    getResponse = apiClient.getProfile(step.getUserId());
                    getEndpointName = "getProfile";
                } else if (action.contains("settings")) {
                    getResponse = apiClient.getSettings(step.getUserId());
                    getEndpointName = "getSettings";
                } else if (def != null && (enableAll || def.supportsEndpoint("get"))
                        && (action.startsWith("create") || action.startsWith("patch") || action.startsWith("update"))) {
                    if (response.isSuccess() || action.startsWith("patch") || action.startsWith("update")) {
                        String verifyDpId = (resolvedDpId != null && !resolvedDpId.isBlank())
                                ? resolvedDpId
                                : getStoredUniqueName(step.getUserId(), dtName, null);
                        if (verifyDpId == null || verifyDpId.isBlank()) {
                            if (response.isSuccess()) {
                                verifyGetPassed = false;
                                verifyGetError = "getDataPoint verification failed: no resource name or dataPointId available after " + action;
                            }
                        } else {
                            getResponse = apiClient.getDataPoint(def, verifyDpId, step.getUserId());
                            getEndpointName = "get (" + def.getName() + ")";
                        }
                    }
                }

                if (getResponse != null) {
                    if (!getResponse.isSuccess()) {
                        verifyGetPassed = false;
                        verifyGetError = getEndpointName + " verification returned HTTP " + getResponse.getStatusCode()
                                + (getResponse.getErrorMessage() != null ? " - " + getResponse.getErrorMessage() : "");
                    } else if (expectedJsonForGet != null && !expectedJsonForGet.isBlank()) {
                        try {
                            JsonNode expectedNode = jsonMapper.readTree(expectedJsonForGet);
                            JsonNode actualNode = jsonMapper.readTree(getResponse.getBody());
                            String mismatchError = verifyJsonSubsetMatch(getEndpointName, "", expectedNode, actualNode);
                            if (mismatchError != null) {
                                verifyGetPassed = false;
                                verifyGetError = mismatchError;
                            }
                        } catch (Exception jsonEx) {
                            verifyGetPassed = false;
                            verifyGetError = getEndpointName + " verification failed to parse JSON: " + jsonEx.getMessage();
                        }
                    }
                }
            }

            boolean containsPassed = true;
            String missingText = null;
            if (step.getAssertContains() != null) {
                for (String expected : step.getAssertContains()) {
                    String needle = expected != null ? expected : "null";
                    if (response.getBody() == null || !response.getBody().contains(needle)) {
                        containsPassed = false;
                        missingText = needle;
                        break;
                    }
                }
            }

            boolean latencyPassed = true;
            Long maxLatencyMs = step.getMaxLatencyMs();
            if (maxLatencyMs != null && maxLatencyMs > 0) {
                if (response.getLatencyMs() > maxLatencyMs) {
                    latencyPassed = false;
                }
            }

            boolean overallPassed = statusPassed && valResult.isValid() && containsPassed && latencyPassed && verifyGetPassed;
            StringBuilder msg = new StringBuilder();
            if (!statusPassed) {
                msg.append("Expected status ").append(step.getAssertStatus())
                        .append(" but got ").append(response.getStatusCode());
                String errorDetail = response.getErrorMessage();
                if (errorDetail != null && !errorDetail.isBlank()) {
                    msg.append(" - ").append(errorDetail);
                }
                msg.append(". ");
            } else if (!response.isSuccess() && !overallPassed) {
                String errorDetail = response.getErrorMessage();
                if (errorDetail != null && !errorDetail.isBlank()) {
                    msg.append("HTTP ").append(response.getStatusCode()).append(" - ").append(errorDetail).append(". ");
                }
            }
            if (!valResult.isValid()) {
                msg.append(valResult.getMessage()).append(" ");
            }
            if (!containsPassed) {
                msg.append("Response missing expected substring: '").append(missingText).append("'. ");
            }
            if (!verifyGetPassed && verifyGetError != null) {
                msg.append(verifyGetError).append(". ");
            }
            if (!latencyPassed) {
                msg.append("Latency ").append(response.getLatencyMs())
                        .append("ms exceeded max allowed ").append(maxLatencyMs).append("ms. ");
            }
            if (overallPassed) {
                msg.append("Passed (HTTP ").append(response.getStatusCode());
                if (step.isVerifyWithGet()) {
                    msg.append(", verified via GET");
                }
                if (maxLatencyMs != null && maxLatencyMs > 0) {
                    msg.append(", latency ").append(response.getLatencyMs())
                            .append("ms <= ").append(maxLatencyMs).append("ms");
                }
                msg.append(")");
            }

            return new TestResult(step.getName(), dtName != null ? dtName : "api", action,
                    overallPassed, response.getStatusCode(), response.getLatencyMs(), msg.toString().trim(),
                    valResult, response);

        } catch (Exception e) {
            return errorResult(step, "Exception during step execution: " + e.getMessage());
        }
    }

    private void captureCreatedResourceName(String responseBody, String userId, String dataType) {
        if (responseBody == null || responseBody.isBlank()) {
            return;
        }
        try {
            JsonNode root = jsonMapper.readTree(responseBody);
            String extractedName = null;
            if (root.has("response") && root.get("response").isObject() && root.get("response").has("name")) {
                extractedName = root.get("response").get("name").asText(null);
            }
            if ((extractedName == null || extractedName.isBlank()) && root.has("name")) {
                String candidate = root.get("name").asText(null);
                if (candidate != null && !candidate.startsWith("operations/")) {
                    extractedName = candidate;
                }
            }
            if ((extractedName == null || extractedName.isBlank())
                    && root.has("dataPoints")
                    && root.get("dataPoints").isArray()
                    && !root.get("dataPoints").isEmpty()) {
                JsonNode firstDp = root.get("dataPoints").get(0);
                if (firstDp != null && firstDp.isObject() && firstDp.has("name")) {
                    extractedName = firstDp.get("name").asText(null);
                }
            }
            if (extractedName != null && !extractedName.isBlank()) {
                String dpId = extractedName;
                int idx = extractedName.lastIndexOf("/dataPoints/");
                if (idx >= 0) {
                    dpId = extractedName.substring(idx + "/dataPoints/".length());
                }
                String key = buildStorageKey(userId, dataType);
                lastCreatedUniqueName = extractedName;
                lastCreatedDataPointId = dpId;
                lastCreatedUniqueNameByKey.put(key, extractedName);
                lastCreatedDataPointIdByKey.put(key, dpId);
            }
        } catch (Exception e) {
            logger.debug("Could not extract created resource name: {}", e.getMessage());
        }
    }

    private String buildStorageKey(String userId, String dataType) {
        String u = (userId != null && !userId.trim().isEmpty()) ? userId.trim().toLowerCase() : "default";
        String dt = (dataType != null && !dataType.trim().isEmpty()) ? dataType.trim().toLowerCase() : "default";
        return u + "::" + dt;
    }

    private String getStoredUniqueName(String userId, String dataType, String fallback) {
        String key = buildStorageKey(userId, dataType);
        String val = lastCreatedUniqueNameByKey.get(key);
        if (val != null && !val.isBlank()) {
            return val;
        }
        if (lastCreatedUniqueName != null && !lastCreatedUniqueName.isBlank()) {
            return lastCreatedUniqueName;
        }
        return fallback;
    }

    private String getStoredDataPointId(String userId, String dataType, String fallback) {
        String key = buildStorageKey(userId, dataType);
        String val = lastCreatedDataPointIdByKey.get(key);
        if (val != null && !val.isBlank()) {
            return val;
        }
        if (lastCreatedDataPointId != null && !lastCreatedDataPointId.isBlank()) {
            return lastCreatedDataPointId;
        }
        return fallback;
    }

    private String resolvePlaceholders(String input, String userId, String dataType) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String uniqueName = getStoredUniqueName(userId, dataType, null);
        String dpId = getStoredDataPointId(userId, dataType, null);
        String result = input;
        if (uniqueName != null) {
            result = result.replace("${uniqueName}", uniqueName)
                    .replace("{{uniqueName}}", uniqueName)
                    .replace("$uniqueName", uniqueName)
                    .replace("${name}", uniqueName)
                    .replace("{{name}}", uniqueName);
        }
        if (dpId != null) {
            result = result.replace("${dataPointId}", dpId)
                    .replace("{{dataPointId}}", dpId)
                    .replace("$dataPointId", dpId);
        }
        return result;
    }

    private Map<String, String> resolveQueryParamPlaceholders(Map<String, String> params, String userId, String dataType) {
        if (params == null || params.isEmpty()) {
            return params;
        }
        Map<String, String> resolved = new java.util.HashMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            resolved.put(entry.getKey(), resolvePlaceholders(entry.getValue(), userId, dataType));
        }
        return resolved;
    }

    private String verifyJsonSubsetMatch(String endpointName, String pathPrefix, JsonNode expectedNode, JsonNode actualNode) {
        if (expectedNode == null || expectedNode.isNull()) {
            return null;
        }
        if (actualNode == null || actualNode.isNull()) {
            return endpointName + " verification failed for field '" + (pathPrefix.isEmpty() ? "root" : pathPrefix)
                    + "': expected " + expectedNode + " but got null";
        }
        if (expectedNode.isObject()) {
            if (!actualNode.isObject()) {
                return endpointName + " verification failed for field '" + (pathPrefix.isEmpty() ? "root" : pathPrefix)
                        + "': expected object " + expectedNode + " but got " + actualNode;
            }
            var fields = expectedNode.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                String fieldName = entry.getKey();
                if ("name".equals(fieldName)) continue;
                String fullPath = pathPrefix.isEmpty() ? fieldName : pathPrefix + "." + fieldName;
                JsonNode expectedVal = entry.getValue();
                JsonNode actualVal = actualNode.get(fieldName);
                if (actualVal == null) {
                    return endpointName + " verification failed for field '" + fullPath
                            + "': expected " + expectedVal + " but got null";
                }
                if (expectedVal.isObject()) {
                    String nestedErr = verifyJsonSubsetMatch(endpointName, fullPath, expectedVal, actualVal);
                    if (nestedErr != null) {
                        return nestedErr;
                    }
                } else if (expectedVal.isArray()) {
                    if (!actualVal.isArray() || actualVal.size() < expectedVal.size()) {
                        return endpointName + " verification failed for field '" + fullPath
                                + "': expected " + expectedVal + " but got " + actualVal;
                    }
                    for (int i = 0; i < expectedVal.size(); i++) {
                        String elemErr = verifyJsonSubsetMatch(endpointName, fullPath + "[" + i + "]", expectedVal.get(i), actualVal.get(i));
                        if (elemErr != null) {
                            return elemErr;
                        }
                    }
                } else if (!valuesMatch(expectedVal, actualVal)) {
                    return endpointName + " verification failed for field '" + fullPath
                            + "': expected " + expectedVal + " but got " + actualVal;
                }
            }
            return null;
        } else if (!valuesMatch(expectedNode, actualNode)) {
            return endpointName + " verification failed for field '" + (pathPrefix.isEmpty() ? "root" : pathPrefix)
                    + "': expected " + expectedNode + " but got " + actualNode;
        }
        return null;
    }

    private boolean valuesMatch(JsonNode expectedVal, JsonNode actualVal) {
        if (expectedVal == null || actualVal == null) {
            return expectedVal == actualVal;
        }
        if (expectedVal.equals(actualVal)) {
            return true;
        }
        if (expectedVal.isNumber() && actualVal.isNumber()) {
            return Double.compare(expectedVal.asDouble(), actualVal.asDouble()) == 0;
        }
        if (expectedVal.isValueNode() && actualVal.isValueNode()) {
            if (expectedVal.asText().equals(actualVal.asText())) {
                return true;
            }
            try {
                double d1 = Double.parseDouble(expectedVal.asText());
                double d2 = Double.parseDouble(actualVal.asText());
                return Double.compare(d1, d2) == 0;
            } catch (NumberFormatException ignored) {
            }
        }
        return false;
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
