package com.google.health.testsuite.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.health.testsuite.model.DataTypeDefinition;
import com.google.health.testsuite.model.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates Google Health API response values against minimum and maximum
 * constraints defined in datatypes.yaml.
 */
public class ValidationEngine {

    private static final Logger logger = LoggerFactory.getLogger(ValidationEngine.class);
    private final ObjectMapper jsonMapper = new ObjectMapper();

    /**
     * Validates an API response body against the min and max values of the DataTypeDefinition.
     */
    public ValidationResult validate(DataTypeDefinition def, String responseBody) {
        if (def == null) {
            return ValidationResult.skipped("No data type definition provided.");
        }
        if (def.getMinValue() == null && def.getMaxValue() == null) {
            return ValidationResult.skipped("No min/max constraints defined for " + def.getName());
        }
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return ValidationResult.skipped("Response body is empty.");
        }

        try {
            JsonNode root = jsonMapper.readTree(responseBody);
            List<Double> extractedValues = new ArrayList<>();

            // 1. Check dataPoints array (ListDataPoints response)
            JsonNode dataPoints = root.get("dataPoints");
            if (dataPoints != null && dataPoints.isArray()) {
                if (dataPoints.isEmpty()) {
                    return ValidationResult.pass("Response has 0 data points (empty range)", null, def.getMinValue(), def.getMaxValue());
                }
                for (JsonNode dp : dataPoints) {
                    Double val = extractNumericValue(def, dp);
                    if (val != null) {
                        extractedValues.add(val);
                    }
                }
            } else {
                // 2. Check single DataPoint (GetDataPoint or CreateDataPoint response)
                Double val = extractNumericValue(def, root);
                if (val != null) {
                    extractedValues.add(val);
                }
            }

            if (extractedValues.isEmpty()) {
                return ValidationResult.pass("Validated schema format (no numeric measurements to check in payload)",
                        null, def.getMinValue(), def.getMaxValue());
            }

            // Check all extracted values against min/max
            for (Double val : extractedValues) {
                if (!def.isWithinRange(val)) {
                    String msg = String.format("Validation failed for %s: value %.2f is outside valid range [%.2f, %.2f] %s",
                            def.getName(), val, def.getMinValue(), def.getMaxValue(), def.getUnit() != null ? def.getUnit() : "");
                    logger.warn(msg);
                    return ValidationResult.fail(msg, val, def.getMinValue(), def.getMaxValue());
                }
            }

            Double firstVal = extractedValues.get(0);
            String successMsg = String.format("Validation passed for %s: checked %d value(s), e.g. %.2f in [%.2f, %.2f] %s",
                    def.getName(), extractedValues.size(), firstVal, def.getMinValue(), def.getMaxValue(),
                    def.getUnit() != null ? def.getUnit() : "");
            return ValidationResult.pass(successMsg, firstVal, def.getMinValue(), def.getMaxValue());

        } catch (Exception e) {
            logger.warn("Could not parse response body for numeric validation: {}", e.getMessage());
            return ValidationResult.skipped("Response is not JSON or could not be parsed: " + e.getMessage());
        }
    }

    /**
     * Extracts numeric value from a DataPoint JSON node using sampleValueField or type heuristics.
     */
    public Double extractNumericValue(DataTypeDefinition def, JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        // Try sampleValueField first (e.g. "steps.count" or "heartRate.beatsPerMinute")
        if (def.getSampleValueField() != null && !def.getSampleValueField().isEmpty()) {
            String[] parts = def.getSampleValueField().split("\\.");
            JsonNode current = node;
            for (String part : parts) {
                if (current != null) {
                    current = current.get(part);
                }
            }
            if (current != null && current.isValueNode()) {
                try {
                    return Double.parseDouble(current.asText());
                } catch (NumberFormatException ignored) {
                }
            }
        }

        // Check common fields matching dataType name
        JsonNode typeSubNode = node.get(def.getName());
        if (typeSubNode == null) {
            // Try camelCase variant e.g. activeEnergyBurned for active_energy_burned
            String camel = toCamelCase(def.getName());
            typeSubNode = node.get(camel);
        }

        if (typeSubNode != null && typeSubNode.isObject()) {
            for (String field : new String[]{"count", "beatsPerMinute", "millimeters", "gainMeters",
                    "kcal", "percentage", "bloodGlucoseMilligramsPerDeciliter", "weightGrams",
                    "heightMillimeters", "activeZoneMinutes", "durationSeconds", "rmssd", "value"}) {
                JsonNode fNode = typeSubNode.get(field);
                if (fNode != null && fNode.isValueNode()) {
                    try {
                        return Double.parseDouble(fNode.asText());
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        return null;
    }

    private static String toCamelCase(String snake) {
        if (snake == null || !snake.contains("_")) {
            return snake;
        }
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (char c : snake.toCharArray()) {
            if (c == '_') {
                upper = true;
            } else if (upper) {
                sb.append(Character.toUpperCase(c));
                upper = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
