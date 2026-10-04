package com.google.health.testsuite.config;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores OAuth 2.0 client credentials, scopes, and API settings.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Preferences {

    @JsonProperty("clientId")
    private String clientId = "";

    @JsonProperty("clientSecret")
    private String clientSecret = "";

    @JsonProperty("authUri")
    private String authUri = "https://accounts.google.com/o/oauth2/v2/auth";

    @JsonProperty("tokenUri")
    private String tokenUri = "https://oauth2.googleapis.com/token";

    @JsonProperty("redirect_uri")
    @JsonAlias({"redirectUri", "redirect_uri"})
    private String redirectUri = "http://localhost:8888/callback";

    @JsonProperty("apiBaseUrl")
    private String apiBaseUrl = "https://health.googleapis.com";

    @JsonProperty("defaultUserId")
    private String defaultUserId = "me";

    @JsonProperty("mockMode")
    private boolean mockMode = false;

    @JsonProperty("scopes")
    private List<String> scopes = new ArrayList<>();

    public Preferences() {
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.trim().isEmpty() &&
                !clientId.contains("YOUR_GOOGLE_CLIENT_ID") &&
                clientSecret != null && !clientSecret.trim().isEmpty() &&
                !clientSecret.contains("YOUR_GOOGLE_CLIENT_SECRET");
    }

    // Getters and Setters
    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getAuthUri() {
        return authUri;
    }

    public void setAuthUri(String authUri) {
        this.authUri = authUri;
    }

    public String getTokenUri() {
        return tokenUri;
    }

    public void setTokenUri(String tokenUri) {
        this.tokenUri = tokenUri;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public String getDefaultUserId() {
        return defaultUserId;
    }

    public void setDefaultUserId(String defaultUserId) {
        this.defaultUserId = defaultUserId;
    }

    public boolean isMockMode() {
        return mockMode;
    }

    public void setMockMode(boolean mockMode) {
        this.mockMode = mockMode;
    }

    public List<String> getScopes() {
        return scopes;
    }

    public void setScopes(List<String> scopes) {
        this.scopes = scopes != null ? scopes : new ArrayList<>();
    }

    public String getJoinedScopes() {
        return scopes != null ? String.join(" ", scopes) : "";
    }
}
