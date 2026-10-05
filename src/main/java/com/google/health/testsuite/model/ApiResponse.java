package com.google.health.testsuite.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Standard structured response for all Google Health API HTTP calls.
 */
public class ApiResponse {

    private final int statusCode;
    private final String statusMessage;
    private final Map<String, List<String>> headers;
    private final String body;
    private final long latencyMs;
    private final String requestUrl;
    private final String requestMethod;
    private final String requestBody;
    private final String curlCommand;
    private final String errorMessage;

    public ApiResponse(int statusCode, String statusMessage, Map<String, List<String>> headers,
                       String body, long latencyMs, String requestUrl, String requestMethod,
                       String requestBody, String curlCommand) {
        this(statusCode, statusMessage, headers, body, latencyMs, requestUrl, requestMethod, requestBody, curlCommand, null);
    }

    public ApiResponse(int statusCode, String statusMessage, Map<String, List<String>> headers,
                       String body, long latencyMs, String requestUrl, String requestMethod,
                       String requestBody, String curlCommand, String errorMessage) {
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
        this.headers = headers != null ? headers : Collections.emptyMap();
        this.body = body != null ? body : "";
        this.latencyMs = latencyMs;
        this.requestUrl = requestUrl;
        this.requestMethod = requestMethod;
        this.requestBody = requestBody;
        this.curlCommand = curlCommand;
        this.errorMessage = (errorMessage != null && !errorMessage.isBlank())
                ? errorMessage
                : extractErrorMessage(this.body, this.statusCode);
    }

    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public String getBody() {
        return body;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public String getRequestUrl() {
        return requestUrl;
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public String getCurlCommand() {
        return curlCommand;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Extracts an informative error message from an endpoint response body or status.
     */
    public static String extractErrorMessage(String body, int statusCode) {
        if (statusCode >= 200 && statusCode < 300) {
            return null;
        }
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(body);

            // Google Cloud standard error format: { "error": { "code": 400, "message": "...", "status": "..." } }
            if (root.has("error")) {
                JsonNode errNode = root.get("error");
                if (errNode.isObject()) {
                    if (errNode.has("message") && !errNode.get("message").asText().isBlank()) {
                        return errNode.get("message").asText().trim();
                    }
                } else if (errNode.isTextual() && !errNode.asText().isBlank()) {
                    if (root.has("error_description") && !root.get("error_description").asText().isBlank()) {
                        return errNode.asText().trim() + ": " + root.get("error_description").asText().trim();
                    }
                    return errNode.asText().trim();
                }
            }

            // Top-level message or error_description
            if (root.has("message") && !root.get("message").asText().isBlank()) {
                return root.get("message").asText().trim();
            }
            if (root.has("error_description") && !root.get("error_description").asText().isBlank()) {
                return root.get("error_description").asText().trim();
            }
        } catch (Exception ignored) {
            // Non-JSON response
        }

        // Plain text fallback if reasonably short and not HTML/XML
        String trimmed = body.trim();
        if (trimmed.length() <= 300 && !trimmed.startsWith("<html") && !trimmed.startsWith("<!DOCTYPE") && !trimmed.startsWith("<?xml")) {
            return trimmed;
        }

        return null;
    }

    @Override
    public String toString() {
        return "ApiResponse{" +
                "statusCode=" + statusCode +
                ", latencyMs=" + latencyMs +
                ", requestMethod='" + requestMethod + '\'' +
                ", requestUrl='" + requestUrl + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                ", bodyLength=" + (body != null ? body.length() : 0) +
                '}';
    }
}
