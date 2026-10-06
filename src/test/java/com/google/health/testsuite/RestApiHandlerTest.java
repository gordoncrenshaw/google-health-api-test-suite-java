package com.google.health.testsuite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.health.testsuite.auth.OAuthService;
import com.google.health.testsuite.client.HealthApiClient;
import com.google.health.testsuite.config.ConfigManager;
import com.google.health.testsuite.config.DataTypeRegistry;
import com.google.health.testsuite.config.Preferences;
import com.google.health.testsuite.runner.SuiteExecutionEngine;
import com.google.health.testsuite.server.RestApiHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RestApiHandlerTest {

    private File tempPrefFile;
    private File tempSecretJsonFile;
    private File tempTokensDir;
    private ConfigManager configManager;
    private OAuthService oAuthService;
    private HttpServer server;
    private int port;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper jsonMapper = new ObjectMapper();

    @BeforeEach
    public void setUp() throws Exception {
        tempPrefFile = File.createTempFile("test_preferences", ".yaml");
        tempSecretJsonFile = File.createTempFile("client_secret", ".json");
        tempTokensDir = Files.createTempDirectory("test_tokens_").toFile();

        Files.writeString(tempSecretJsonFile.toPath(),
                "{\"web\":{\"client_id\":\"test-client-id.apps.googleusercontent.com\",\"client_secret\":\"test-secret\"}}");

        configManager = new ConfigManager(tempPrefFile, tempSecretJsonFile);
        oAuthService = new OAuthService(configManager, tempTokensDir);
        HealthApiClient apiClient = new HealthApiClient(configManager, oAuthService);
        DataTypeRegistry registry = new DataTypeRegistry(new File("config/datatypes.yaml"));
        SuiteExecutionEngine engine = new SuiteExecutionEngine(configManager, registry, apiClient);

        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api", new RestApiHandler(engine, oAuthService));
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (server != null) {
            server.stop(0);
        }
        if (tempPrefFile.exists()) tempPrefFile.delete();
        if (tempSecretJsonFile.exists()) tempSecretJsonFile.delete();
        if (tempTokensDir != null && tempTokensDir.exists()) {
            Files.walk(tempTokensDir.toPath())
                    .sorted(Comparator.reverseOrder())
                    .map(java.nio.file.Path::toFile)
                    .forEach(File::delete);
        }
    }

    @Test
    void testGetScopesEndpoint() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/scopes"))
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());

        JsonNode root = jsonMapper.readTree(resp.body());
        assertTrue(root.has("scopes"));
        assertTrue(root.get("scopes").isArray());
        assertTrue(root.get("scopes").size() >= 10);
    }

    @Test
    void testDynamicScopeAdditionPresentedByApi() throws Exception {
        // Add a brand new scope to the preferences.yaml file
        File prefFile = configManager.getPreferencesFile();
        String currentContent = Files.exists(prefFile.toPath()) ? Files.readString(prefFile.toPath()) : "scopes:\n";
        String customScope = "https://www.googleapis.com/auth/googlehealth.custom_symptom.readonly";
        Files.writeString(prefFile.toPath(), currentContent + "  - \"" + customScope + "\"\n");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/scopes"))
                .GET()
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());

        JsonNode root = jsonMapper.readTree(resp.body());
        boolean foundCustom = false;
        for (JsonNode s : root.get("scopes")) {
            if (customScope.equals(s.asText())) {
                foundCustom = true;
                break;
            }
        }
        assertTrue(foundCustom, "Newly added scope in preferences file must be presented by GET /api/scopes");
    }

    @Test
    void testAuthUrlWithSelectedScopes() throws Exception {
        String jsonPayload = """
                {
                  "scopes": [
                    "https://www.googleapis.com/auth/googlehealth.activity_and_fitness.readonly",
                    "https://www.googleapis.com/auth/googlehealth.sleep.readonly"
                  ]
                }
                """;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/auth/url"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());

        JsonNode root = jsonMapper.readTree(resp.body());
        assertTrue(root.has("authUrl"));
        String url = root.get("authUrl").asText();
        assertTrue(url.contains("scope="));
        assertTrue(url.contains("activity_and_fitness.readonly"));
        assertTrue(url.contains("sleep.readonly"));
    }
}
