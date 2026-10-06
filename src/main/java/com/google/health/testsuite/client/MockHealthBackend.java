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
            if (path.contains("/identity")) {
                ObjectNode identity = jsonMapper.createObjectNode();
                identity.put("name", "users/" + effectiveUser + "/identity");
                identity.put("legacyUserId", "DCFX45");
                identity.put("healthUserId", "8677373576871223311");
                responseBody = identity.toPrettyString();

            } else if (path.contains("/profile")) {
                ObjectNode profile = jsonMapper.createObjectNode();
                profile.put("name", "users/" + effectiveUser + "/profile");
                profile.put("healthUserId", "gh-user-" + UUID.randomUUID().toString().substring(0, 8));
                profile.put("displayName", "Test Health User");
                profile.put("locale", "en-US");
                if ("PATCH".equalsIgnoreCase(httpMethod) && requestBody != null && !requestBody.trim().isEmpty()) {
                    try {
                        JsonNode patchNode = jsonMapper.readTree(requestBody);
                        if (patchNode.has("displayName")) profile.put("displayName", patchNode.path("displayName").asText());
                        if (patchNode.has("locale")) profile.put("locale", patchNode.path("locale").asText());
                    } catch (Exception ignored) {}
                }
                responseBody = profile.toPrettyString();

            } else if (path.contains("/irnProfile")) {
                ObjectNode irn = jsonMapper.createObjectNode();
                irn.put("name", "users/" + effectiveUser + "/irnProfile");
                irn.put("enrollmentStatus", "ENROLLED");
                irn.put("onboardingStatus", "ONBOARDING_COMPLETED");
                irn.put("lastDataAnalyzedTime", Instant.now().minus(2, ChronoUnit.HOURS).toString());
                responseBody = irn.toPrettyString();

            } else if (path.contains("/settings")) {
                ObjectNode settings = jsonMapper.createObjectNode();
                settings.put("name", "users/" + effectiveUser + "/settings");
                settings.put("distanceUnit", "KILOMETERS");
                settings.put("weightUnit", "KILOGRAMS");
                settings.put("heightUnit", "CENTIMETERS");
                settings.put("temperatureUnit", "CELSIUS");
                settings.put("timezone", "America/New_York");
                if ("PATCH".equalsIgnoreCase(httpMethod) && requestBody != null && !requestBody.trim().isEmpty()) {
                    try {
                        JsonNode patchNode = jsonMapper.readTree(requestBody);
                        if (patchNode.has("timezone")) settings.put("timezone", patchNode.path("timezone").asText());
                        if (patchNode.has("timeZone")) settings.put("timezone", patchNode.path("timeZone").asText());
                        if (patchNode.has("temperatureUnit")) settings.put("temperatureUnit", patchNode.path("temperatureUnit").asText());
                        if (patchNode.has("distanceUnit")) settings.put("distanceUnit", patchNode.path("distanceUnit").asText());
                        if (patchNode.has("weightUnit")) settings.put("weightUnit", patchNode.path("weightUnit").asText());
                        if (patchNode.has("heightUnit")) settings.put("heightUnit", patchNode.path("heightUnit").asText());
                    } catch (Exception ignored) {}
                }
                responseBody = settings.toPrettyString();

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

            } else if (path.contains(":reconcile")) {
                ObjectNode reconcile = jsonMapper.createObjectNode();
                reconcile.put("reconcileToken", "rec-token-" + System.currentTimeMillis());
                ArrayNode dataPoints = reconcile.putArray("dataPoints");
                dataPoints.add(generateSampleDataPointNode(def, effectiveUser, 0));
                responseBody = reconcile.toPrettyString();

            } else if (path.contains(":exportExerciseTcx")) {
                responseBody = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<TrainingCenterDatabase xmlns=\"http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2\">\n  <Activities>\n    <Activity Sport=\"Running\">\n      <Id>" + Instant.now().toString() + "</Id>\n    </Activity>\n  </Activities>\n</TrainingCenterDatabase>";

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

            } else if ("PATCH".equalsIgnoreCase(httpMethod)) {
                // Patch DataPoint
                if (requestBody != null && !requestBody.trim().isEmpty()) {
                    responseBody = requestBody;
                } else {
                    responseBody = generateSampleDataPointJson(def, effectiveUser);
                }
                statusCode = 200;
                statusMessage = "OK";

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

        String pathWithQuery = path;
        if (queryParams != null && !queryParams.isEmpty()) {
            boolean first = true;
            StringBuilder qb = new StringBuilder();
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                if (entry.getValue() == null || entry.getValue().trim().isEmpty()) {
                    continue;
                }
                if (first) {
                    qb.append("?");
                    first = false;
                } else {
                    qb.append("&");
                }
                qb.append(entry.getKey()).append("=").append(entry.getValue());
            }
            pathWithQuery = path + qb.toString();
        }

        StringBuilder curlBuilder = new StringBuilder("curl -X ").append(httpMethod)
                .append(" \"https://health.googleapis.com").append(pathWithQuery).append("\" ")
                .append("-H \"Authorization: Bearer mock_token\" -H \"Content-Type: application/json\"");
        if (requestBody != null && !requestBody.trim().isEmpty() && !"GET".equalsIgnoreCase(httpMethod) && !"DELETE".equalsIgnoreCase(httpMethod)) {
            curlBuilder.append(" -d '").append(requestBody.replace("'", "'\\''")).append("'");
        }
        String curl = curlBuilder.toString();

        return new ApiResponse(statusCode, statusMessage, headers, responseBody, latency,
                "https://health.googleapis.com" + pathWithQuery, httpMethod, requestBody, curl);
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

        String normName = dtName != null ? dtName.replace('_', '-') : "";
        if ("steps".equals(normName) || "floors".equals(normName)) {
            typeNode.put("count", (long) sampleVal);
        } else if ("heart-rate".equals(normName) || "daily-resting-heart-rate".equals(normName) || "heart-rate-variability".equals(normName)) {
            typeNode.put("beatsPerMinute", (long) sampleVal);
        } else if ("distance".equals(normName)) {
            typeNode.put("millimeters", (long) sampleVal);
        } else if ("weight".equals(normName)) {
            typeNode.put("weightGrams", sampleVal);
        } else if ("height".equals(normName)) {
            typeNode.put("heightMillimeters", sampleVal);
        } else if ("oxygen-saturation".equals(normName) || "body-fat".equals(normName)) {
            typeNode.put("percentage", sampleVal);
        } else if ("blood-glucose".equals(normName)) {
            typeNode.put("bloodGlucoseMilligramsPerDeciliter", sampleVal);
        } else if ("blood-pressure".equals(normName)) {
            typeNode.put("systolic", (long) sampleVal);
            typeNode.put("diastolic", 80L);
        } else if ("active-energy-burned".equals(normName) || "basal-energy-burned".equals(normName)) {
            typeNode.put("kcal", sampleVal);
        } else if ("active-zone-minutes".equals(normName)) {
            typeNode.put("activeZoneMinutes", (int) sampleVal);
        } else if ("sleep".equals(normName) || "exercise".equals(normName) || "mindfulness".equals(normName)) {
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

        String normDefName = def.getName() != null ? def.getName().replace('_', '-') : "";
        // Choose a realistic healthy value within min and max
        if ("steps".equals(normDefName)) return 4500.0 + (offset * 150.0);
        if ("heart-rate".equals(normDefName)) return 72.0 + (offset * 2.0);
        if ("distance".equals(normDefName)) return 3200000.0 + (offset * 50000.0); // 3.2 km in mm
        if ("weight".equals(normDefName)) return 75000.0; // 75 kg in grams
        if ("height".equals(normDefName)) return 1780.0; // 178 cm in mm
        if ("oxygen-saturation".equals(normDefName)) return 98.0;
        if ("blood-glucose".equals(normDefName)) return 95.0;
        if ("body-fat".equals(normDefName)) return 18.5;
        if ("active-energy-burned".equals(normDefName)) return 350.0;
        if ("sleep".equals(normDefName)) return 28800.0; // 8 hours in seconds

        return Math.max(min, Math.min(max, (min + max) / 2.0));
    }

    private static String toCamelCase(String name) {
        if (name == null || (!name.contains("_") && !name.contains("-"))) {
            return name;
        }
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (char c : name.toCharArray()) {
            if (c == '_' || c == '-') {
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
