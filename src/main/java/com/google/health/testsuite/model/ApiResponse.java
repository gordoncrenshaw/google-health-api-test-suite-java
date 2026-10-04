package com.google.health.testsuite.model;

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

    public ApiResponse(int statusCode, String statusMessage, Map<String, List<String>> headers,
                       String body, long latencyMs, String requestUrl, String requestMethod,
                       String requestBody, String curlCommand) {
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
        this.headers = headers != null ? headers : Collections.emptyMap();
        this.body = body != null ? body : "";
        this.latencyMs = latencyMs;
        this.requestUrl = requestUrl;
        this.requestMethod = requestMethod;
        this.requestBody = requestBody;
        this.curlCommand = curlCommand;
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

    @Override
    public String toString() {
        return "ApiResponse{" +
                "statusCode=" + statusCode +
                ", latencyMs=" + latencyMs +
                ", requestMethod='" + requestMethod + '\'' +
                ", requestUrl='" + requestUrl + '\'' +
                ", bodyLength=" + (body != null ? body.length() : 0) +
                '}';
    }
}
