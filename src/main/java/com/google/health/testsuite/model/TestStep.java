package com.google.health.testsuite.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a single test step in a test script file (Mode 3).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TestStep {

    @JsonProperty("name")
    private String name;

    @JsonProperty("action")
    private String action = "list"; // list, get, create, rollup, dailyRollup, batchDelete, profile, pairedDevices, checkAuth

    @JsonProperty("dataType")
    private String dataType;

    @JsonProperty("dataPointId")
    private String dataPointId;

    @JsonProperty("userId")
    private String userId;

    @JsonProperty("queryParams")
    private Map<String, String> queryParams = new HashMap<>();

    @JsonProperty("body")
    private String body;

    // Assertions
    @JsonProperty("assertStatus")
    private List<Integer> assertStatus = new ArrayList<>(List.of(200));

    @JsonProperty("validateRange")
    private boolean validateRange = true;

    @JsonProperty("assertContains")
    private List<String> assertContains = new ArrayList<>();

    public TestStep() {
    }

    public String getName() {
        if (name != null && !name.isEmpty()) {
            return name;
        }
        return action + (dataType != null ? " (" + dataType + ")" : "");
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getDataPointId() {
        return dataPointId;
    }

    public void setDataPointId(String dataPointId) {
        this.dataPointId = dataPointId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Map<String, String> getQueryParams() {
        return queryParams;
    }

    public void setQueryParams(Map<String, String> queryParams) {
        this.queryParams = queryParams != null ? queryParams : new HashMap<>();
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public List<Integer> getAssertStatus() {
        return assertStatus;
    }

    public void setAssertStatus(Object assertStatusObj) {
        this.assertStatus = new ArrayList<>();
        if (assertStatusObj instanceof Number num) {
            this.assertStatus.add(num.intValue());
        } else if (assertStatusObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Number num) {
                    this.assertStatus.add(num.intValue());
                } else if (item != null) {
                    try {
                        this.assertStatus.add(Integer.parseInt(item.toString()));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
    }

    public boolean isValidateRange() {
        return validateRange;
    }

    public void setValidateRange(boolean validateRange) {
        this.validateRange = validateRange;
    }

    public List<String> getAssertContains() {
        return assertContains;
    }

    public void setAssertContains(List<String> assertContains) {
        this.assertContains = assertContains != null ? assertContains : new ArrayList<>();
    }
}
