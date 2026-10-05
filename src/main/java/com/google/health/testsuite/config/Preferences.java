package com.google.health.testsuite.config;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores OAuth 2.0 client credentials, scopes, and API settings.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Preferences {

    @JsonProperty(value = "clientId", access = JsonProperty.Access.WRITE_ONLY)
    private String clientId = "";

    @JsonProperty(value = "clientSecret", access = JsonProperty.Access.WRITE_ONLY)
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

    @JsonProperty("healthUserId")
    @JsonAlias({"health_user_id", "healthUserId"})
    private String healthUserId = "";

    @JsonProperty("defaultUserId")
    private String defaultUserId = "me";

    @JsonProperty("endpointUserId")
    @JsonAlias({"userIdMode", "endpointUserSyntax", "endpointSyntax", "userSyntax", "endpointUserIdSetting", "useHealthUserId", "use_health_user_id"})
    private String endpointUserId = "me";

    @JsonProperty("mockMode")
    private boolean mockMode = false;

    @JsonProperty("enableAllEndpoints")
    @JsonAlias({"enable_all_endpoints", "enableAllEndpointsForEachDatatype", "enableAllEndpoints"})
    private boolean enableAllEndpoints = false;

    @JsonProperty("scopes")
    private List<String> scopes = new ArrayList<>();


    public Preferences() {
    }

    @JsonIgnore
    public boolean isConfigured() {
        return clientId != null && !clientId.trim().isEmpty() &&
                !clientId.contains("YOUR_GOOGLE_CLIENT_ID") &&
                clientSecret != null && !clientSecret.trim().isEmpty() &&
                !clientSecret.contains("YOUR_GOOGLE_CLIENT_SECRET");
    }

    // Getters and Setters
    @JsonIgnore
    public String getClientId() {
        return clientId;
    }

    @JsonProperty("clientId")
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    @JsonIgnore
    public String getClientSecret() {
        return clientSecret;
    }

    @JsonProperty("clientSecret")
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

    public String getHealthUserId() {
        return healthUserId != null ? healthUserId : "";
    }

    public void setHealthUserId(String healthUserId) {
        this.healthUserId = healthUserId != null ? healthUserId : "";
    }

    public String getDefaultUserId() {
        return defaultUserId;
    }

    public void setDefaultUserId(String defaultUserId) {
        this.defaultUserId = defaultUserId;
    }

    public String getEndpointUserId() {
        if ("true".equalsIgnoreCase(endpointUserId)) {
            return "healthUserId";
        }
        if ("false".equalsIgnoreCase(endpointUserId)) {
            return "me";
        }
        return endpointUserId != null && !endpointUserId.trim().isEmpty() ? endpointUserId : "me";
    }

    public void setEndpointUserId(String endpointUserId) {
        if (endpointUserId == null || endpointUserId.trim().isEmpty()) {
            this.endpointUserId = "me";
        } else if ("true".equalsIgnoreCase(endpointUserId.trim())) {
            this.endpointUserId = "healthUserId";
        } else if ("false".equalsIgnoreCase(endpointUserId.trim())) {
            this.endpointUserId = "me";
        } else {
            this.endpointUserId = endpointUserId.trim();
        }
    }

    public String getEndpointUserSyntax() {
        return getEndpointUserId();
    }

    public void setEndpointUserSyntax(String endpointUserSyntax) {
        setEndpointUserId(endpointUserSyntax);
    }

    @JsonIgnore
    public boolean isUseHealthUserId() {
        String mode = getEndpointUserId();
        return "healthUserId".equalsIgnoreCase(mode) ||
               "health_user_id".equalsIgnoreCase(mode) ||
               "healthUserID".equalsIgnoreCase(mode) ||
               "true".equalsIgnoreCase(mode);
    }

    @JsonSetter("useHealthUserId")
    public void setUseHealthUserId(Boolean useHealthUserId) {
        if (useHealthUserId != null) {
            this.endpointUserId = useHealthUserId ? "healthUserId" : "me";
        }
    }

    public boolean isMockMode() {
        return mockMode;
    }

    public void setMockMode(boolean mockMode) {
        this.mockMode = mockMode;
    }

    public boolean isEnableAllEndpoints() {
        return enableAllEndpoints;
    }

    public void setEnableAllEndpoints(boolean enableAllEndpoints) {
        this.enableAllEndpoints = enableAllEndpoints;
    }

    public List<String> getScopes() {
        return scopes;
    }

    public void setScopes(List<String> scopes) {
        this.scopes = scopes != null ? scopes : new ArrayList<>();
    }

    @JsonIgnore
    public String getJoinedScopes() {
        return scopes != null ? String.join(" ", scopes) : "";
    }
}
