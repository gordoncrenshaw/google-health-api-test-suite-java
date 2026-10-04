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
        assertEquals("v4", steps.getEndpointVersion(), "steps endpoint version must be v4");
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
        assertEquals("v4", hr.getEndpointVersion(), "heart_rate endpoint version must be v4");
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

    @Test
    void testAllDataTypesHaveEndpointVersion() {
        List<DataTypeDefinition> all = registry.getAllDataTypes();
        assertFalse(all.isEmpty());
        for (DataTypeDefinition dt : all) {
            assertNotNull(dt.getEndpointVersion(), "DataType " + dt.getName() + " must have endpointVersion");
            assertFalse(dt.getEndpointVersion().trim().isEmpty(), "DataType " + dt.getName() + " endpointVersion must not be empty");
            assertEquals("v4", dt.getEndpointVersion(), "Default endpoint version for " + dt.getName() + " must be v4");
        }
    }

    @Test
    void testAddAdditionalDataType() throws Exception {
        File tempFile = File.createTempFile("datatypes_test", ".yaml");
        java.nio.file.Files.copy(new File("config/datatypes.yaml").toPath(), tempFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        DataTypeRegistry testReg = new DataTypeRegistry(tempFile);
        int initialCount = testReg.getAllDataTypes().size();
        assertFalse(testReg.hasDataType("vo2_max"));

        DataTypeDefinition vo2 = new DataTypeDefinition("vo2_max", "VO2 Max", "v4",
                "https://www.googleapis.com/auth/googlehealth.activity_and_fitness.readonly",
                List.of("list", "get", "create", "batchDelete"),
                "vo2_max.sample_time.physical_time", true, 10.0, 95.0, "mL/kg/min");

        boolean added = testReg.addDataType(vo2);
        assertTrue(added, "addDataType should succeed for new definition");
        assertEquals(initialCount + 1, testReg.getAllDataTypes().size());
        assertTrue(testReg.hasDataType("vo2_max"));

        Optional<DataTypeDefinition> fetched = testReg.getDataType("vo2_max");
        assertTrue(fetched.isPresent());
        assertEquals("VO2 Max", fetched.get().getDisplayName());
        assertEquals("v4", fetched.get().getEndpointVersion());
        assertEquals("mL/kg/min", fetched.get().getUnit());
        assertTrue(fetched.get().isWebhooksSupported());

        // Test rejecting duplicate
        boolean duplicateAdded = testReg.addDataType(vo2);
        assertFalse(duplicateAdded, "addDataType should reject duplicate data type identifier");

        // Verify fresh registry instance reading tempFile sees the persisted data type
        DataTypeRegistry reloadedReg = new DataTypeRegistry(tempFile);
        assertTrue(reloadedReg.hasDataType("vo2_max"));
        assertEquals(initialCount + 1, reloadedReg.getAllDataTypes().size());

        tempFile.deleteOnExit();
    }
}
