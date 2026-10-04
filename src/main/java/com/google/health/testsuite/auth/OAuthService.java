package com.google.health.testsuite.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.health.testsuite.config.ConfigManager;
import com.google.health.testsuite.config.Preferences;
import com.google.health.testsuite.config.UserAuthorization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * Handles Google OAuth 2.0 authorization URL construction, code exchange,
 * and automatic refresh token rotation & persistence.
 */
public class OAuthService {

    private static final Logger logger = LoggerFactory.getLogger(OAuthService.class);

    private final ConfigManager configManager;
    private final HttpClient httpClient;
    private final ObjectMapper jsonMapper;

    public OAuthService(ConfigManager configManager) {
        this.configManager = configManager;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.jsonMapper = new ObjectMapper();
    }

    /**
     * Builds the Google OAuth 2.0 authorization URL.
     */
    public String buildAuthorizationUrl(String state) {
        Preferences prefs = configManager.getPreferences();
        String stateParam = (state != null && !state.isEmpty()) ? state : UUID.randomUUID().toString();

        StringBuilder sb = new StringBuilder(prefs.getAuthUri());
        sb.append("?client_id=").append(urlEncode(prefs.getClientId()));
        sb.append("&redirect_uri=").append(urlEncode(prefs.getRedirectUri()));
        sb.append("&response_type=code");
        sb.append("&scope=").append(urlEncode(prefs.getJoinedScopes()));
        sb.append("&access_type=offline");
        sb.append("&prompt=consent");
        sb.append("&state=").append(urlEncode(stateParam));

        return sb.toString();
    }

    /**
     * Exchanges an authorization code for access and refresh tokens.
     */
    public boolean exchangeCodeForTokens(String authorizationCode) {
        Preferences prefs = configManager.getPreferences();

        if (prefs.isMockMode()) {
            logger.info("[Mock Mode] Simulating authorization code exchange.");
            String mockAccessToken = "mock_access_token_" + UUID.randomUUID().toString().substring(0, 8);
            String mockRefreshToken = "mock_refresh_token_" + UUID.randomUUID().toString().substring(0, 8);
            configManager.updateTokens(mockAccessToken, mockRefreshToken, 3600, prefs.getJoinedScopes());
            return true;
        }

        String formBody = "code=" + urlEncode(authorizationCode) +
                "&client_id=" + urlEncode(prefs.getClientId()) +
                "&client_secret=" + urlEncode(prefs.getClientSecret()) +
                "&redirect_uri=" + urlEncode(prefs.getRedirectUri()) +
                "&grant_type=authorization_code";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(prefs.getTokenUri()))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode json = jsonMapper.readTree(response.body());
                String accessToken = json.path("access_token").asText("");
                String refreshToken = json.path("refresh_token").asText("");
                long expiresIn = json.path("expires_in").asLong(3600);
                String scope = json.path("scope").asText(prefs.getJoinedScopes());

                configManager.updateTokens(accessToken, refreshToken, expiresIn, scope);
                logger.info("Successfully exchanged authorization code for tokens!");
                return true;
            } else {
                logger.error("Token exchange failed with HTTP {}: {}", response.statusCode(), response.body());
                return false;
            }
        } catch (Exception e) {
            logger.error("Exception during authorization code exchange: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Refreshes the access token using the stored refresh token.
     * Automatically updates and saves the userAuthorization file upon completion.
     */
    public synchronized boolean refreshAccessToken() {
        Preferences prefs = configManager.getPreferences();
        UserAuthorization userAuth = configManager.getUserAuthorization();

        if (prefs.isMockMode()) {
            logger.info("[Mock Mode] Simulating access token refresh.");
            String newMockToken = "mock_access_token_" + UUID.randomUUID().toString().substring(0, 8);
            configManager.updateTokens(newMockToken, userAuth.getRefreshToken(), 3600, userAuth.getScope());
            return true;
        }

        if (!userAuth.hasRefreshToken()) {
            logger.warn("Cannot refresh access token: No refresh token stored in userAuthorization file.");
            return false;
        }

        logger.info("Refreshing access token using stored refresh token...");
        String formBody = "client_id=" + urlEncode(prefs.getClientId()) +
                "&client_secret=" + urlEncode(prefs.getClientSecret()) +
                "&refresh_token=" + urlEncode(userAuth.getRefreshToken()) +
                "&grant_type=refresh_token";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(prefs.getTokenUri()))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode json = jsonMapper.readTree(response.body());
                String newAccessToken = json.path("access_token").asText("");
                // Google might optionally rotate the refresh token
                String newRefreshToken = json.path("refresh_token").asText(userAuth.getRefreshToken());
                long expiresIn = json.path("expires_in").asLong(3600);
                String scope = json.path("scope").asText(userAuth.getScope());

                configManager.updateTokens(newAccessToken, newRefreshToken, expiresIn, scope);
                logger.info("Token refresh successful! Updated userAuthorization file with new expiry ({}s).", expiresIn);
                return true;
            } else {
                logger.error("Token refresh failed with HTTP {}: {}", response.statusCode(), response.body());
                return false;
            }
        } catch (Exception e) {
            logger.error("Exception during token refresh: {}", e.getMessage(), e);
            return false;
        }
    }

    private String urlEncode(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
