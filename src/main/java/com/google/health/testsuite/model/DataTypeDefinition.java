package com.google.health.testsuite.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a Google Health API data type definition as maintained in datatypes.yaml.
 * The endpointsSupported map explicitly lists all canonical endpoints and boolean true/false support.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataTypeDefinition {

    /**
     * Canonical list of all REST operations available on Google Health API data points resources.
     */
    public static final List<String> ALL_ENDPOINTS = List.of(
            "list", "get", "create", "batchDelete", "rollUp", "dailyRollUp",
            "exportExerciseTcx", "reconcile", "patch"
    );

    @JsonProperty("name")
    private String name;

    @JsonProperty("displayName")
    private String displayName;

    @JsonProperty("scopeRequired")
    private String scopeRequired;

    @JsonProperty("writeScopeRequired")
    private String writeScopeRequired;

    @JsonAlias({"endpointVersion", "version", "apiVersion"})
    @JsonProperty("endpointVersion")
    private String endpointVersion = "v4";

    @JsonProperty("endpointsSupported")
    private Map<String, Boolean> endpointsSupported = createDefaultEndpointsMap();

    @JsonProperty("filterParameterName")
    private String filterParameterName;

    @JsonProperty("webhooksSupported")
    private boolean webhooksSupported;

    @JsonProperty("minValue")
    private Double minValue;

    @JsonProperty("maxValue")
    private Double maxValue;

    @JsonProperty("unit")
    private String unit;

    @JsonProperty("sampleValueField")
    private String sampleValueField;

    public DataTypeDefinition() {
    }

    public DataTypeDefinition(String name, String displayName, String scopeRequired,
                              List<String> endpointsSupported, String filterParameterName,
                              boolean webhooksSupported, Double minValue, Double maxValue, String unit) {
        this(name, displayName, "v4", scopeRequired, endpointsSupported, filterParameterName, webhooksSupported, minValue, maxValue, unit);
    }

    public DataTypeDefinition(String name, String displayName, String endpointVersion, String scopeRequired,
                              List<String> endpointsSupported, String filterParameterName,
                              boolean webhooksSupported, Double minValue, Double maxValue, String unit) {
        this.name = name;
        this.displayName = displayName;
        this.endpointVersion = (endpointVersion != null && !endpointVersion.trim().isEmpty()) ? endpointVersion.trim() : "v4";
        this.scopeRequired = scopeRequired;
        setEndpointsSupportedFromList(endpointsSupported);
        this.filterParameterName = filterParameterName;
        this.webhooksSupported = webhooksSupported;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.unit = unit;
    }

    public DataTypeDefinition(String name, String displayName, String endpointVersion, String scopeRequired,
                              Map<String, Boolean> endpointsSupported, String filterParameterName,
                              boolean webhooksSupported, Double minValue, Double maxValue, String unit) {
        this.name = name;
        this.displayName = displayName;
        this.endpointVersion = (endpointVersion != null && !endpointVersion.trim().isEmpty()) ? endpointVersion.trim() : "v4";
        this.scopeRequired = scopeRequired;
        setEndpointsSupported(endpointsSupported);
        this.filterParameterName = filterParameterName;
        this.webhooksSupported = webhooksSupported;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.unit = unit;
    }

    /**
     * Generates a template map containing all canonical endpoints initialized to false.
     */
    public static Map<String, Boolean> createDefaultEndpointsMap() {
        Map<String, Boolean> map = new LinkedHashMap<>();
        for (String ep : ALL_ENDPOINTS) {
            map.put(ep, false);
        }
        return map;
    }

    /**
     * Generates a template map containing all canonical endpoints initialized to true.
     */
    public static Map<String, Boolean> createAllEnabledEndpointsMap() {
        Map<String, Boolean> map = new LinkedHashMap<>();
        for (String ep : ALL_ENDPOINTS) {
            map.put(ep, true);
        }
        return map;
    }

    /**
     * Returns a copy of this DataTypeDefinition with all endpoints enabled.
     */
    public DataTypeDefinition withAllEndpointsEnabled() {
        DataTypeDefinition copy = new DataTypeDefinition();
        copy.name = this.name;
        copy.displayName = this.displayName;
        copy.endpointVersion = this.endpointVersion;
        copy.scopeRequired = this.scopeRequired;
        copy.writeScopeRequired = this.writeScopeRequired;
        copy.endpointsSupported = createAllEnabledEndpointsMap();
        copy.filterParameterName = this.filterParameterName;
        copy.webhooksSupported = this.webhooksSupported;
        copy.minValue = this.minValue;
        copy.maxValue = this.maxValue;
        copy.unit = this.unit;
        copy.sampleValueField = this.sampleValueField;
        return copy;
    }

    /**
     * Checks if the given endpoint is supported (true). Case-insensitive.
     */
    public boolean supportsEndpoint(String endpoint) {
        if (endpointsSupported == null || endpoint == null) {
            return false;
        }
        for (Map.Entry<String, Boolean> entry : endpointsSupported.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(endpoint.trim())) {
                return Boolean.TRUE.equals(entry.getValue());
            }
        }
        return false;
    }

    public boolean isWithinRange(double value) {
        if (minValue != null && value < minValue) {
            return false;
        }
        if (maxValue != null && value > maxValue) {
            return false;
        }
        return true;
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName != null ? displayName : name;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getScopeRequired() {
        return scopeRequired;
    }

    public void setScopeRequired(String scopeRequired) {
        this.scopeRequired = scopeRequired;
    }

    public String getWriteScopeRequired() {
        return writeScopeRequired;
    }

    public void setWriteScopeRequired(String writeScopeRequired) {
        this.writeScopeRequired = writeScopeRequired;
    }

    public Map<String, Boolean> getEndpointsSupported() {
        return endpointsSupported;
    }

    public void setEndpointsSupported(Map<String, Boolean> endpointsSupported) {
        this.endpointsSupported = createDefaultEndpointsMap();
        if (endpointsSupported != null) {
            for (Map.Entry<String, Boolean> entry : endpointsSupported.entrySet()) {
                this.endpointsSupported.put(entry.getKey(), Boolean.TRUE.equals(entry.getValue()));
            }
        }
    }

    public void setEndpointsSupportedFromList(List<String> endpoints) {
        this.endpointsSupported = createDefaultEndpointsMap();
        if (endpoints != null) {
            for (String ep : endpoints) {
                if (ep != null) {
                    this.endpointsSupported.put(ep.trim(), true);
                }
            }
        }
    }

    @JsonSetter("endpointsSupported")
    public void deserializeEndpointsSupported(JsonNode node) {
        this.endpointsSupported = createDefaultEndpointsMap();
        if (node == null) return;
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                this.endpointsSupported.put(entry.getKey(), entry.getValue().asBoolean(false));
            });
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                this.endpointsSupported.put(item.asText(), true);
            }
        }
    }

    @JsonIgnore
    public List<String> getSupportedEndpointNames() {
        List<String> list = new ArrayList<>();
        if (endpointsSupported != null) {
            for (Map.Entry<String, Boolean> entry : endpointsSupported.entrySet()) {
                if (Boolean.TRUE.equals(entry.getValue())) {
                    list.add(entry.getKey());
                }
            }
        }
        return list;
    }

    public String getFilterParameterName() {
        return filterParameterName;
    }

    public void setFilterParameterName(String filterParameterName) {
        this.filterParameterName = filterParameterName;
    }

    public boolean isWebhooksSupported() {
        return webhooksSupported;
    }

    public void setWebhooksSupported(boolean webhooksSupported) {
        this.webhooksSupported = webhooksSupported;
    }

    public Double getMinValue() {
        return minValue;
    }

    public void setMinValue(Double minValue) {
        this.minValue = minValue;
    }

    public Double getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(Double maxValue) {
        this.maxValue = maxValue;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getSampleValueField() {
        return sampleValueField;
    }

    public void setSampleValueField(String sampleValueField) {
        this.sampleValueField = sampleValueField;
    }

    public String getEndpointVersion() {
        if (endpointVersion == null || endpointVersion.trim().isEmpty()) {
            return "v4";
        }
        return endpointVersion.trim();
    }

    public void setEndpointVersion(String endpointVersion) {
        this.endpointVersion = endpointVersion;
    }

    @Override
    public String toString() {
        return "DataTypeDefinition{" +
                "name='" + name + '\'' +
                ", displayName='" + displayName + '\'' +
                ", endpointVersion='" + getEndpointVersion() + '\'' +
                ", scopeRequired='" + scopeRequired + '\'' +
                ", endpointsSupported=" + endpointsSupported +
                ", filterParameterName='" + filterParameterName + '\'' +
                ", webhooksSupported=" + webhooksSupported +
                ", range=[" + minValue + ", " + maxValue + " " + unit + "]" +
                '}';
    }
}
