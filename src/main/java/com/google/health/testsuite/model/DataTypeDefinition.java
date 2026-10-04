package com.google.health.testsuite.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a Google Health API data type definition as maintained in datatypes.yaml.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataTypeDefinition {

    @JsonProperty("name")
    private String name;

    @JsonProperty("displayName")
    private String displayName;

    @JsonProperty("scopeRequired")
    private String scopeRequired;

    @JsonProperty("writeScopeRequired")
    private String writeScopeRequired;

    @JsonProperty("endpointsSupported")
    private List<String> endpointsSupported = new ArrayList<>();

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
        this.name = name;
        this.displayName = displayName;
        this.scopeRequired = scopeRequired;
        this.endpointsSupported = endpointsSupported != null ? endpointsSupported : new ArrayList<>();
        this.filterParameterName = filterParameterName;
        this.webhooksSupported = webhooksSupported;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.unit = unit;
    }

    public boolean supportsEndpoint(String endpoint) {
        if (endpointsSupported == null || endpoint == null) {
            return false;
        }
        for (String ep : endpointsSupported) {
            if (ep.equalsIgnoreCase(endpoint.trim())) {
                return true;
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

    public List<String> getEndpointsSupported() {
        return endpointsSupported;
    }

    public void setEndpointsSupported(List<String> endpointsSupported) {
        this.endpointsSupported = endpointsSupported;
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

    @Override
    public String toString() {
        return "DataTypeDefinition{" +
                "name='" + name + '\'' +
                ", displayName='" + displayName + '\'' +
                ", scopeRequired='" + scopeRequired + '\'' +
                ", endpointsSupported=" + endpointsSupported +
                ", filterParameterName='" + filterParameterName + '\'' +
                ", webhooksSupported=" + webhooksSupported +
                ", range=[" + minValue + ", " + maxValue + " " + unit + "]" +
                '}';
    }
}
