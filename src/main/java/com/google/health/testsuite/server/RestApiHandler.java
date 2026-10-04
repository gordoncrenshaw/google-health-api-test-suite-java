package com.google.health.testsuite.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.health.testsuite.auth.BrowserUtil;
import com.google.health.testsuite.auth.LocalOAuthReceiver;
import com.google.health.testsuite.auth.OAuthService;
import com.google.health.testsuite.config.ConfigManager;
import com.google.health.testsuite.config.DataTypeRegistry;
import com.google.health.testsuite.config.Preferences;
import com.google.health.testsuite.config.UserAuthorization;
import com.google.health.testsuite.model.*;
import com.google.health.testsuite.runner.ScriptRunner;
import com.google.health.testsuite.runner.SuiteExecutionEngine;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Handles all REST API requests from the JavaScript/HTML frontend (Mode 2).
 */
public class RestApiHandler implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(RestApiHandler.class);

    private final SuiteExecutionEngine engine;
    private final ConfigManager configManager;
    private final DataTypeRegistry dataTypeRegistry;
    private final OAuthService oAuthService;
    private final ObjectMapper jsonMapper;
    private LocalOAuthReceiver activeReceiver;

    public RestApiHandler(SuiteExecutionEngine engine, OAuthService oAuthService) {
        this.engine = engine;
        this.configManager = engine.getConfigManager();
        this.dataTypeRegistry = engine.getDataTypeRegistry();
        this.oAuthService = oAuthService;
        this.jsonMapper = new ObjectMapper();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod().toUpperCase();

        // Enable CORS for modern web UX
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");

        if ("OPTIONS".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        try {
            switch (path) {
                case "/api/datatypes" -> handleGetDataTypes(exchange);
                case "/api/preferences" -> {
                    if ("GET".equals(method)) handleGetPreferences(exchange);
                    else if ("POST".equals(method)) handleSavePreferences(exchange);
                    else sendError(exchange, 405, "Method Not Allowed");
                }
                case "/api/auth/status" -> handleGetAuthStatus(exchange);
                case "/api/auth/refresh" -> handleRefreshToken(exchange);
                case "/api/auth/url" -> handleGetAuthUrl(exchange);
                case "/api/auth/start" -> handleStartAuth(exchange);
                case "/api/auth/code" -> handleExchangeAuthCode(exchange);
                case "/api/test/single" -> handleRunSingleTest(exchange);
                case "/api/test/run-all" -> handleRunAllTests(exchange);
                case "/api/test/script" -> handleRunScript(exchange);
                case "/api/scripts" -> handleListScripts(exchange);
                case "/api/health/profile" -> handleGetProfile(exchange);
                case "/api/health/devices" -> handleGetDevices(exchange);
                default -> sendError(exchange, 404, "Endpoint not found: " + path);
            }
        } catch (Exception e) {
            logger.error("Error processing API request {} {}: {}", method, path, e.getMessage(), e);
            sendError(exchange, 500, "Internal Server Error: " + e.getMessage());
        }
    }

    private void handleGetDataTypes(HttpExchange exchange) throws IOException {
        List<DataTypeDefinition> list = dataTypeRegistry.getAllDataTypes();
        sendJson(exchange, 200, jsonMapper.writeValueAsString(list));
    }

    private void handleGetPreferences(HttpExchange exchange) throws IOException {
        Preferences prefs = configManager.getPreferences();
        ObjectNode node = jsonMapper.valueToTree(prefs);
        // Mask client secret for security
        String secret = prefs.getClientSecret();
        if (secret != null && secret.length() > 4) {
            node.put("clientSecretMasked", "********" + secret.substring(secret.length() - 4));
        } else {
            node.put("clientSecretMasked", "");
        }
        sendJson(exchange, 200, node.toPrettyString());
    }

    private void handleSavePreferences(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Preferences incoming = jsonMapper.readValue(body, Preferences.class);
        Preferences current = configManager.getPreferences();

        if (incoming.getClientId() != null) current.setClientId(incoming.getClientId());
        if (incoming.getClientSecret() != null && !incoming.getClientSecret().contains("****")) {
            current.setClientSecret(incoming.getClientSecret());
        }
        if (incoming.getRedirectUri() != null) current.setRedirectUri(incoming.getRedirectUri());
        if (incoming.getApiBaseUrl() != null) current.setApiBaseUrl(incoming.getApiBaseUrl());
        if (incoming.getDefaultUserId() != null) current.setDefaultUserId(incoming.getDefaultUserId());
        current.setMockMode(incoming.isMockMode());
        if (incoming.getScopes() != null && !incoming.getScopes().isEmpty()) {
            current.setScopes(incoming.getScopes());
        }

        configManager.savePreferences(current);
        sendJson(exchange, 200, "{\"success\": true, \"message\": \"Preferences saved successfully.\"}");
    }

    private void handleGetAuthStatus(HttpExchange exchange) throws IOException {
        UserAuthorization auth = configManager.getUserAuthorization();
        Preferences prefs = configManager.getPreferences();

        ObjectNode node = jsonMapper.createObjectNode();
        node.put("healthUserID", auth.getHealthUserID());
        node.put("hasAccessToken", auth.hasAccessToken());
        node.put("hasRefreshToken", auth.hasRefreshToken());
        node.put("isExpired", auth.isExpired());
        node.put("remainingSeconds", auth.getRemainingSeconds());
        node.put("expiresAtEpochMs", auth.getExpiresAtEpochMs());
        node.put("updatedAt", auth.getUpdatedAt());
        node.put("scope", auth.getScope());
        node.put("mockMode", prefs.isMockMode());

        sendJson(exchange, 200, node.toPrettyString());
    }

    private void handleRefreshToken(HttpExchange exchange) throws IOException {
        boolean ok = oAuthService.refreshAccessToken();
        UserAuthorization auth = configManager.getUserAuthorization();

        ObjectNode resp = jsonMapper.createObjectNode();
        resp.put("success", ok);
        resp.put("message", ok ? "Token refreshed successfully." : "Token refresh failed.");
        resp.put("remainingSeconds", auth.getRemainingSeconds());
        resp.put("updatedAt", auth.getUpdatedAt());

        sendJson(exchange, ok ? 200 : 400, resp.toPrettyString());
    }

    private void handleGetAuthUrl(HttpExchange exchange) throws IOException {
        String url = oAuthService.buildAuthorizationUrl("web_ux_" + System.currentTimeMillis());
        ObjectNode resp = jsonMapper.createObjectNode();
        resp.put("authUrl", url);
        sendJson(exchange, 200, resp.toPrettyString());
    }

    private void handleExchangeAuthCode(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JsonNode json = jsonMapper.readTree(body);
        String code = json.path("code").asText("");

        if (code.isEmpty()) {
            sendError(exchange, 400, "Missing 'code' parameter.");
            return;
        }

        boolean ok = oAuthService.exchangeCodeForTokens(code);
        ObjectNode resp = jsonMapper.createObjectNode();
        resp.put("success", ok);
        resp.put("message", ok ? "Tokens successfully retrieved and stored." : "Failed to exchange code.");
        sendJson(exchange, ok ? 200 : 400, resp.toPrettyString());
    }

    private synchronized void handleStartAuth(HttpExchange exchange) throws IOException {
        Preferences prefs = configManager.getPreferences();
        if (activeReceiver != null) {
            activeReceiver.stop();
            activeReceiver = null;
        }

        try {
            activeReceiver = LocalOAuthReceiver.fromRedirectUri(prefs.getRedirectUri(), oAuthService);
            activeReceiver.start();

            // Run callback listener in background thread
            final LocalOAuthReceiver receiverRef = activeReceiver;
            Thread listenerThread = new Thread(() -> {
                try {
                    logger.info("Local OAuth receiver waiting for callback on {}...", prefs.getRedirectUri());
                    receiverRef.waitForCallback(180);
                } catch (Exception e) {
                    logger.warn("Callback listener encountered: {}", e.getMessage());
                }
            }, "oauth-callback-listener");
            listenerThread.setDaemon(true);
            listenerThread.start();

            String authUrl = oAuthService.buildAuthorizationUrl("web_ux_" + System.currentTimeMillis());
            boolean opened = BrowserUtil.openBrowser(authUrl);

            ObjectNode resp = jsonMapper.createObjectNode();
            resp.put("success", true);
            resp.put("authUrl", authUrl);
            resp.put("redirectUri", prefs.getRedirectUri());
            resp.put("browserOpened", opened);
            resp.put("message", "Local receiver listening on " + prefs.getRedirectUri() + ". Browser launched.");
            sendJson(exchange, 200, resp.toPrettyString());
        } catch (Exception e) {
            logger.error("Failed to start OAuth callback receiver: {}", e.getMessage(), e);
            sendError(exchange, 500, "Failed to start receiver on " + prefs.getRedirectUri() + ": " + e.getMessage());
        }
    }

    private void handleRunSingleTest(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JsonNode json = jsonMapper.readTree(body);

        String dataType = json.path("dataType").asText("steps");
        String endpoint = json.path("endpoint").asText("list");
        String requestBody = json.has("body") ? json.path("body").asText(null) : null;

        Map<String, String> params = new HashMap<>();
        if (json.has("params") && json.get("params").isObject()) {
            json.get("params").fields().forEachRemaining(entry -> params.put(entry.getKey(), entry.getValue().asText()));
        }

        TestResult result = engine.executeSingleTest(dataType, endpoint, params, requestBody);
        sendJson(exchange, 200, jsonMapper.writeValueAsString(result));
    }

    private void handleRunAllTests(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String endpoint = "list";
        if (body != null && !body.trim().isEmpty()) {
            JsonNode json = jsonMapper.readTree(body);
            if (json.has("endpoint")) {
                endpoint = json.path("endpoint").asText("list");
            }
        }

        List<TestResult> results = engine.executeAllDataTypesTest(endpoint);
        sendJson(exchange, 200, jsonMapper.writeValueAsString(results));
    }

    private void handleListScripts(HttpExchange exchange) throws IOException {
        File scriptsDir = new File("scripts");
        ArrayNode arr = jsonMapper.createArrayNode();
        if (scriptsDir.exists() && scriptsDir.isDirectory()) {
            File[] files = scriptsDir.listFiles((dir, name) -> name.endsWith(".yaml") || name.endsWith(".json"));
            if (files != null) {
                for (File f : files) {
                    ObjectNode sNode = arr.addObject();
                    sNode.put("filename", f.getName());
                    sNode.put("path", f.getPath());
                    sNode.put("sizeBytes", f.length());
                }
            }
        }
        sendJson(exchange, 200, arr.toPrettyString());
    }

    private void handleRunScript(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JsonNode json = jsonMapper.readTree(body);

        String scriptPath = json.path("scriptPath").asText("scripts/sample_suite.yaml");
        File scriptFile = new File(scriptPath);

        if (!scriptFile.exists()) {
            sendError(exchange, 404, "Script file not found: " + scriptPath);
            return;
        }

        ScriptRunner runner = new ScriptRunner(engine);
        int exitCode = runner.runScript(scriptFile);

        ObjectNode resp = jsonMapper.createObjectNode();
        resp.put("exitCode", exitCode);
        resp.put("success", exitCode == 0);
        resp.put("message", exitCode == 0 ? "All script tests passed successfully." : "One or more script tests failed.");

        sendJson(exchange, 200, resp.toPrettyString());
    }

    private void handleGetProfile(HttpExchange exchange) throws IOException {
        ApiResponse resp = engine.getApiClient().getProfile(null);
        sendApiResponse(exchange, resp);
    }

    private void handleGetDevices(HttpExchange exchange) throws IOException {
        ApiResponse resp = engine.getApiClient().listPairedDevices(null);
        sendApiResponse(exchange, resp);
    }

    private void sendApiResponse(HttpExchange exchange, ApiResponse resp) throws IOException {
        ObjectNode node = jsonMapper.createObjectNode();
        node.put("statusCode", resp.getStatusCode());
        node.put("statusMessage", resp.getStatusMessage());
        node.put("latencyMs", resp.getLatencyMs());
        node.put("requestUrl", resp.getRequestUrl());
        node.put("curlCommand", resp.getCurlCommand());
        node.put("body", resp.getBody());
        sendJson(exchange, 200, node.toPrettyString());
    }

    private void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        String json = "{\"error\": \"" + message.replace("\"", "\\\"") + "\"}";
        sendJson(exchange, statusCode, json);
    }
}
