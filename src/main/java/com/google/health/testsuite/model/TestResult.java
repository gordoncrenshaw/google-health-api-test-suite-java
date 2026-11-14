package com.google.health.testsuite.model;

import java.time.Instant;

/**
 * Result of a single test step or test case execution.
 */
public class TestResult {

    private final String testName;
    private final String dataType;
    private final String endpoint;
    private final boolean passed;
    private final int statusCode;
    private final long latencyMs;
    private final String message;
    private final ValidationResult validationResult;
    private final ApiResponse apiResponse;
    private final String timestamp;
    private String scriptFile;
    private String bugId;
    private String errorReportPath;
    private ApiResponse verificationResponse;
    private Long maxLatencyMs;

    public TestResult(String testName, String dataType, String endpoint, boolean passed,
                      int statusCode, long latencyMs, String message,
                      ValidationResult validationResult, ApiResponse apiResponse) {
        this.testName = testName;
        this.dataType = dataType;
        this.endpoint = endpoint;
        this.passed = passed;
        this.statusCode = statusCode;
        this.latencyMs = latencyMs;
        this.message = message;
        this.validationResult = validationResult;
        this.apiResponse = apiResponse;
        this.timestamp = Instant.now().toString();
    }

    public static TestResult success(String testName, String dataType, String endpoint,
                                     ApiResponse apiResponse, ValidationResult validationResult, String message) {
        return new TestResult(testName, dataType, endpoint, true,
                apiResponse != null ? apiResponse.getStatusCode() : 200,
                apiResponse != null ? apiResponse.getLatencyMs() : 0,
                message, validationResult, apiResponse);
    }

    public static TestResult failure(String testName, String dataType, String endpoint,
                                     ApiResponse apiResponse, ValidationResult validationResult, String message) {
        return new TestResult(testName, dataType, endpoint, false,
                apiResponse != null ? apiResponse.getStatusCode() : 500,
                apiResponse != null ? apiResponse.getLatencyMs() : 0,
                message, validationResult, apiResponse);
    }

    public String getTestName() {
        return testName;
    }

    public String getDataType() {
        return dataType;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public boolean isPassed() {
        return passed;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public String getMessage() {
        return message;
    }

    public ValidationResult getValidationResult() {
        return validationResult;
    }

    public ApiResponse getApiResponse() {
        return apiResponse;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getScriptFile() {
        return scriptFile;
    }

    public void setScriptFile(String scriptFile) {
        this.scriptFile = scriptFile;
    }

    public String getBugId() {
        return bugId;
    }

    public void setBugId(String bugId) {
        this.bugId = bugId;
    }

    public String getErrorReportPath() {
        return errorReportPath;
    }

    public void setErrorReportPath(String errorReportPath) {
        this.errorReportPath = errorReportPath;
    }

    public ApiResponse getVerificationResponse() {
        return verificationResponse;
    }

    public void setVerificationResponse(ApiResponse verificationResponse) {
        this.verificationResponse = verificationResponse;
    }

    public Long getMaxLatencyMs() {
        return maxLatencyMs;
    }

    public void setMaxLatencyMs(Long maxLatencyMs) {
        this.maxLatencyMs = maxLatencyMs;
    }

    @Override
    public String toString() {
        String bugPart = (bugId != null && !bugId.isBlank()) ? " [bugId: " + bugId + "]" : "";
        return "[" + (passed ? "PASS" : "FAIL") + "] " + testName + " (" + dataType + " " + endpoint + ")" + bugPart + " " +
                "HTTP " + statusCode + " in " + latencyMs + "ms - " + message;
    }
}
