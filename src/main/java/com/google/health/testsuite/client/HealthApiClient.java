package com.google.health.testsuite.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.health.testsuite.auth.OAuthService;
import com.google.health.testsuite.config.ConfigManager;
import com.google.health.testsuite.config.Preferences;
import com.google.health.testsuite.config.UserAuthorization;
import com.google.health.testsuite.model.ApiResponse;
import com.google.health.testsuite.model.DataTypeDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Standard HTTP client for calling Google Health API (v4) endpoints.
 * Provides reusable methods for all data types with automatic OAuth token
 * expiration detection and refresh token rotation.
 */
public class HealthApiClient {

    private static final Logger logger = LoggerFactory.getLogger(HealthApiClient.class);

    private final ConfigManager configManager;
    private final OAuthService oAuthService;
    private final MockHealthBackend mockBackend;
    private final HttpClient httpClient;
    private final ObjectMapper jsonMapper;

    public HealthApiClient(ConfigManager configManager, OAuthService oAuthService) {
        this.configManager = configManager;
        this.oAuthService = oAuthService;
        this.mockBackend = new MockHealthBackend();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        this.jsonMapper = new ObjectMapper();
    }

    /**
     * Universal reusable method to execute Google Health API HTTP requests.
     * Automatically injects Bearer token, handles auto-refresh on 401, and logs curl commands.
     */
    public ApiResponse execute(String method, String relativePath, Map<String, String> queryParams,
                               String body, DataTypeDefinition def) {
        Preferences prefs = configManager.getPreferences();
        UserAuthorization userAuth = configManager.getUserAuthorization();
        String effectiveUser = userAuth.getHealthUserID();

        // 1. Check if mock mode is active
        if (prefs.isMockMode()) {
            logger.debug("[Mock Mode] Intercepting request to {}", relativePath);
            return mockBackend.handleRequest(method, relativePath, queryParams, body, def, effectiveUser);
        }

        // 2. Pre-check token expiration: auto-refresh before request if expired
        if (userAuth.isExpired() && userAuth.hasRefreshToken()) {
            logger.info("Access token is expired or expiring soon. Refreshing before request...");
            boolean refreshed = oAuthService.refreshAccessToken();
            if (refreshed) {
                userAuth = configManager.getUserAuthorization();
            } else {
                logger.warn("Automatic pre-request token refresh failed. Proceeding with existing token.");
            }
        }

        // 3. Execute HTTP request
        ApiResponse response = sendHttpRequest(method, relativePath, queryParams, body, userAuth.getAccessToken());

        // 4. If 401 Unauthorized is returned, refresh token automatically and retry once
        if (response.getStatusCode() == 401 && userAuth.hasRefreshToken()) {
            logger.warn("Received HTTP 401 Unauthorized. Attempting automatic token refresh and retry...");
            boolean refreshed = oAuthService.refreshAccessToken();
            if (refreshed) {
                userAuth = configManager.getUserAuthorization();
                logger.info("Retrying request with newly refreshed access token...");
                response = sendHttpRequest(method, relativePath, queryParams, body, userAuth.getAccessToken());
            } else {
                logger.error("Automatic token refresh after 401 failed.");
            }
        }

        return response;
    }

    private ApiResponse sendHttpRequest(String method, String relativePath, Map<String, String> queryParams,
                                        String body, String accessToken) {
        Preferences prefs = configManager.getPreferences();
        String baseUrl = prefs.getApiBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (!relativePath.startsWith("/")) {
            relativePath = "/" + relativePath;
        }

        StringBuilder urlBuilder = new StringBuilder(baseUrl).append(relativePath);
        if (queryParams != null && !queryParams.isEmpty()) {
            urlBuilder.append("?");
            boolean first = true;
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                if (!first) urlBuilder.append("&");
                urlBuilder.append(urlEncode(entry.getKey()))
                        .append("=")
                        .append(urlEncode(entry.getValue()));
                first = false;
            }
        }

        String fullUrl = urlBuilder.toString();
        String curl = buildCurlCommand(method, fullUrl, body, accessToken);

        long startTime = System.currentTimeMillis();
        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(fullUrl))
                    .timeout(Duration.ofSeconds(20));

            if (accessToken != null && !accessToken.trim().isEmpty()) {
                reqBuilder.header("Authorization", "Bearer " + accessToken);
            }
            reqBuilder.header("Accept", "application/json");

            if ("POST".equalsIgnoreCase(method)) {
                reqBuilder.header("Content-Type", "application/json");
                reqBuilder.POST(HttpRequest.BodyPublishers.ofString(body != null ? body : "{}"));
            } else if ("PUT".equalsIgnoreCase(method)) {
                reqBuilder.header("Content-Type", "application/json");
                reqBuilder.PUT(HttpRequest.BodyPublishers.ofString(body != null ? body : "{}"));
            } else if ("PATCH".equalsIgnoreCase(method)) {
                reqBuilder.header("Content-Type", "application/json");
                reqBuilder.method("PATCH", HttpRequest.BodyPublishers.ofString(body != null ? body : "{}"));
            } else if ("DELETE".equalsIgnoreCase(method)) {
                reqBuilder.DELETE();
            } else {
                reqBuilder.GET();
            }

            HttpResponse<String> httpResponse = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - startTime;
            String prettyBody = formatPrettyJson(httpResponse.body());

            return new ApiResponse(httpResponse.statusCode(), "HTTP " + httpResponse.statusCode(),
                    httpResponse.headers().map(), prettyBody, latency, fullUrl, method, body, curl);

        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            logger.error("HTTP request error for {} {}: {}", method, fullUrl, e.getMessage());
            return new ApiResponse(500, "Connection Error: " + e.getMessage(), Collections.emptyMap(),
                    "{\n  \"error\": \"" + e.getMessage() + "\"\n}", latency, fullUrl, method, body, curl);
        }
    }

    // =========================================================================
    // Reusable Standard High-Level Methods for Data Types
    // =========================================================================

    /**
     * Standard List Data Points request.
     * Syntax: GET /v4/users/{userId}/dataTypes/{dataType}/dataPoints
     */
    public ApiResponse listDataPoints(DataTypeDefinition def, Map<String, String> queryParams) {
        String userId = configManager.getUserAuthorization().getHealthUserID();
        String path = "/v4/users/" + userId + "/dataTypes/" + def.getName() + "/dataPoints";
        return execute("GET", path, queryParams, null, def);
    }

    /**
     * Standard Get Single Data Point request.
     * Syntax: GET /v4/users/{userId}/dataTypes/{dataType}/dataPoints/{dataPointId}
     */
    public ApiResponse getDataPoint(DataTypeDefinition def, String dataPointId) {
        String userId = configManager.getUserAuthorization().getHealthUserID();
        String path = "/v4/users/" + userId + "/dataTypes/" + def.getName() + "/dataPoints/" + dataPointId;
        return execute("GET", path, null, null, def);
    }

    /**
     * Standard Create Data Point request.
     * Syntax: POST /v4/users/{userId}/dataTypes/{dataType}/dataPoints
     */
    public ApiResponse createDataPoint(DataTypeDefinition def, String jsonBody) {
        String userId = configManager.getUserAuthorization().getHealthUserID();
        String path = "/v4/users/" + userId + "/dataTypes/" + def.getName() + "/dataPoints";
        return execute("POST", path, null, jsonBody, def);
    }

    /**
     * Standard Batch Delete Data Points request.
     * Syntax: POST /v4/users/{userId}/dataTypes/{dataType}/dataPoints:batchDelete
     */
    public ApiResponse batchDeleteDataPoints(DataTypeDefinition def, List<String> dataPointNames) {
        String userId = configManager.getUserAuthorization().getHealthUserID();
        String path = "/v4/users/" + userId + "/dataTypes/" + def.getName() + "/dataPoints:batchDelete";

        ObjectNode reqNode = jsonMapper.createObjectNode();
        ArrayNode namesArray = reqNode.putArray("names");
        for (String name : dataPointNames) {
            namesArray.add(name);
        }

        return execute("POST", path, null, reqNode.toPrettyString(), def);
    }

    /**
     * Standard Physical Rollup request.
     * Syntax: POST /v4/users/{userId}/dataTypes/{dataType}/dataPoints:rollUp
     */
    public ApiResponse rollUpDataPoints(DataTypeDefinition def, String jsonBody) {
        String userId = configManager.getUserAuthorization().getHealthUserID();
        String path = "/v4/users/" + userId + "/dataTypes/" + def.getName() + "/dataPoints:rollUp";
        return execute("POST", path, null, jsonBody != null ? jsonBody : "{}", def);
    }

    /**
     * Standard Daily Civil Rollup request.
     * Syntax: POST /v4/users/{userId}/dataTypes/{dataType}/dataPoints:dailyRollUp
     */
    public ApiResponse dailyRollUpDataPoints(DataTypeDefinition def, String jsonBody) {
        String userId = configManager.getUserAuthorization().getHealthUserID();
        String path = "/v4/users/" + userId + "/dataTypes/" + def.getName() + "/dataPoints:dailyRollUp";
        return execute("POST", path, null, jsonBody != null ? jsonBody : "{}", def);
    }

    /**
     * Standard Get User Profile request.
     * Syntax: GET /v4/users/{userId}/profile
     */
    public ApiResponse getProfile(String userId) {
        String effectiveUser = (userId != null && !userId.isEmpty()) ? userId : configManager.getUserAuthorization().getHealthUserID();
        String path = "/v4/users/" + effectiveUser + "/profile";
        return execute("GET", path, null, null, null);
    }

    /**
     * Standard Get User Identity request.
     * Syntax: GET /v4/users/{userId}/identity
     * When called, if healthUserId in preferences is missing, it is automatically
     * extracted and stored into the preferences file (config/preferences.yaml).
     */
    public ApiResponse getIdentity(String userId) {
        Preferences prefs = configManager.getPreferences();
        String effectiveUser;
        if (userId != null && !userId.trim().isEmpty()) {
            effectiveUser = userId.trim();
        } else if (prefs.getHealthUserId() != null && !prefs.getHealthUserId().trim().isEmpty()) {
            effectiveUser = prefs.getHealthUserId().trim();
        } else if (configManager.getUserAuthorization().getHealthUserID() != null &&
                !configManager.getUserAuthorization().getHealthUserID().trim().isEmpty() &&
                !"me".equalsIgnoreCase(configManager.getUserAuthorization().getHealthUserID())) {
            effectiveUser = configManager.getUserAuthorization().getHealthUserID().trim();
        } else {
            effectiveUser = "me";
        }

        String path = "/v4/users/" + effectiveUser + "/identity";
        ApiResponse resp = execute("GET", path, null, null, null);

        // Check if getIdentity returned a healthUserId and if preferences is missing it
        if (resp.isSuccess() && resp.getBody() != null) {
            try {
                JsonNode root = jsonMapper.readTree(resp.getBody());
                String discoveredId = "";
                if (root.has("healthUserId") && !root.path("healthUserId").asText().trim().isEmpty()) {
                    discoveredId = root.path("healthUserId").asText().trim();
                } else if (root.has("response") && root.path("response").has("healthUserId")) {
                    discoveredId = root.path("response").path("healthUserId").asText().trim();
                }

                if (!discoveredId.isEmpty()) {
                    if (prefs.getHealthUserId() == null || prefs.getHealthUserId().trim().isEmpty()) {
                        logger.info("Retrieved healthUserId '{}' from getIdentity endpoint. Storing into preferences file...", discoveredId);
                        prefs.setHealthUserId(discoveredId);
                        configManager.savePreferences(prefs);
                    }
                    // Also update UserAuthorization if missing or set to default "me"
                    UserAuthorization auth = configManager.getUserAuthorization();
                    if (auth.getHealthUserID() == null || auth.getHealthUserID().trim().isEmpty() || "me".equalsIgnoreCase(auth.getHealthUserID())) {
                        auth.setHealthUserID(discoveredId);
                        configManager.saveUserAuthorization(auth);
                    }
                }
            } catch (Exception e) {
                logger.warn("Could not extract healthUserId from identity response: {}", e.getMessage());
            }
        }

        return resp;
    }

    /**
     * Standard List Paired Devices request.
     * Syntax: GET /v4/users/{userId}/pairedDevices
     */
    public ApiResponse listPairedDevices(String userId) {
        String effectiveUser = (userId != null && !userId.isEmpty()) ? userId : configManager.getUserAuthorization().getHealthUserID();
        String path = "/v4/users/" + effectiveUser + "/pairedDevices";
        return execute("GET", path, null, null, null);
    }

    /**
     * Standard Get Paired Devices request (alias for listPairedDevices).
     * Syntax: GET /v4/users/{userId}/pairedDevices
     */
    public ApiResponse getDevices(String userId) {
        return listPairedDevices(userId);
    }

    public String formatPrettyJson(String rawJson) {
        if (rawJson == null || rawJson.trim().isEmpty()) {
            return rawJson != null ? rawJson : "";
        }
        String trimmed = rawJson.trim();
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            try {
                Object parsed = jsonMapper.readValue(trimmed, Object.class);
                return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
            } catch (Exception ignore) {
                return rawJson;
            }
        }
        return rawJson;
    }

    /**
     * Standard List Subscriptions (Webhooks) request.
     * Syntax: GET /v4/projects/{projectId}/subscribers/{subscriberId}/subscriptions
     */
    public ApiResponse listSubscriptions(String projectId, String subscriberId) {
        String path = "/v4/projects/" + projectId + "/subscribers/" + subscriberId + "/subscriptions";
        return execute("GET", path, null, null, null);
    }

    private String buildCurlCommand(String method, String url, String body, String token) {
        StringBuilder sb = new StringBuilder("curl -X ").append(method).append(" \"").append(url).append("\"");
        if (token != null && !token.isEmpty()) {
            sb.append(" \\\n  -H \"Authorization: Bearer ").append(maskToken(token)).append("\"");
        }
        sb.append(" \\\n  -H \"Accept: application/json\"");
        if (body != null && !body.trim().isEmpty() && !("GET".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method))) {
            sb.append(" \\\n  -H \"Content-Type: application/json\"");
            sb.append(" \\\n  -d '").append(body.replace("'", "'\\''")).append("'");
        }
        return sb.toString();
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 12) {
            return "ya29.***";
        }
        return token.substring(0, 8) + "..." + token.substring(token.length() - 4);
    }

    private String urlEncode(String val) {
        if (val == null) return "";
        return URLEncoder.encode(val, StandardCharsets.UTF_8);
    }

    public MockHealthBackend getMockBackend() {
        return mockBackend;
    }
}
