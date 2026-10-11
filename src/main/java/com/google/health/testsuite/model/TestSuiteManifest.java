package com.google.health.testsuite.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Model representing a test suite manifest file (e.g., config/test_suite_manifest.yaml).
 * Defines the ordered list of YAML/JSON test script files to run during full suite execution.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TestSuiteManifest {

    @JsonProperty("name")
    private String name = "Google Health API Full Test Suite";

    @JsonProperty("description")
    private String description = "Master test suite executing data type and endpoint test scripts";

    @JsonProperty("scripts")
    private List<String> scripts = new ArrayList<>();

    public TestSuiteManifest() {
    }

    public TestSuiteManifest(String name, String description, List<String> scripts) {
        this.name = name != null ? name : "Google Health API Full Test Suite";
        this.description = description != null ? description : "";
        this.scripts = scripts != null ? new ArrayList<>(scripts) : new ArrayList<>();
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

    public List<String> getScripts() {
        return scripts;
    }

    public void setScripts(List<String> scripts) {
        this.scripts = scripts != null ? new ArrayList<>(scripts) : new ArrayList<>();
    }
}
