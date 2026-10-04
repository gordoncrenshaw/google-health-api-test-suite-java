package com.google.health.testsuite.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Stores the healthUserID, access token, refresh token, and expiration timestamp.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserAuthorization {

    @JsonProperty("healthUserID")
    private String healthUserID = "me";

    @JsonProperty("accessToken")
    private String accessToken = "";

    @JsonProperty("refreshToken")
    private String refreshToken = "";

    @JsonProperty("tokenType")
    private String tokenType = "Bearer";

    @JsonProperty("expiresAtEpochMs")
    private long expiresAtEpochMs = 0;

    @JsonProperty("updatedAt")
    private String updatedAt = "";

    @JsonProperty("scope")
    private String scope = "";

    public UserAuthorization() {
    }

    public UserAuthorization(String healthUserID, String accessToken, String refreshToken,
                             long expiresAtEpochMs, String scope) {
        this.healthUserID = healthUserID != null && !healthUserID.isEmpty() ? healthUserID : "me";
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresAtEpochMs = expiresAtEpochMs;
        this.updatedAt = Instant.now().toString();
        this.scope = scope;
    }

    @JsonIgnore
    public boolean hasAccessToken() {
        return accessToken != null && !accessToken.trim().isEmpty();
    }

    @JsonIgnore
    public boolean hasRefreshToken() {
        return refreshToken != null && !refreshToken.trim().isEmpty();
    }

    /**
     * Checks if the access token is expired or within 60 seconds of expiring.
     */
    @JsonIgnore
    public boolean isExpired() {
        if (!hasAccessToken()) {
            return true;
        }
        if (expiresAtEpochMs <= 0) {
            return false;
        }
        // Consider token expired if less than 60 seconds remain to prevent race condition
        return System.currentTimeMillis() >= (expiresAtEpochMs - 60_000);
    }

    @JsonIgnore
    public long getRemainingSeconds() {
        if (expiresAtEpochMs <= 0) {
            return 0;
        }
        long diff = expiresAtEpochMs - System.currentTimeMillis();
        return Math.max(0, diff / 1000);
    }

    // Getters and Setters
    public String getHealthUserID() {
        return (healthUserID != null && !healthUserID.trim().isEmpty()) ? healthUserID : "me";
    }

    public void setHealthUserID(String healthUserID) {
        this.healthUserID = healthUserID;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getTokenType() {
        return tokenType != null ? tokenType : "Bearer";
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public long getExpiresAtEpochMs() {
        return expiresAtEpochMs;
    }

    public void setExpiresAtEpochMs(long expiresAtEpochMs) {
        this.expiresAtEpochMs = expiresAtEpochMs;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    @Override
    public String toString() {
        return "UserAuthorization{" +
                "healthUserID='" + healthUserID + '\'' +
                ", hasAccessToken=" + hasAccessToken() +
                ", hasRefreshToken=" + hasRefreshToken() +
                ", expiresAt=" + Instant.ofEpochMilli(expiresAtEpochMs) +
                ", remainingSec=" + getRemainingSeconds() +
                '}';
    }
}
