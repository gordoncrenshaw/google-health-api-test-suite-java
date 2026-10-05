package com.google.health.testsuite;

import com.google.health.testsuite.client.ValidationEngine;
import com.google.health.testsuite.model.DataTypeDefinition;
import com.google.health.testsuite.model.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ValidationEngineTest {

    private ValidationEngine validationEngine;
    private DataTypeDefinition stepsDef;
    private DataTypeDefinition heartRateDef;

    @BeforeEach
    void setUp() {
        validationEngine = new ValidationEngine();
        stepsDef = new DataTypeDefinition("steps", "Steps", "scope.fitness",
                List.of("list", "create"), "steps.interval.start_time", true,
                0.0, 1000000.0, "count");
        stepsDef.setSampleValueField("steps.count");

        heartRateDef = new DataTypeDefinition("heart-rate", "Heart Rate", "scope.metrics",
                List.of("list", "create"), "heart_rate.sample_time.physical_time", false,
                1.0, 300.0, "bpm");
        heartRateDef.setSampleValueField("heartRate.beatsPerMinute");
    }

    @Test
    void testValidateStepsWithinRange() {
        String json = """
                {
                  "dataPoints": [
                    {
                      "name": "users/me/dataTypes/steps/dataPoints/1",
                      "steps": { "count": 7500 }
                    },
                    {
                      "name": "users/me/dataTypes/steps/dataPoints/2",
                      "steps": { "count": 12000 }
                    }
                  ]
                }
                """;

        ValidationResult result = validationEngine.validate(stepsDef, json);
        assertTrue(result.isValid(), "Validation should pass for valid step counts");
        assertEquals(7500.0, result.getExtractedValue());
    }

    @Test
    void testValidateStepsOutOfRange() {
        String json = """
                {
                  "dataPoints": [
                    {
                      "name": "users/me/dataTypes/steps/dataPoints/1",
                      "steps": { "count": -50 }
                    }
                  ]
                }
                """;

        ValidationResult result = validationEngine.validate(stepsDef, json);
        assertFalse(result.isValid(), "Validation should fail for negative step count");
        assertEquals(-50.0, result.getExtractedValue());
    }

    @Test
    void testValidateHeartRateWithinRange() {
        String json = """
                {
                  "name": "users/me/dataTypes/heart-rate/dataPoints/dp-1",
                  "heartRate": { "beatsPerMinute": 72 }
                }
                """;

        ValidationResult result = validationEngine.validate(heartRateDef, json);
        assertTrue(result.isValid(), "Validation should pass for 72 bpm");
    }

    @Test
    void testValidateHeartRateAboveMax() {
        String json = """
                {
                  "name": "users/me/dataTypes/heart-rate/dataPoints/dp-1",
                  "heartRate": { "beatsPerMinute": 350 }
                }
                """;

        ValidationResult result = validationEngine.validate(heartRateDef, json);
        assertFalse(result.isValid(), "Validation should fail for 350 bpm (max 300)");
    }

    @Test
    void testValidateWithoutMinMaxConstraints() {
        DataTypeDefinition unconstrained = new DataTypeDefinition("skin-temperature-sensors",
                "Skin Temperature Sensors", "v4beta", null,
                List.of("list"), "skin_temperature_sensors", false, null, null, "celcius");
        unconstrained.setSampleValueField("skinTemperatureSensor.sensorData.temperatureCelsius");

        String json = """
                {
                  "dataPoints": [
                    {
                      "name": "users/me/dataTypes/skin-temperature-sensors/dataPoints/1",
                      "skinTemperatureSensor": {
                        "sensorData": {
                          "temperatureCelsius": 36.6
                        }
                      }
                    }
                  ]
                }
                """;

        ValidationResult result = validationEngine.validate(unconstrained, json);
        assertTrue(result.isValid(), "Validation must not fail when minValue and maxValue are not listed in the definition");
        assertTrue(result.getMessage().contains("passed") || result.getMessage().contains("Valid"));
    }
}
