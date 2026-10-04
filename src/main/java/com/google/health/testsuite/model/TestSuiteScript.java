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
}
