package com.google.health.testsuite.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.health.testsuite.model.ApiResponse;
import com.google.health.testsuite.model.DataTypeDefinition;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Provides offline/mock responses mimicking Google Health API (v4)
 * for testing when live credentials are not available or during CI.
 */
public class MockHealthBackend {

    private final ObjectMapper jsonMapper = new ObjectMapper();

    public ApiResponse handleRequest(String httpMethod, String path, Map<String, String> queryParams,
                                      String requestBody, DataTypeDefinition def, String healthUserId) {
        long startTime = System.currentTimeMillis();
        int statusCode = 200;
        String statusMessage = "OK";
        String responseBody = "{}";

        String effectiveUser = (healthUserId != null && !healthUserId.isEmpty()) ? healthUserId : "me";

        try {
            if (path.contains("/profile")) {
                ObjectNode profile = jsonMapper.createObjectNode();
                profile.put("name", "users/" + effectiveUser + "/profile");
                profile.put("healthUserId", "gh-user-" + UUID.randomUUID().toString().substring(0, 8));
                profile.put("displayName", "Test Health User");
                profile.put("locale", "en-US");
                responseBody = profile.toPrettyString();

            } else if (path.contains("/pairedDevices")) {
                ObjectNode devList = jsonMapper.createObjectNode();
                ArrayNode devices = devList.putArray("pairedDevices");
                ObjectNode d1 = devices.addObject();
                d1.put("name", "users/" + effectiveUser + "/pairedDevices/pixel-watch-3");
                d1.put("deviceType", "SMARTWATCH");
                d1.put("manufacturer", "Google");
                d1.put("model", "Pixel Watch 3");
                responseBody = devList.toPrettyString();

            } else if (path.contains("/subscriptions")) {
                if ("POST".equalsIgnoreCase(httpMethod)) {
                    ObjectNode sub = jsonMapper.createObjectNode();
                    sub.put("name", "projects/test-project/subscribers/test-sub/subscriptions/" + UUID.randomUUID());
                    sub.put("user", "users/" + effectiveUser);
                    responseBody = sub.toPrettyString();
                    statusCode = 201;
                    statusMessage = "Created";
                } else {
                    ObjectNode subList = jsonMapper.createObjectNode();
                    ArrayNode subs = subList.putArray("subscriptions");
                    ObjectNode s1 = subs.addObject();
                    s1.put("name", "projects/test-project/subscribers/test-sub/subscriptions/sub-001");
                    s1.put("user", "users/" + effectiveUser);
                    responseBody = subList.toPrettyString();
                }

            } else if (path.contains(":batchDelete")) {
                ObjectNode op = jsonMapper.createObjectNode();
                op.put("name", "operations/batch-delete-" + System.currentTimeMillis());
                op.put("done", true);
                responseBody = op.toPrettyString();

            } else if (path.contains(":rollUp") || path.contains(":dailyRollUp")) {
                ObjectNode rollup = jsonMapper.createObjectNode();
                ArrayNode dataPoints = rollup.putArray("dataPoints");
                ObjectNode dp = dataPoints.addObject();
                dp.put("startTime", Instant.now().minus(1, ChronoUnit.DAYS).toString());
                dp.put("endTime", Instant.now().toString());
                if (def != null) {
                    double sampleVal = def.getMinValue() != null && def.getMaxValue() != null ?
                            (def.getMinValue() + def.getMaxValue()) / 2.0 : 100.0;
                    ObjectNode typeNode = dp.putObject(toCamelCase(def.getName()));
                    typeNode.put("value", sampleVal);
                }
                responseBody = rollup.toPrettyString();

            } else if ("POST".equalsIgnoreCase(httpMethod)) {
                // Create DataPoint
                if (requestBody != null && !requestBody.trim().isEmpty()) {
                    responseBody = requestBody;
                } else {
                    responseBody = generateSampleDataPointJson(def, effectiveUser);
                }
                statusCode = 201;
                statusMessage = "Created";

            } else {
                // List or Get DataPoints
                if (path.matches(".*/dataPoints/[^/]+$")) {
                    // Single DataPoint
                    responseBody = generateSampleDataPointJson(def, effectiveUser);
                } else {
                    // List DataPoints
                    ObjectNode listResp = jsonMapper.createObjectNode();
                    ArrayNode dataPoints = listResp.putArray("dataPoints");
                    for (int i = 0; i < 3; i++) {
                        dataPoints.add(generateSampleDataPointNode(def, effectiveUser, i));
                    }
                    responseBody = listResp.toPrettyString();
                }
            }
        } catch (Exception e) {
            statusCode = 500;
            statusMessage = "Internal Mock Error: " + e.getMessage();
            responseBody = "{\"error\": \"" + e.getMessage() + "\"}";
        }

        long latency = System.currentTimeMillis() - startTime + 35; // simulate 35ms network latency
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Content-Type", List.of("application/json; charset=UTF-8"));
        headers.put("X-Mock-Backend", List.of("true"));

        String curl = "curl -X " + httpMethod + " \"https://health.googleapis.com" + path + "\" " +
                "-H \"Authorization: Bearer mock_token\" -H \"Content-Type: application/json\"";

        return new ApiResponse(statusCode, statusMessage, headers, responseBody, latency,
                "https://health.googleapis.com" + path, httpMethod, requestBody, curl);
    }

    public JsonNode generateSampleDataPointNode(DataTypeDefinition def, String healthUserId, int index) {
        ObjectNode dp = jsonMapper.createObjectNode();
        String dtName = def != null ? def.getName() : "steps";
        dp.put("name", "users/" + healthUserId + "/dataTypes/" + dtName + "/dataPoints/dp-" + (1000 + index));

        Instant now = Instant.now().minus(index * 30L, ChronoUnit.MINUTES);
        Instant start = now.minus(15, ChronoUnit.MINUTES);

        ObjectNode interval = dp.putObject("interval");
        interval.put("startTime", start.toString());
        interval.put("endTime", now.toString());

        double sampleVal = calculateMockValue(def, index);
        String fieldName = toCamelCase(dtName);
        ObjectNode typeNode = dp.putObject(fieldName);

        if ("steps".equals(dtName) || "floors".equals(dtName)) {
            typeNode.put("count", (long) sampleVal);
        } else if ("heart_rate".equals(dtName) || "daily_resting_heart_rate".equals(dtName)) {
            typeNode.put("beatsPerMinute", (long) sampleVal);
        } else if ("distance".equals(dtName)) {
            typeNode.put("millimeters", (long) sampleVal);
        } else if ("weight".equals(dtName)) {
            typeNode.put("weightGrams", sampleVal);
        } else if ("height".equals(dtName)) {
            typeNode.put("heightMillimeters", sampleVal);
        } else if ("oxygen_saturation".equals(dtName) || "body_fat".equals(dtName)) {
            typeNode.put("percentage", sampleVal);
        } else if ("blood_glucose".equals(dtName)) {
            typeNode.put("bloodGlucoseMilligramsPerDeciliter", sampleVal);
        } else if ("active_energy_burned".equals(dtName) || "basal_energy_burned".equals(dtName)) {
            typeNode.put("kcal", sampleVal);
        } else if ("active_zone_minutes".equals(dtName)) {
            typeNode.put("activeZoneMinutes", (int) sampleVal);
        } else if ("sleep".equals(dtName) || "exercise".equals(dtName) || "mindfulness".equals(dtName)) {
            typeNode.put("durationSeconds", (long) sampleVal);
        } else {
            typeNode.put("value", sampleVal);
        }

        return dp;
    }

    public String generateSampleDataPointJson(DataTypeDefinition def, String healthUserId) {
        return generateSampleDataPointNode(def, healthUserId, 0).toPrettyString();
    }

    private double calculateMockValue(DataTypeDefinition def, int offset) {
        if (def == null) return 100.0;
        double min = def.getMinValue() != null ? def.getMinValue() : 0.0;
        double max = def.getMaxValue() != null ? def.getMaxValue() : 1000.0;

        // Choose a realistic healthy value within min and max
        if ("steps".equals(def.getName())) return 4500.0 + (offset * 150.0);
        if ("heart_rate".equals(def.getName())) return 72.0 + (offset * 2.0);
        if ("distance".equals(def.getName())) return 3200000.0 + (offset * 50000.0); // 3.2 km in mm
        if ("weight".equals(def.getName())) return 75000.0; // 75 kg in grams
        if ("height".equals(def.getName())) return 1780.0; // 178 cm in mm
        if ("oxygen_saturation".equals(def.getName())) return 98.0;
        if ("blood_glucose".equals(def.getName())) return 95.0;
        if ("body_fat".equals(def.getName())) return 18.5;
        if ("active_energy_burned".equals(def.getName())) return 350.0;
        if ("sleep".equals(def.getName())) return 28800.0; // 8 hours in seconds

        return Math.max(min, Math.min(max, (min + max) / 2.0));
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
