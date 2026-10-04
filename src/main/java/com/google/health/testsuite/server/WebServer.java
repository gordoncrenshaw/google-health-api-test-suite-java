package com.google.health.testsuite.server;

import com.google.health.testsuite.auth.OAuthService;
import com.google.health.testsuite.runner.SuiteExecutionEngine;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.Executors;

/**
 * Mode 2: Lightweight Embedded HTTP Server hosting the HTML/CSS/JavaScript Web UX
 * and the supporting REST API.
 */
public class WebServer {

    private static final Logger logger = LoggerFactory.getLogger(WebServer.class);

    private final int port;
    private final SuiteExecutionEngine engine;
    private final OAuthService oAuthService;
    private HttpServer server;

    public WebServer(int port, SuiteExecutionEngine engine, OAuthService oAuthService) {
        this.port = port;
        this.engine = engine;
        this.oAuthService = oAuthService;
    }

    public synchronized void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);

        // 1. Static Web Content Handler
        server.createContext("/", new StaticFileHandler());

        // 2. REST API Handler
        RestApiHandler apiHandler = new RestApiHandler(engine, oAuthService);
        server.createContext("/api", apiHandler);

        // Multi-threaded executor
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        logger.info("Web UX server running at: http://localhost:{}", port);
        System.out.println("\n\u001B[32m\u001B[1m========================================================================\u001B[0m");
        System.out.println("\u001B[36m\u001B[1m Google Health API Test Suite - Web UX (Mode 2) is RUNNING!\u001B[0m");
        System.out.printf(" Open your browser to: \u001B[34m\u001B[4mhttp://localhost:%d\u001B[0m\n", port);
        System.out.println(" Press Ctrl+C in terminal to stop server.");
        System.out.println("\u001B[32m\u001B[1m========================================================================\u001B[0m\n");
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(1);
            server = null;
            logger.info("Web server stopped.");
        }
    }

    public int getPort() {
        return port;
    }

    /**
     * Serves HTML, CSS, JavaScript, and asset files.
     */
    private static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }

            // Remove any query params or leading slash
            if (path.startsWith("/")) {
                path = path.substring(1);
            }

            byte[] content = loadAsset(path);
            if (content == null) {
                // If not found, try fallback to index.html for single page app
                content = loadAsset("index.html");
                if (content == null) {
                    String notFound = "<html><body><h1>404 Not Found</h1><p>Resource " + path + " not found.</p></body></html>";
                    byte[] bytes = notFound.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                    exchange.sendResponseHeaders(404, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                    }
                    return;
                }
            }

            String contentType = determineContentType(path);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
            exchange.sendResponseHeaders(200, content.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(content);
            }
        }

        private byte[] loadAsset(String relativePath) {
            // 1. Try classpath resource: /web/relativePath
            try (InputStream is = getClass().getResourceAsStream("/web/" + relativePath)) {
                if (is != null) {
                    return is.readAllBytes();
                }
            } catch (Exception ignored) {}

            // 2. Try file system relative to project root: src/main/resources/web/
            File f = new File("src/main/resources/web", relativePath);
            if (f.exists() && f.isFile()) {
                try {
                    return Files.readAllBytes(f.toPath());
                } catch (IOException ignored) {}
            }

            return null;
        }

        private String determineContentType(String path) {
            if (path.endsWith(".html") || path.endsWith(".htm")) return "text/html; charset=UTF-8";
            if (path.endsWith(".css")) return "text/css; charset=UTF-8";
            if (path.endsWith(".js")) return "application/javascript; charset=UTF-8";
            if (path.endsWith(".json")) return "application/json; charset=UTF-8";
            if (path.endsWith(".png")) return "image/png";
            if (path.endsWith(".svg")) return "image/svg+xml";
            if (path.endsWith(".ico")) return "image/x-icon";
            return "text/plain; charset=UTF-8";
        }
    }
}
