package com.google.health.testsuite.model;

import com.fasterxml.jackson.annotation.JsonAlias;
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

    @JsonProperty("bugId")
    @JsonAlias({"bug_id", "buganizerId", "buganizer_id", "bug"})
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String bugId = null;

    @JsonProperty("stopOnError")
    private boolean stopOnError = false;

    @JsonProperty("maxLatencyMs")
    @JsonAlias({"maxLatency", "latencyMs", "latency", "assertMaxLatencyMs", "assertLatencyMs"})
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private Long maxLatencyMs = null;

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
        return getEffectiveUserId("me");
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getEffectiveUserId(String defaultUserId) {
        if (userId != null && !userId.trim().isEmpty()) {
            return userId.trim();
        }
        if (defaultUserId != null && !defaultUserId.trim().isEmpty()) {
            return defaultUserId.trim();
        }
        return "me";
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getBugId() {
        return bugId;
    }

    public void setBugId(String bugId) {
        this.bugId = bugId;
    }

    public boolean isStopOnError() {
        return stopOnError;
    }

    public void setStopOnError(boolean stopOnError) {
        this.stopOnError = stopOnError;
    }

    public Long getMaxLatencyMs() {
        return maxLatencyMs;
    }

    public void setMaxLatencyMs(Long maxLatencyMs) {
        this.maxLatencyMs = maxLatencyMs;
    }

    public List<TestStep> getSteps() {
        return steps;
    }

    public void setSteps(List<TestStep> steps) {
        this.steps = steps != null ? steps : new ArrayList<>();
    }

    /**
     * Returns the list of executable test steps, expanding any steps configured with
     * userId "both" (either explicitly on the step, inherited from the script, or inherited
     * from Preferences) into separate steps for "me" and "healthUserId", and inheriting
     * global maxLatencyMs when not overridden on the step.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public List<TestStep> getExpandedSteps() {
        return getExpandedSteps("me");
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public List<TestStep> getExpandedSteps(String defaultUserId) {
        List<TestStep> expanded = new ArrayList<>();
        String scriptUser = getEffectiveUserId(defaultUserId);

        boolean hasStatefulSequence = ("both".equalsIgnoreCase(scriptUser)
                || steps.stream().anyMatch(s -> "both".equalsIgnoreCase(s.getUserId())))
                && steps.stream().anyMatch(s ->
                (s.getDataPointId() != null && (s.getDataPointId().contains("uniqueName") || s.getDataPointId().contains("dataPointId")))
                || (s.getBody() != null && (s.getBody().contains("uniqueName") || s.getBody().contains("dataPointId"))));

        if (hasStatefulSequence) {
            for (String targetUser : List.of("me", "healthUserId")) {
                for (TestStep step : steps) {
                    String stepUser = (step.getUserId() != null && !step.getUserId().trim().isEmpty())
                            ? step.getUserId().trim()
                            : scriptUser;
                    Long effectiveMaxLatency = step.getMaxLatencyMs() != null
                            ? step.getMaxLatencyMs()
                            : this.maxLatencyMs;
                    String effectiveBugId = (step.getBugId() != null && !step.getBugId().trim().isEmpty())
                            ? step.getBugId().trim()
                            : this.bugId;
                    if ("both".equalsIgnoreCase(stepUser)) {
                        TestStep copyStep = step.copy();
                        copyStep.setUserId(targetUser);
                        copyStep.setMaxLatencyMs(effectiveMaxLatency);
                        if (effectiveBugId != null && !effectiveBugId.isBlank()) {
                            copyStep.setBugId(effectiveBugId);
                        }
                        copyStep.setName(appendUserSuffix(step.getName(), targetUser));
                        expanded.add(copyStep);
                    } else if ("me".equalsIgnoreCase(targetUser)) {
                        TestStep s = step.copy();
                        s.setUserId(stepUser);
                        s.setMaxLatencyMs(effectiveMaxLatency);
                        if (effectiveBugId != null && !effectiveBugId.isBlank()) {
                            s.setBugId(effectiveBugId);
                        }
                        expanded.add(s);
                    }
                }
            }
            return expanded;
        }

        for (TestStep step : steps) {
            String stepUser = (step.getUserId() != null && !step.getUserId().trim().isEmpty())
                    ? step.getUserId().trim()
                    : scriptUser;
            Long effectiveMaxLatency = step.getMaxLatencyMs() != null
                    ? step.getMaxLatencyMs()
                    : this.maxLatencyMs;
            String effectiveBugId = (step.getBugId() != null && !step.getBugId().trim().isEmpty())
                    ? step.getBugId().trim()
                    : this.bugId;

            if ("both".equalsIgnoreCase(stepUser)) {
                TestStep stepMe = step.copy();
                stepMe.setUserId("me");
                stepMe.setMaxLatencyMs(effectiveMaxLatency);
                if (effectiveBugId != null && !effectiveBugId.isBlank()) {
                    stepMe.setBugId(effectiveBugId);
                }
                stepMe.setName(appendUserSuffix(step.getName(), "me"));
                expanded.add(stepMe);

                TestStep stepHealthUser = step.copy();
                stepHealthUser.setUserId("healthUserId");
                stepHealthUser.setMaxLatencyMs(effectiveMaxLatency);
                if (effectiveBugId != null && !effectiveBugId.isBlank()) {
                    stepHealthUser.setBugId(effectiveBugId);
                }
                stepHealthUser.setName(appendUserSuffix(step.getName(), "healthUserId"));
                expanded.add(stepHealthUser);
            } else {
                TestStep s = step.copy();
                s.setUserId(stepUser);
                s.setMaxLatencyMs(effectiveMaxLatency);
                if (effectiveBugId != null && !effectiveBugId.isBlank()) {
                    s.setBugId(effectiveBugId);
                }
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
