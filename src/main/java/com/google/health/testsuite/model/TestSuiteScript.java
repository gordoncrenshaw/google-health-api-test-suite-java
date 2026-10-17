package com.google.health.testsuite.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * Model representing a test suite script file containing metadata and multiple test steps.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TestSuiteScript {

    @JsonProperty("name")
    private String name = "Unnamed Test Script";

    @JsonProperty("description")
    private String description = "";

    @JsonProperty("userId")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String userId = null;

    @JsonProperty("stopOnError")
    private boolean stopOnError = false;

    @JsonProperty("steps")
    private List<TestStep> steps = new ArrayList<>();

    public TestSuiteScript() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @JsonProperty("userId")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public String getUserId() {
        return userId;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getEffectiveUserId() {
        return userId != null && !userId.trim().isEmpty() ? userId.trim() : "me";
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public boolean isStopOnError() {
        return stopOnError;
    }

    public void setStopOnError(boolean stopOnError) {
        this.stopOnError = stopOnError;
    }

    public List<TestStep> getSteps() {
        return steps;
    }

    public void setSteps(List<TestStep> steps) {
        this.steps = steps != null ? steps : new ArrayList<>();
    }

    /**
     * Returns the list of executable test steps, expanding any steps configured with
     * userId "both" (either explicitly on the step or inherited from the script) into
     * separate steps for "me" and "healthUserId".
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public List<TestStep> getExpandedSteps() {
        List<TestStep> expanded = new ArrayList<>();
        String scriptUser = getEffectiveUserId();

        for (TestStep step : steps) {
            String stepUser = (step.getUserId() != null && !step.getUserId().trim().isEmpty())
                    ? step.getUserId().trim()
                    : scriptUser;

            if ("both".equalsIgnoreCase(stepUser)) {
                TestStep stepMe = step.copy();
                stepMe.setUserId("me");
                stepMe.setName(appendUserSuffix(step.getName(), "me"));
                expanded.add(stepMe);

                TestStep stepHealthUser = step.copy();
                stepHealthUser.setUserId("healthUserId");
                stepHealthUser.setName(appendUserSuffix(step.getName(), "healthUserId"));
                expanded.add(stepHealthUser);
            } else {
                TestStep s = step.copy();
                s.setUserId(stepUser);
                expanded.add(s);
            }
        }
        return expanded;
    }

    private static String appendUserSuffix(String originalName, String suffix) {
        if (originalName == null || originalName.isEmpty()) {
            return "(" + suffix + ")";
        }
        String cleaned = originalName;
        if (cleaned.endsWith(" (Default)")) {
            cleaned = cleaned.substring(0, cleaned.length() - " (Default)".length());
        }
        if (cleaned.toLowerCase().endsWith(" (" + suffix.toLowerCase() + ")")) {
            return cleaned;
        }
        return cleaned + " (" + suffix + ")";
    }
}
