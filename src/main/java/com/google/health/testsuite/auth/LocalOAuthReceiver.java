package com.google.health.testsuite.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Temporary local HTTP server to receive OAuth 2.0 redirect callbacks
 * and automatically capture the authorization code.
 */
public class LocalOAuthReceiver {

    private static final Logger logger = LoggerFactory.getLogger(LocalOAuthReceiver.class);

    private final int port;
    private final String path;
    private final OAuthService oAuthService;
    private HttpServer server;
    private final CompletableFuture<Boolean> completionFuture = new CompletableFuture<>();

    public LocalOAuthReceiver(int port, String path, OAuthService oAuthService) {
        this.port = port;
        this.path = (path != null && !path.isEmpty()) ? path : "/oauth2callback";
        this.oAuthService = oAuthService;
    }

    public static LocalOAuthReceiver fromRedirectUri(String redirectUri, OAuthService oAuthService) {
        try {
            URI uri = URI.create(redirectUri);
            int port = uri.getPort() > 0 ? uri.getPort() : 8888;
            String path = uri.getPath();
            return new LocalOAuthReceiver(port, path, oAuthService);
        } catch (Exception e) {
            return new LocalOAuthReceiver(8888, "/oauth2callback", oAuthService);
        }
    }

    public synchronized void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext(path, new CallbackHandler());
        server.setExecutor(null); // default executor
        server.start();
        logger.info("Local OAuth receiver server started on port {}, waiting on callback at {}", port, path);
    }

    public boolean waitForCallback(long timeoutSeconds) {
        try {
            return completionFuture.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.warn("Timed out or interrupted waiting for OAuth callback: {}", e.getMessage());
            return false;
        } finally {
            stop();
        }
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(1);
            server = null;
            logger.debug("Local OAuth receiver server stopped.");
        }
    }

    private class CallbackHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            URI uri = exchange.getRequestURI();
            Map<String, String> queryParams = parseQueryParams(uri.getQuery());

            String code = queryParams.get("code");
            String error = queryParams.get("error");

            String responseHtml;
            int responseCode = 200;

            if (code != null && !code.isEmpty()) {
                boolean success = oAuthService.exchangeCodeForTokens(code);
                if (success) {
                    responseHtml = """
                            <!DOCTYPE html>
                            <html>
                            <head><title>Authorization Successful</title>
                            <style>body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0f172a; color: #f8fafc; text-align: center; padding: 60px 20px; } .card { background: #1e293b; border-radius: 12px; padding: 40px; display: inline-block; max-width: 500px; box-shadow: 0 10px 25px rgba(0,0,0,0.5); } h1 { color: #38bdf8; } p { color: #94a3b8; font-size: 16px; }</style>
                            </head>
                            <body>
                              <div class="card">
                                <h1>&#10004; Authorization Successful!</h1>
                                <p>Google Health API tokens have been received and saved into <code>userAuthorization.yaml</code>.</p>
                                <p>You can now return to the terminal or web application.</p>
                              </div>
                            </body></html>
                            """;
                    completionFuture.complete(true);
                } else {
                    responseCode = 500;
                    responseHtml = "<html><body><h1>Authorization Failed</h1><p>Failed to exchange code for tokens. Check console logs.</p></body></html>";
                    completionFuture.complete(false);
                }
            } else {
                responseCode = 400;
                responseHtml = "<html><body><h1>Authorization Error</h1><p>" + (error != null ? error : "No code provided") + "</p></body></html>";
                completionFuture.complete(false);
            }

            byte[] bytes = responseHtml.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(responseCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return params;
        }
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0 && idx < pair.length() - 1) {
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String val = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                params.put(key, val);
            }
        }
        return params;
    }
}
