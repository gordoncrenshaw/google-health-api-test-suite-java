package com.google.health.testsuite;

import com.google.health.testsuite.config.DataTypeRegistry;
import com.google.health.testsuite.model.DataTypeDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class DataTypeRegistryTest {

    private DataTypeRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DataTypeRegistry(new File("config/datatypes.yaml"));
    }

    @Test
    void testLoadDataTypes() {
        List<DataTypeDefinition> list = registry.getAllDataTypes();
        assertNotNull(list);
        assertFalse(list.isEmpty(), "Data types list should not be empty");
        assertTrue(list.size() >= 10, "Should have at least 10 Google Health data types registered");
    }

    @Test
    void testStepsDataTypeDefinition() {
        Optional<DataTypeDefinition> stepsOpt = registry.getDataType("steps");
        assertTrue(stepsOpt.isPresent(), "steps data type must exist in registry");

        DataTypeDefinition steps = stepsOpt.get();
        assertEquals("steps", steps.getName());
        assertEquals("https://www.googleapis.com/auth/googlehealth.activity_and_fitness.readonly", steps.getScopeRequired());
        assertTrue(steps.isWebhooksSupported(), "steps must support webhooks");
        assertEquals(0.0, steps.getMinValue());
        assertEquals(1000000.0, steps.getMaxValue());
        assertTrue(steps.supportsEndpoint("list"));
        assertTrue(steps.supportsEndpoint("create"));
        assertTrue(steps.supportsEndpoint("rollUp"));
        assertTrue(steps.isWithinRange(5000));
        assertFalse(steps.isWithinRange(-1));
        assertFalse(steps.isWithinRange(1000001));
    }

    @Test
    void testHeartRateDataTypeDefinition() {
        Optional<DataTypeDefinition> hrOpt = registry.getDataType("heart_rate");
        assertTrue(hrOpt.isPresent(), "heart_rate data type must exist in registry");

        DataTypeDefinition hr = hrOpt.get();
        assertEquals(1.0, hr.getMinValue());
        assertEquals(300.0, hr.getMaxValue());
        assertEquals("bpm", hr.getUnit());
        assertTrue(hr.isWithinRange(75));
        assertFalse(hr.isWithinRange(0));
        assertFalse(hr.isWithinRange(350));
    }

    @Test
    void testWebhooksSupportedDataTypes() {
        List<DataTypeDefinition> webhooksTypes = registry.getWebhooksSupportedDataTypes();
        assertFalse(webhooksTypes.isEmpty());
        // Verify steps, distance, floors, weight, sleep, altitude support webhooks
        assertTrue(webhooksTypes.stream().anyMatch(d -> d.getName().equals("steps")));
        assertTrue(webhooksTypes.stream().anyMatch(d -> d.getName().equals("weight")));
        assertTrue(webhooksTypes.stream().anyMatch(d -> d.getName().equals("distance")));
        assertTrue(webhooksTypes.stream().anyMatch(d -> d.getName().equals("sleep")));
    }
}
