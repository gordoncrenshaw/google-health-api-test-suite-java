package com.google.health.testsuite.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.google.health.testsuite.auth.BrowserUtil;
import com.google.health.testsuite.auth.LocalOAuthReceiver;
import com.google.health.testsuite.auth.OAuthService;
import com.google.health.testsuite.config.ConfigManager;
import com.google.health.testsuite.config.DataTypeRegistry;
import com.google.health.testsuite.config.Preferences;
import com.google.health.testsuite.config.UserAuthorization;
import com.google.health.testsuite.common.AppVersion;
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
import java.util.concurrent.atomic.AtomicInteger;

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
    private final ObjectMapper yamlMapper;
    private LocalOAuthReceiver activeReceiver;

    public RestApiHandler(SuiteExecutionEngine engine, OAuthService oAuthService) {
        this.engine = engine;
        this.configManager = engine.getConfigManager();
        this.dataTypeRegistry = engine.getDataTypeRegistry();
        this.oAuthService = oAuthService;
        this.jsonMapper = new ObjectMapper();
        YAMLFactory yamlFactory = YAMLFactory.builder()
                .enable(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                .enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR)
                .disable(YAMLGenerator.Feature.SPLIT_LINES)
                .build();
        this.yamlMapper = new ObjectMapper(yamlFactory);
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
                case "/api/datatypes" -> {
                    if ("GET".equals(method)) handleGetDataTypes(exchange);
                    else if ("POST".equals(method)) handleAddDataType(exchange);
                    else sendError(exchange, 405, "Method Not Allowed");
                }
                case "/api/preferences" -> {
                    if ("GET".equals(method)) handleGetPreferences(exchange);
                    else if ("POST".equals(method)) handleSavePreferences(exchange);
                    else sendError(exchange, 405, "Method Not Allowed");
                }
                case "/api/scopes" -> {
                    if ("GET".equals(method)) handleGetScopes(exchange);
                    else sendError(exchange, 405, "Method Not Allowed");
                }
                case "/api/manifest" -> {
                    if ("GET".equals(method)) handleGetManifest(exchange);
                    else if ("POST".equals(method) || "PUT".equals(method)) handleSaveManifest(exchange);
                    else sendError(exchange, 405, "Method Not Allowed");
                }
                case "/api/version", "/api/status" -> handleGetVersion(exchange);
                case "/api/auth/status" -> handleGetAuthStatus(exchange);
                case "/api/auth/refresh" -> handleRefreshToken(exchange);
                case "/api/auth/url" -> handleGetAuthUrl(exchange);
                case "/api/auth/start" -> handleStartAuth(exchange);
                case "/api/auth/code" -> handleExchangeAuthCode(exchange);
                case "/api/test/single" -> handleRunSingleTest(exchange);
                case "/api/test/run-all", "/api/test/run-stream" -> handleRunAllTests(exchange);
                case "/api/test/script" -> handleRunScript(exchange);
                case "/api/scripts" -> handleListScripts(exchange);
                case "/api/custom-tests" -> {
                    if ("POST".equals(method)) handleSaveCustomTest(exchange);
                    else if ("GET".equals(method)) handleListCustomTests(exchange);
                    else sendError(exchange, 405, "Method Not Allowed");
                }
                case "/api/health/profile" -> {
                    if ("POST".equals(method) || "PATCH".equals(method)) handleUpdateProfile(exchange);
                    else handleGetProfile(exchange);
                }
                case "/api/health/irnProfile", "/api/health/getIrnProfile" -> handleGetIrnProfile(exchange);
                case "/api/health/settings", "/api/health/getSettings" -> {
                    if ("POST".equals(method) || "PATCH".equals(method)) handleUpdateSettings(exchange);
                    else handleGetSettings(exchange);
                }
                case "/api/health/devices", "/api/health/getDevices" -> handleGetDevices(exchange);
                case "/api/health/identity", "/api/health/getIdentity" -> handleGetIdentity(exchange);
                default -> sendError(exchange, 404, "Endpoint not found: " + path);
            }
        } catch (Exception e) {
            logger.error("Error processing API request {} {}: {}", method, path, e.getMessage(), e);
            sendError(exchange, 500, "Internal Server Error: " + e.getMessage());
        }
    }

    private void handleGetDataTypes(HttpExchange exchange) throws IOException {
        boolean enableAll = configManager.getPreferences().isEnableAllEndpoints();
        List<DataTypeDefinition> list = dataTypeRegistry.getAllDataTypes(enableAll);
        sendJson(exchange, 200, jsonMapper.writeValueAsString(list));
    }

    private void handleAddDataType(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (body == null || body.trim().isEmpty()) {
            sendError(exchange, 400, "Request body is empty.");
            return;
        }

        DataTypeDefinition newDef;
        try {
            newDef = jsonMapper.readValue(body, DataTypeDefinition.class);
        } catch (Exception e) {
            sendError(exchange, 400, "Invalid JSON data type payload: " + e.getMessage());
            return;
        }

        if (newDef.getName() == null || newDef.getName().trim().isEmpty()) {
            sendError(exchange, 400, "Property 'name' (identifier) is required.");
            return;
        }

        String name = newDef.getName().trim().toLowerCase();
        if (dataTypeRegistry.hasDataType(name)) {
            sendError(exchange, 409, "Data type '" + name + "' already exists in registry.");
            return;
        }

        if (newDef.getDisplayName() == null || newDef.getDisplayName().trim().isEmpty()) {
            newDef.setDisplayName(name);
        }
        if (newDef.getEndpointVersion() == null || newDef.getEndpointVersion().trim().isEmpty()) {
            newDef.setEndpointVersion("v4");
        }

        boolean ok = dataTypeRegistry.addDataType(newDef);
        if (ok) {
            ObjectNode resp = jsonMapper.createObjectNode();
            resp.put("success", true);
            resp.put("message", "Data type '" + name + "' successfully added to registry and datatypes.yaml.");
            resp.set("dataType", jsonMapper.valueToTree(newDef));
            sendJson(exchange, 201, resp.toPrettyString());
        } else {
            sendError(exchange, 500, "Failed to persist new data type to datatypes.yaml.");
        }
    }

    private void handleGetVersion(HttpExchange exchange) throws IOException {
        ObjectNode node = jsonMapper.createObjectNode();
        node.put("version", AppVersion.VERSION);
        node.put("formattedVersion", AppVersion.getFormattedVersion());
        node.put("appName", AppVersion.APP_NAME);
        node.put("apiTarget", AppVersion.API_TARGET);
        node.put("releaseDate", AppVersion.RELEASE_DATE);
        sendJson(exchange, 200, node.toPrettyString());
    }

    private void handleGetPreferences(HttpExchange exchange) throws IOException {
        Preferences prefs = configManager.getPreferences();
        ObjectNode node = jsonMapper.valueToTree(prefs);
        node.put("appVersion", AppVersion.VERSION);
        node.put("formattedVersion", AppVersion.getFormattedVersion());
        node.put("apiTarget", AppVersion.API_TARGET);
        node.put("releaseDate", AppVersion.RELEASE_DATE);
        node.put("appName", AppVersion.APP_NAME);
        node.put("clientId", prefs.getClientId());
        node.put("endpointUserId", prefs.getEndpointUserId());
        node.put("endpointUserSyntax", prefs.getEndpointUserId());
        node.put("effectiveUserId", engine.getApiClient().getEffectiveUserId());
        node.put("useHealthUserId", prefs.isUseHealthUserId());
        node.put("enableAllEndpoints", prefs.isEnableAllEndpoints());
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

        boolean credsChanged = false;
        if (incoming.getClientId() != null && !incoming.getClientId().trim().isEmpty() &&
                !incoming.getClientId().trim().equals(current.getClientId())) {
            current.setClientId(incoming.getClientId().trim());
            credsChanged = true;
        }
        if (incoming.getClientSecret() != null && !incoming.getClientSecret().trim().isEmpty() &&
                !incoming.getClientSecret().contains("****") &&
                !incoming.getClientSecret().trim().equals(current.getClientSecret())) {
            current.setClientSecret(incoming.getClientSecret().trim());
            credsChanged = true;
        }
        if (credsChanged) {
            configManager.saveClientSecret(current.getClientId(), current.getClientSecret());
        }
        if (incoming.getRedirectUri() != null && !incoming.getRedirectUri().trim().isEmpty()) {
            current.setRedirectUri(incoming.getRedirectUri());
        }
        if (incoming.getApiBaseUrl() != null && !incoming.getApiBaseUrl().trim().isEmpty()) {
            current.setApiBaseUrl(incoming.getApiBaseUrl());
        }
        if (incoming.getHealthUserId() != null && !incoming.getHealthUserId().trim().isEmpty()) {
            current.setHealthUserId(incoming.getHealthUserId());
        }
        if (incoming.getDefaultUserId() != null && !incoming.getDefaultUserId().trim().isEmpty()) {
            current.setDefaultUserId(incoming.getDefaultUserId());
        }
        if (incoming.getEndpointUserId() != null && !incoming.getEndpointUserId().trim().isEmpty()) {
            current.setEndpointUserId(incoming.getEndpointUserId().trim());
        }
        current.setMockMode(incoming.isMockMode());
        current.setEnableAllEndpoints(incoming.isEnableAllEndpoints());
        if (incoming.getSuiteVisibleRows() > 0) {
            current.setSuiteVisibleRows(incoming.getSuiteVisibleRows());
        }
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
        node.put("enableAllEndpoints", prefs.isEnableAllEndpoints());
        node.put("endpointUserId", prefs.getEndpointUserId());
        node.put("endpointUserSyntax", prefs.getEndpointUserId());
        node.put("effectiveUserId", engine.getApiClient().getEffectiveUserId());
        node.put("useHealthUserId", prefs.isUseHealthUserId());
        node.put("accessToken", auth.getAccessToken() != null ? auth.getAccessToken() : "");
        node.put("appVersion", AppVersion.VERSION);

        sendJson(exchange, 200, node.toPrettyString());
    }

    private void handleRefreshToken(HttpExchange exchange) throws IOException {
        boolean ok = oAuthService.refreshAccessToken();
        UserAuthorization auth = configManager.getUserAuthorization();

        ObjectNode resp = jsonMapper.createObjectNode();
        resp.put("success", ok);
        resp.put("message", ok ? "Token refreshed successfully." : "Token refresh failed.");
        resp.put("accessToken", auth.getAccessToken() != null ? auth.getAccessToken() : "");
        resp.put("remainingSeconds", auth.getRemainingSeconds());
        resp.put("updatedAt", auth.getUpdatedAt());

        sendJson(exchange, ok ? 200 : 400, resp.toPrettyString());
    }

    private void handleGetScopes(HttpExchange exchange) throws IOException {
        List<String> scopes = configManager.getAvailableScopes();
        ObjectNode resp = jsonMapper.createObjectNode();
        ArrayNode arr = resp.putArray("scopes");
        for (String s : scopes) {
            arr.add(s);
        }
        sendJson(exchange, 200, resp.toPrettyString());
    }

    private void handleGetAuthUrl(HttpExchange exchange) throws IOException {
        List<String> scopes = null;
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (!body.isBlank()) {
                JsonNode json = jsonMapper.readTree(body);
                if (json.has("scopes") && json.get("scopes").isArray()) {
                    scopes = new ArrayList<>();
                    for (JsonNode n : json.get("scopes")) {
                        String s = n.asText("").trim();
                        if (!s.isEmpty()) scopes.add(s);
                    }
                }
            }
        }
        String url = oAuthService.buildAuthorizationUrl("web_ux_" + System.currentTimeMillis(), scopes);
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
        List<String> requestedScopes = null;
        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (!body.isBlank()) {
                JsonNode json = jsonMapper.readTree(body);
                if (json.has("scopes") && json.get("scopes").isArray()) {
                    requestedScopes = new ArrayList<>();
                    for (JsonNode item : json.get("scopes")) {
                        String s = item.asText("").trim();
                        if (!s.isEmpty()) requestedScopes.add(s);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Could not parse request body in handleStartAuth: {}", e.getMessage());
        }

        if (requestedScopes != null && !requestedScopes.isEmpty()) {
            prefs.setScopes(requestedScopes);
            configManager.savePreferences(prefs);
        }

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

            String authUrl = oAuthService.buildAuthorizationUrl("web_ux_" + System.currentTimeMillis(), requestedScopes);
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

        if (json.has("endpointUserId") && !json.path("endpointUserId").asText().trim().isEmpty()) {
            configManager.getPreferences().setEndpointUserId(json.path("endpointUserId").asText().trim());
        }

        Map<String, String> params = new HashMap<>();
        if (json.has("params") && json.get("params").isObject()) {
            json.get("params").fields().forEachRemaining(entry -> {
                String val = entry.getValue().asText();
                if (val != null && !val.trim().isEmpty()) {
                    params.put(entry.getKey(), val.trim());
                }
            });
        }

        TestResult result = engine.executeSingleTest(dataType, endpoint, params, requestBody);
        sendJson(exchange, 200, jsonMapper.writeValueAsString(result));
    }

    private void handleRunAllTests(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String scriptPath = null;
        String manifestPath = null;
        boolean stream = "/api/test/run-stream".equals(exchange.getRequestURI().getPath());
        if (body != null && !body.trim().isEmpty()) {
            try {
                JsonNode json = jsonMapper.readTree(body);
                if (json.has("manifestPath") && !json.path("manifestPath").asText().isBlank()) {
                    manifestPath = json.path("manifestPath").asText().trim();
                }
                if (json.has("scriptPath") && !json.path("scriptPath").asText().isBlank()) {
                    scriptPath = json.path("scriptPath").asText().trim();
                }
                if (json.has("stream") && json.path("stream").asBoolean(false)) {
                    stream = true;
                }
            } catch (Exception ignored) {
            }
        }

        String query = exchange.getRequestURI().getQuery();
        if (query != null && query.contains("stream=true")) {
            stream = true;
        }

        // Determine if we should execute a manifest or an explicit single script
        File manifestFile = null;
        if (manifestPath != null) {
            manifestFile = new File(manifestPath);
        } else if (scriptPath == null || scriptPath.isBlank() || scriptPath.endsWith("manifest.yaml")) {
            File defaultManifest = configManager.getManifestFile();
            if (defaultManifest != null && defaultManifest.exists() && defaultManifest.length() > 0) {
                manifestFile = defaultManifest;
            }
        }

        if (manifestFile != null && manifestFile.exists() && manifestFile.length() > 0) {
            if (stream) {
                handleStreamingManifestExecution(exchange, manifestFile);
            } else {
                logger.info("Executing full test suite from manifest: {}", manifestFile.getPath());
                ScriptRunner runner = new ScriptRunner(engine);
                List<TestResult> results = runner.executeManifest(manifestFile);
                sendJson(exchange, 200, jsonMapper.writeValueAsString(results));
            }
            return;
        }

        // Fallback to single script execution
        if (scriptPath == null || scriptPath.isBlank()) {
            scriptPath = "scripts/standard_tests.yaml";
        }
        File scriptFile = new File(scriptPath);
        if (!scriptFile.exists()) {
            logger.error("Test script file not found: {}", scriptFile.getAbsolutePath());
            sendError(exchange, 404, "Script file is not found: " + scriptPath);
            return;
        }

        if (stream) {
            handleStreamingExecution(exchange, scriptFile);
            return;
        }

        logger.info("Executing full test suite from script: {}", scriptFile.getPath());
        ScriptRunner runner = new ScriptRunner(engine);
        List<TestResult> results = runner.executeScript(scriptFile);
        sendJson(exchange, 200, jsonMapper.writeValueAsString(results));
    }

    private void handleStreamingManifestExecution(HttpExchange exchange, File manifestFile) throws IOException {
        logger.info("Starting real-time streaming execution for manifest: {}", manifestFile.getPath());

        exchange.getResponseHeaders().set("Content-Type", "application/x-ndjson; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-transform");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.getResponseHeaders().set("X-Accel-Buffering", "no");

        exchange.sendResponseHeaders(200, 0);

        try (OutputStream os = exchange.getResponseBody()) {
            ScriptRunner runner = new ScriptRunner(engine);
            TestSuiteManifest manifest;
            try {
                if (manifestFile.getName().endsWith(".json")) {
                    manifest = jsonMapper.readValue(manifestFile, TestSuiteManifest.class);
                } else {
                    manifest = yamlMapper.readValue(manifestFile, TestSuiteManifest.class);
                }
            } catch (Exception e) {
                logger.error("Error parsing manifest file {}: {}", manifestFile.getName(), e.getMessage());
                ObjectNode errNode = jsonMapper.createObjectNode();
                errNode.put("type", "error");
                errNode.put("message", "Error parsing manifest file: " + e.getMessage());
                errNode.put("errorMessage", e.getMessage());
                os.write((errNode.toString() + "\n").getBytes(StandardCharsets.UTF_8));
                os.flush();
                return;
            }

            int totalStepsAcrossAll = 0;
            List<String> scriptPaths = manifest.getScripts() != null ? manifest.getScripts() : Collections.emptyList();
            for (String sp : scriptPaths) {
                if (sp == null || sp.trim().isEmpty()) continue;
                File sf = new File(sp.trim());
                if (!sf.exists()) sf = new File("scripts", sp.trim());
                if (sf.exists()) {
                    try {
                        TestSuiteScript s = sf.getName().endsWith(".json")
                                ? jsonMapper.readValue(sf, TestSuiteScript.class)
                                : yamlMapper.readValue(sf, TestSuiteScript.class);
                        totalStepsAcrossAll += s.getSteps().size();
                    } catch (Exception ignored) {}
                }
            }

            ObjectNode startNode = jsonMapper.createObjectNode();
            startNode.put("type", "start");
            startNode.put("manifestName", manifest.getName());
            startNode.put("manifestFile", manifestFile.getPath());
            startNode.put("total", totalStepsAcrossAll);
            startNode.put("totalScripts", scriptPaths.size());
            os.write((startNode.toString() + "\n").getBytes(StandardCharsets.UTF_8));
            os.flush();

            long startTime = System.currentTimeMillis();
            AtomicInteger passedCount = new AtomicInteger(0);
            AtomicInteger failedCount = new AtomicInteger(0);

            runner.executeManifest(manifest, (index, total, step, result) -> {
                if (result.isPassed()) {
                    passedCount.incrementAndGet();
                } else {
                    failedCount.incrementAndGet();
                }

                ObjectNode stepNode = jsonMapper.createObjectNode();
                stepNode.put("type", "step");
                stepNode.put("index", index);
                stepNode.put("total", total);
                stepNode.put("scriptFile", result.getScriptFile() != null ? result.getScriptFile() : "");
                stepNode.set("result", jsonMapper.valueToTree(result));

                try {
                    os.write((stepNode.toString() + "\n").getBytes(StandardCharsets.UTF_8));
                    os.flush();
                } catch (IOException e) {
                    throw new RuntimeException("Client disconnected: " + e.getMessage(), e);
                }
            });

            long duration = System.currentTimeMillis() - startTime;
            ObjectNode completeNode = jsonMapper.createObjectNode();
            completeNode.put("type", "complete");
            completeNode.put("manifestName", manifest.getName());
            completeNode.put("total", totalStepsAcrossAll);
            completeNode.put("passed", passedCount.get());
            completeNode.put("failed", failedCount.get());
            completeNode.put("durationMs", duration);
            os.write((completeNode.toString() + "\n").getBytes(StandardCharsets.UTF_8));
            os.flush();
        } catch (Exception e) {
            logger.warn("Streaming manifest execution completed or connection closed: {}", e.getMessage());
        }
    }

    private void handleStreamingExecution(HttpExchange exchange, File scriptFile) throws IOException {
        logger.info("Starting real-time streaming execution for script: {}", scriptFile.getPath());

        exchange.getResponseHeaders().set("Content-Type", "application/x-ndjson; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-transform");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.getResponseHeaders().set("X-Accel-Buffering", "no");

        // 0 tells Sun HttpServer to use chunked transfer encoding
        exchange.sendResponseHeaders(200, 0);

        try (OutputStream os = exchange.getResponseBody()) {
            ScriptRunner runner = new ScriptRunner(engine);

            TestSuiteScript script;
            try {
                if (scriptFile.getName().endsWith(".json")) {
                    script = jsonMapper.readValue(scriptFile, TestSuiteScript.class);
                } else {
                    script = yamlMapper.readValue(scriptFile, TestSuiteScript.class);
                }
            } catch (Exception e) {
                logger.error("Error parsing script file {}: {}", scriptFile.getName(), e.getMessage());
                ObjectNode errNode = jsonMapper.createObjectNode();
                errNode.put("type", "error");
                errNode.put("message", "Error parsing script file: " + e.getMessage());
                errNode.put("errorMessage", e.getMessage());
                os.write((errNode.toString() + "\n").getBytes(StandardCharsets.UTF_8));
                os.flush();
                return;
            }

            int totalSteps = script.getExpandedSteps().size();
            ObjectNode startNode = jsonMapper.createObjectNode();
            startNode.put("type", "start");
            startNode.put("scriptName", script.getName());
            startNode.put("scriptFile", scriptFile.getName());
            startNode.put("total", totalSteps);
            os.write((startNode.toString() + "\n").getBytes(StandardCharsets.UTF_8));
            os.flush();

            long startTime = System.currentTimeMillis();
            AtomicInteger passedCount = new AtomicInteger(0);
            AtomicInteger failedCount = new AtomicInteger(0);

            runner.executeScript(scriptFile, (index, total, step, result) -> {
                if (result.isPassed()) {
                    passedCount.incrementAndGet();
                } else {
                    failedCount.incrementAndGet();
                }

                ObjectNode stepNode = jsonMapper.createObjectNode();
                stepNode.put("type", "step");
                stepNode.put("index", index);
                stepNode.put("total", total);
                stepNode.put("scriptFile", result.getScriptFile() != null ? result.getScriptFile() : scriptFile.getName());
                stepNode.set("result", jsonMapper.valueToTree(result));

                try {
                    os.write((stepNode.toString() + "\n").getBytes(StandardCharsets.UTF_8));
                    os.flush();
                } catch (IOException e) {
                    throw new RuntimeException("Client disconnected: " + e.getMessage(), e);
                }
            });

            long duration = System.currentTimeMillis() - startTime;
            ObjectNode completeNode = jsonMapper.createObjectNode();
            completeNode.put("type", "complete");
            completeNode.put("total", totalSteps);
            completeNode.put("passed", passedCount.get());
            completeNode.put("failed", failedCount.get());
            completeNode.put("durationMs", duration);
            os.write((completeNode.toString() + "\n").getBytes(StandardCharsets.UTF_8));
            os.flush();
        } catch (Exception e) {
            logger.warn("Streaming execution completed or connection closed: {}", e.getMessage());
        }
    }

    private void handleGetManifest(HttpExchange exchange) throws IOException {
        TestSuiteManifest manifest = configManager.loadTestSuiteManifest();
        ObjectNode node = (ObjectNode) jsonMapper.valueToTree(manifest);
        node.put("manifestPath", configManager.getManifestFile().getPath());
        node.put("manifestFileExists", configManager.getManifestFile().exists());
        sendJson(exchange, 200, jsonMapper.writeValueAsString(node));
    }

    private void handleSaveManifest(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        try {
            TestSuiteManifest manifest = jsonMapper.readValue(body, TestSuiteManifest.class);
            configManager.saveTestSuiteManifest(manifest);
            ObjectNode node = (ObjectNode) jsonMapper.valueToTree(manifest);
            node.put("success", true);
            node.put("manifestPath", configManager.getManifestFile().getPath());
            node.put("message", "Test suite manifest saved successfully.");
            sendJson(exchange, 200, jsonMapper.writeValueAsString(node));
        } catch (Exception e) {
            logger.error("Failed to save manifest: {}", e.getMessage(), e);
            sendError(exchange, 400, "Failed to save manifest: " + e.getMessage());
        }
    }

    private void handleListScripts(HttpExchange exchange) throws IOException {
        File scriptsDir = new File("scripts");
        ArrayNode arr = jsonMapper.createArrayNode();
        if (scriptsDir.exists() && scriptsDir.isDirectory()) {
            File[] files = scriptsDir.listFiles((dir, name) -> name.endsWith(".yaml") || name.endsWith(".json"));
            if (files != null) {
                Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
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

    private void handleListCustomTests(HttpExchange exchange) throws IOException {
        File scriptsDir = new File("scripts");
        ArrayNode arr = jsonMapper.createArrayNode();
        if (scriptsDir.exists() && scriptsDir.isDirectory()) {
            File[] files = scriptsDir.listFiles((dir, name) -> name.startsWith("custom_tests_") && (name.endsWith(".yaml") || name.endsWith(".json")));
            if (files != null) {
                Arrays.sort(files, Comparator.comparing(File::getName));
                for (File f : files) {
                    ObjectNode sNode = arr.addObject();
                    sNode.put("filename", f.getName());
                    sNode.put("path", f.getPath());
                    sNode.put("sizeBytes", f.length());
                    try {
                        TestSuiteScript s = yamlMapper.readValue(f, TestSuiteScript.class);
                        sNode.put("name", s.getName());
                        sNode.put("description", s.getDescription());
                        sNode.put("totalSteps", s.getSteps() != null ? s.getSteps().size() : 0);
                    } catch (Exception ignored) {
                        sNode.put("name", f.getName());
                        sNode.put("totalSteps", 0);
                    }
                }
            }
        }
        sendJson(exchange, 200, arr.toPrettyString());
    }

    private void handleSaveCustomTest(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (body == null || body.trim().isEmpty()) {
            sendError(exchange, 400, "Request body is empty.");
            return;
        }

        JsonNode json = jsonMapper.readTree(body);

        String rawFileName = json.path("fileName").asText("").trim();
        if (rawFileName.isEmpty()) {
            sendError(exchange, 400, "File name is required.");
            return;
        }

        // Clean up filename: ensure starts with 'custom_tests_' and ends with '.yaml'
        String fileName = rawFileName;
        if (fileName.startsWith("scripts/")) {
            fileName = fileName.substring("scripts/".length());
        }
        if (!fileName.startsWith("custom_tests_")) {
            fileName = "custom_tests_" + fileName;
        }
        if (!fileName.endsWith(".yaml") && !fileName.endsWith(".json")) {
            fileName = fileName + ".yaml";
        }

        File scriptsDir = new File("scripts");
        if (!scriptsDir.exists()) {
            scriptsDir.mkdirs();
        }
        File scriptFile = new File(scriptsDir, fileName);

        String mode = json.path("mode").asText("create").trim().toLowerCase(); // "create" or "append"
        boolean autoAddToManifest = json.path("autoAddToManifest").asBoolean(true);

        TestSuiteScript script;
        if ("append".equals(mode) && scriptFile.exists() && scriptFile.length() > 0) {
            try {
                script = yamlMapper.readValue(scriptFile, TestSuiteScript.class);
            } catch (Exception e) {
                sendError(exchange, 400, "Failed to parse existing custom test file for appending: " + e.getMessage());
                return;
            }
        } else {
            script = new TestSuiteScript();
            String suiteName = json.path("suiteName").asText("").trim();
            if (suiteName.isEmpty()) {
                String baseTitle = fileName.replace("custom_tests_", "").replace(".yaml", "").replace(".json", "");
                suiteName = "Custom Tests: " + baseTitle;
            }
            script.setName(suiteName);
            script.setDescription(json.path("description").asText("Custom test suite created via API Explorer"));
            // CRITICAL REQUIREMENT: userId is specific to the test step, NOT the YAML file top-level
            script.setUserId(null);
        }

        // Parse TestStep from payload
        JsonNode stepNode = json.path("step");
        if (stepNode.isMissingNode() || !stepNode.isObject()) {
            sendError(exchange, 400, "Step definition object 'step' is required.");
            return;
        }

        TestStep step;
        try {
            step = jsonMapper.treeToValue(stepNode, TestStep.class);
        } catch (Exception e) {
            sendError(exchange, 400, "Invalid step configuration: " + e.getMessage());
            return;
        }

        if (step.getName() == null || step.getName().trim().isEmpty()) {
            step.setName("Custom: " + (step.getDataType() != null ? step.getDataType() : "Unknown") + " " + step.getAction());
        }

        // Ensure step has userId set directly on the step
        if (step.getUserId() == null || step.getUserId().trim().isEmpty()) {
            String stepUserId = json.path("userId").asText("").trim();
            step.setUserId(!stepUserId.isEmpty() ? stepUserId : "me");
        }

        if (script.getSteps() == null) {
            script.setSteps(new ArrayList<>());
        }
        script.getSteps().add(step);

        // Ensure all step bodies with valid JSON are formatted as pretty JSON for clean YAML literal block output
        for (TestStep s : script.getSteps()) {
            if (s.getBody() != null && !s.getBody().trim().isEmpty()) {
                try {
                    JsonNode bodyJson = jsonMapper.readTree(s.getBody());
                    s.setBody(jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(bodyJson));
                } catch (Exception ignored) {
                    // Keep original text if not valid JSON
                }
            }
        }

        // Write script to file
        try {
            yamlMapper.writeValue(scriptFile, script);
            logger.info("Saved custom test suite to {} with {} steps", scriptFile.getPath(), script.getSteps().size());
        } catch (Exception e) {
            logger.error("Failed to write custom test file {}: {}", scriptFile.getPath(), e.getMessage(), e);
            sendError(exchange, 500, "Failed to write custom test file: " + e.getMessage());
            return;
        }

        // Auto add to manifest if requested
        boolean manifestUpdated = false;
        if (autoAddToManifest) {
            try {
                TestSuiteManifest manifest = configManager.loadTestSuiteManifest();
                String relativeScriptPath = "scripts/" + fileName;
                if (!manifest.getScripts().contains(relativeScriptPath)) {
                    manifest.getScripts().add(relativeScriptPath);
                    configManager.saveTestSuiteManifest(manifest);
                    manifestUpdated = true;
                    logger.info("Added {} to test suite manifest", relativeScriptPath);
                }
            } catch (Exception e) {
                logger.warn("Failed to auto-add {} to manifest: {}", fileName, e.getMessage());
            }
        }

        ObjectNode resp = jsonMapper.createObjectNode();
        resp.put("success", true);
        resp.put("fileName", fileName);
        resp.put("filePath", "scripts/" + fileName);
        resp.put("totalSteps", script.getSteps().size());
        resp.put("manifestUpdated", manifestUpdated);
        resp.put("message", "Custom test step saved to scripts/" + fileName + " (" + script.getSteps().size() + " total steps).");
        sendJson(exchange, 200, jsonMapper.writeValueAsString(resp));
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
        ScriptRunner.ScriptRunResult result = runner.runScriptWithDetails(scriptFile);

        ObjectNode resp = jsonMapper.createObjectNode();
        resp.put("exitCode", result.getExitCode());
        resp.put("success", result.isSuccess());
        resp.put("total", result.getTotalSteps());
        resp.put("passed", result.getPassedCount());
        resp.put("failed", result.getFailedCount());
        resp.put("durationMs", result.getDurationMs());

        if (result.getErrorMessage() != null && !result.getErrorMessage().isBlank()) {
            resp.put("errorMessage", result.getErrorMessage());
        }

        if (result.isSuccess()) {
            resp.put("message", "All script tests passed successfully.");
        } else if (result.getErrorMessage() != null && !result.getErrorMessage().isBlank() && result.getFailedTestNames().isEmpty()) {
            resp.put("message", result.getErrorMessage());
        } else {
            String namesStr = String.join(", ", result.getFailedTestNames());
            resp.put("message", "One or more script tests failed: " + namesStr);
        }

        ArrayNode namesArray = resp.putArray("failedTestNames");
        for (String name : result.getFailedTestNames()) {
            namesArray.add(name);
        }

        ArrayNode testsArray = resp.putArray("failedTests");
        for (TestResult tr : result.getFailedTests()) {
            ObjectNode item = testsArray.addObject();
            item.put("testName", tr.getTestName());
            if (tr.getDataType() != null) {
                item.put("dataType", tr.getDataType());
            }
            if (tr.getEndpoint() != null) {
                item.put("endpoint", tr.getEndpoint());
            }
            item.put("statusCode", tr.getStatusCode());
            item.put("latencyMs", tr.getLatencyMs());
            item.put("message", tr.getMessage());
        }

        sendJson(exchange, 200, resp.toPrettyString());
    }

    private void handleGetProfile(HttpExchange exchange) throws IOException {
        ApiResponse resp = engine.getApiClient().getProfile(null);
        sendApiResponse(exchange, resp);
    }

    private void handleGetIrnProfile(HttpExchange exchange) throws IOException {
        ApiResponse resp = engine.getApiClient().getIrnProfile(null);
        sendApiResponse(exchange, resp);
    }

    private void handleGetSettings(HttpExchange exchange) throws IOException {
        ApiResponse resp = engine.getApiClient().getSettings(null);
        sendApiResponse(exchange, resp);
    }

    private void handleUpdateProfile(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        ApiResponse resp = engine.getApiClient().updateProfile(null, body);
        sendApiResponse(exchange, resp);
    }

    private void handleUpdateSettings(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        ApiResponse resp = engine.getApiClient().updateSettings(null, body);
        sendApiResponse(exchange, resp);
    }

    private void handleGetDevices(HttpExchange exchange) throws IOException {
        ApiResponse resp = engine.getApiClient().listPairedDevices(null);
        sendApiResponse(exchange, resp);
    }

    private void handleGetIdentity(HttpExchange exchange) throws IOException {
        ApiResponse resp = engine.getApiClient().getIdentity(null);
        Preferences prefs = configManager.getPreferences();
        ObjectNode node = jsonMapper.createObjectNode();
        node.put("statusCode", resp.getStatusCode());
        node.put("statusMessage", resp.getStatusMessage());
        node.put("latencyMs", resp.getLatencyMs());
        node.put("requestUrl", resp.getRequestUrl());
        node.put("curlCommand", resp.getCurlCommand());
        node.put("body", resp.getBody());
        node.put("healthUserId", prefs.getHealthUserId());
        node.put("endpointUserId", engine.getApiClient().getEffectiveUserId());
        sendJson(exchange, 200, node.toPrettyString());
    }

    private void sendApiResponse(HttpExchange exchange, ApiResponse resp) throws IOException {
        ObjectNode node = jsonMapper.createObjectNode();
        node.put("statusCode", resp.getStatusCode());
        node.put("statusMessage", resp.getStatusMessage());
        node.put("latencyMs", resp.getLatencyMs());
        node.put("requestUrl", resp.getRequestUrl());
        node.put("curlCommand", resp.getCurlCommand());
        node.put("endpointUserId", engine.getApiClient().getEffectiveUserId());
        node.put("body", resp.getBody());
        sendJson(exchange, 200, node.toPrettyString());
    }

    private void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(statusCode, -1);
            return;
        }
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
