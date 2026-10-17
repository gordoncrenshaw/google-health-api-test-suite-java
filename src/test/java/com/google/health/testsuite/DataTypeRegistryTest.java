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
        assertTrue(steps.supportsEndpoint("rollUp"));
        assertTrue(steps.supportsEndpoint("reconcile"));
        assertFalse(steps.supportsEndpoint("create"));
        assertTrue(steps.isWithinRange(5000));
        assertFalse(steps.isWithinRange(-1));
        assertFalse(steps.isWithinRange(1000001));
    }

    @Test
    void testHeartRateDataTypeDefinition() {
        Optional<DataTypeDefinition> hrOpt = registry.getDataType("heart-rate");
        assertTrue(hrOpt.isPresent(), "heart-rate data type must exist in registry");

        DataTypeDefinition hr = hrOpt.get();
        assertEquals("heart-rate", hr.getName());
        assertEquals("v4", hr.getEndpointVersion(), "heart-rate endpoint version must be v4");
        assertEquals(1.0, hr.getMinValue());
        assertEquals(300.0, hr.getMaxValue());
        assertEquals("bpm", hr.getUnit());
        assertTrue(hr.isWithinRange(75));
        assertFalse(hr.isWithinRange(0));
        assertFalse(hr.isWithinRange(350));

        // Backward compatibility fallback
        assertTrue(registry.getDataType("heart_rate").isPresent(), "heart_rate fallback must work");
        assertEquals("https://www.googleapis.com/auth/googlehealth.health_metrics_and_measurements.writeonly", hr.getWriteScopeRequired());
    }

    @Test
    void testDerivedWriteScopeRequired() {
        DataTypeDefinition custom = new DataTypeDefinition("custom", "Custom", "https://www.googleapis.com/auth/googlehealth.sample.readonly",
                List.of("list", "batchDelete"), null, false, 0.0, 100.0, "unit");
        assertEquals("https://www.googleapis.com/auth/googlehealth.sample.writeonly", custom.getWriteScopeRequired());
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
            assertTrue(dt.getEndpointVersion().startsWith("v"), "Endpoint version for " + dt.getName() + " must start with 'v'");
        }
        assertEquals("v4", registry.getDataType("steps").get().getEndpointVersion());
        if (registry.hasDataType("skin-temperature-sensors")) {
            assertEquals("v4beta", registry.getDataType("skin-temperature-sensors").get().getEndpointVersion());
        }
    }

    @Test
    void testAddAdditionalDataType() throws Exception {
        File tempFile = File.createTempFile("datatypes_test", ".yaml");
        java.nio.file.Files.copy(new File("config/datatypes.yaml").toPath(), tempFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        DataTypeRegistry testReg = new DataTypeRegistry(tempFile);
        int initialCount = testReg.getAllDataTypes().size();
        assertFalse(testReg.hasDataType("respiratory_rate"));

        DataTypeDefinition respRate = new DataTypeDefinition("respiratory_rate", "Respiratory Rate", "v4",
                "https://www.googleapis.com/auth/googlehealth.activity_and_fitness.readonly",
                List.of("list", "get", "create", "batchDelete"),
                "respiratory_rate.sample_time.physical_time", true, 10.0, 95.0, "breaths/min");

        boolean added = testReg.addDataType(respRate);
        assertTrue(added, "addDataType should succeed for new definition");
        assertEquals(initialCount + 1, testReg.getAllDataTypes().size());
        assertTrue(testReg.hasDataType("respiratory_rate"));

        Optional<DataTypeDefinition> fetched = testReg.getDataType("respiratory_rate");
        assertTrue(fetched.isPresent());
        assertEquals("Respiratory Rate", fetched.get().getDisplayName());
        assertEquals("v4", fetched.get().getEndpointVersion());
        assertEquals("breaths/min", fetched.get().getUnit());
        assertTrue(fetched.get().isWebhooksSupported());

        // Test rejecting duplicate
        boolean duplicateAdded = testReg.addDataType(respRate);
        assertFalse(duplicateAdded, "addDataType should reject duplicate data type identifier");

        // Verify fresh registry instance reading tempFile sees the persisted data type
        DataTypeRegistry reloadedReg = new DataTypeRegistry(tempFile);
        assertTrue(reloadedReg.hasDataType("respiratory_rate"));
        assertEquals(initialCount + 1, reloadedReg.getAllDataTypes().size());

        // Verify respiratory_rate endpointsSupported is a map listing all endpoints with true/false
        DataTypeDefinition reloadedResp = reloadedReg.getDataType("respiratory_rate").get();
        assertNotNull(reloadedResp.getEndpointsSupported());
        for (String ep : DataTypeDefinition.ALL_ENDPOINTS) {
            assertTrue(reloadedResp.getEndpointsSupported().containsKey(ep), "respiratory_rate endpointsSupported must contain " + ep);
        }
        assertTrue(reloadedResp.getEndpointsSupported().get("list"));
        assertTrue(reloadedResp.getEndpointsSupported().get("get"));
        assertTrue(reloadedResp.getEndpointsSupported().get("create"));
        assertTrue(reloadedResp.getEndpointsSupported().get("batchDelete"));
        assertFalse(reloadedResp.getEndpointsSupported().get("rollUp"));
        assertFalse(reloadedResp.getEndpointsSupported().get("exportExerciseTcx"));
        assertFalse(reloadedResp.getEndpointsSupported().get("reconcile"));

        tempFile.deleteOnExit();
    }

    @Test
    void testEndpointsSupportedMapListsAllEndpointsWithTrueOrFalse() {
        List<DataTypeDefinition> all = registry.getAllDataTypes();
        assertFalse(all.isEmpty());

        for (DataTypeDefinition dt : all) {
            java.util.Map<String, Boolean> epMap = dt.getEndpointsSupported();
            assertNotNull(epMap, "endpointsSupported must not be null for " + dt.getName());
            for (String canonicalEp : DataTypeDefinition.ALL_ENDPOINTS) {
                assertTrue(epMap.containsKey(canonicalEp),
                        "endpointsSupported for " + dt.getName() + " must contain endpoint '" + canonicalEp + "'");
                assertNotNull(epMap.get(canonicalEp),
                        "endpoint '" + canonicalEp + "' for " + dt.getName() + " must have a boolean true/false value");
            }
        }

        // Test specific known endpoint mappings
        DataTypeDefinition steps = registry.getDataType("steps").orElseThrow();
        assertTrue(steps.getEndpointsSupported().get("list"));
        assertFalse(steps.getEndpointsSupported().get("get"));
        assertFalse(steps.getEndpointsSupported().get("create"));
        assertFalse(steps.getEndpointsSupported().get("batchDelete"));
        assertTrue(steps.getEndpointsSupported().get("rollUp"));
        assertTrue(steps.getEndpointsSupported().get("dailyRollUp"));
        assertFalse(steps.getEndpointsSupported().get("exportExerciseTcx"));
        assertTrue(steps.getEndpointsSupported().get("reconcile"));
        assertFalse(steps.getEndpointsSupported().get("patch"));

        DataTypeDefinition exercise = registry.getDataType("exercise").orElseThrow();
        assertTrue(exercise.getEndpointsSupported().get("list"));
        assertTrue(exercise.getEndpointsSupported().get("get"));
        assertTrue(exercise.getEndpointsSupported().get("exportExerciseTcx"));
        assertFalse(exercise.getEndpointsSupported().get("create"));
        assertFalse(exercise.getEndpointsSupported().get("batchDelete"));
        assertFalse(exercise.getEndpointsSupported().get("rollUp"));
        assertFalse(exercise.getEndpointsSupported().get("dailyRollUp"));
        assertFalse(exercise.getEndpointsSupported().get("reconcile"));
        assertFalse(exercise.getEndpointsSupported().get("patch"));

        DataTypeDefinition sleep = registry.getDataType("sleep").orElseThrow();
        assertTrue(sleep.getEndpointsSupported().get("list"));
        assertTrue(sleep.getEndpointsSupported().get("get"));
        assertTrue(sleep.getEndpointsSupported().get("create"));
        assertTrue(sleep.getEndpointsSupported().get("batchDelete"));
        assertTrue(sleep.getEndpointsSupported().get("reconcile"));
        assertFalse(sleep.getEndpointsSupported().get("rollUp"));
        assertFalse(sleep.getEndpointsSupported().get("dailyRollUp"));
        assertFalse(sleep.getEndpointsSupported().get("exportExerciseTcx"));
        assertFalse(sleep.getEndpointsSupported().get("patch"));
    }

    @Test
    void testEnableAllEndpointsOverride() {
        // Normal steps definition does not support patch or exportExerciseTcx
        DataTypeDefinition stepsNormal = registry.getDataType("steps", false).orElseThrow();
        assertFalse(stepsNormal.supportsEndpoint("exportExerciseTcx"));
        assertFalse(stepsNormal.supportsEndpoint("patch"));

        // With enableAllEndpoints = true, all endpoints are enabled
        DataTypeDefinition stepsAll = registry.getDataType("steps", true).orElseThrow();
        for (String ep : DataTypeDefinition.ALL_ENDPOINTS) {
            assertTrue(stepsAll.supportsEndpoint(ep), "All endpoints must be supported when enableAll is true: " + ep);
            assertTrue(stepsAll.getEndpointsSupported().get(ep));
        }

        // Verify getAllDataTypes(true) enables all endpoints across all datatypes
        List<DataTypeDefinition> all = registry.getAllDataTypes(true);
        assertFalse(all.isEmpty());
        for (DataTypeDefinition dt : all) {
            for (String ep : DataTypeDefinition.ALL_ENDPOINTS) {
                assertTrue(dt.supportsEndpoint(ep), dt.getName() + " must support " + ep + " when enableAll is true");
            }
        }
    }

    @Test
    void testNewlyAddedDataTypes() {
        String[] newTypes = new String[]{
                "activity-level", "time-in-heart-rate-zone", "swim-lengths-data",
                "run-vo2-max", "active-minutes", "sedentary-period",
                "total-calories", "vo2-max"
        };

        for (String name : newTypes) {
            assertTrue(registry.hasDataType(name), "Registry must contain data type: " + name);
            DataTypeDefinition def = registry.getDataType(name).orElseThrow();
            assertEquals(name, def.getName());
            assertEquals("v4", def.getEndpointVersion());
            assertEquals("https://www.googleapis.com/auth/googlehealth.activity_and_fitness.readonly", def.getScopeRequired());
            assertEquals("https://www.googleapis.com/auth/googlehealth.activity_and_fitness.writeonly", def.getWriteScopeRequired());
            assertNotNull(def.getFilterParameterName());
            assertTrue(def.getFilterParameterName().contains(name.replace('-', '_')));
            assertNotNull(def.getEndpointsSupported());
            for (String ep : DataTypeDefinition.ALL_ENDPOINTS) {
                assertTrue(def.getEndpointsSupported().containsKey(ep));
            }
        }

        // Test specific fields
        DataTypeDefinition actLevel = registry.getDataType("activity-level").orElseThrow();
        assertEquals("Activity Level", actLevel.getDisplayName());
        assertTrue(actLevel.isWebhooksSupported());

        DataTypeDefinition timeInHrZone = registry.getDataType("time-in-heart-rate-zone").orElseThrow();
        assertEquals("Time In Heart Rate Zone", timeInHrZone.getDisplayName());
        assertEquals(0.0, timeInHrZone.getMinValue());
        assertEquals(86400.0, timeInHrZone.getMaxValue());
        assertEquals("seconds", timeInHrZone.getUnit());
        assertTrue(timeInHrZone.isWebhooksSupported());

        DataTypeDefinition swim = registry.getDataType("swim-lengths-data").orElseThrow();
        assertEquals("Swim Lengths Data", swim.getDisplayName());
        assertFalse(swim.isWebhooksSupported());
        assertEquals("count", swim.getUnit());

        DataTypeDefinition runVo2 = registry.getDataType("run-vo2-max").orElseThrow();
        assertEquals("Run VO2 Max", runVo2.getDisplayName());
        assertEquals(10.0, runVo2.getMinValue());
        assertEquals(100.0, runVo2.getMaxValue());
        assertEquals("ml/kg/min", runVo2.getUnit());
        assertTrue(runVo2.isWebhooksSupported());

        DataTypeDefinition actMin = registry.getDataType("active-minutes").orElseThrow();
        assertEquals("Active Minutes", actMin.getDisplayName());
        assertEquals(0.0, actMin.getMinValue());
        assertEquals(1440.0, actMin.getMaxValue());
        assertEquals("minutes", actMin.getUnit());
        assertFalse(actMin.isWebhooksSupported());

        DataTypeDefinition sed = registry.getDataType("sedentary-period").orElseThrow();
        assertEquals("Sedentary Period", sed.getDisplayName());
        assertTrue(sed.isWebhooksSupported());
    }

    @Test
    void testGetAllDataTypesReturnsAlphabeticallySorted() {
        List<DataTypeDefinition> list = registry.getAllDataTypes();
        assertNotNull(list);
        assertTrue(list.size() > 1);

        String prev = null;
        for (DataTypeDefinition dt : list) {
            String name = dt.getName();
            if (prev != null) {
                assertTrue(name.compareToIgnoreCase(prev) >= 0,
                        "Data types must be sorted by name! Expected '" + name + "' to be >= '" + prev + "'");
            }
            prev = name;
        }
    }

    @Test
    void testUpdateAndDeleteDataTypeInTempFile() throws Exception {
        java.nio.file.Path tempPath = java.nio.file.Files.createTempFile("test_datatypes", ".yaml");
        java.nio.file.Files.copy(new File("config/datatypes.yaml").toPath(), tempPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        DataTypeRegistry tempRegistry = new DataTypeRegistry(tempPath.toFile());
        assertTrue(tempRegistry.hasDataType("steps"));

        // 1. Update an existing data type
        DataTypeDefinition steps = tempRegistry.getDataType("steps").orElseThrow();
        assertEquals("Step Count", steps.getDisplayName());
        steps.setDisplayName("Updated Step Count");
        steps.setUnit("steps_unit");

        boolean updateOk = tempRegistry.updateDataType(steps);
        assertTrue(updateOk, "updateDataType should return true");

        // Verify in-memory and reloaded
        DataTypeDefinition updatedSteps = tempRegistry.getDataType("steps").orElseThrow();
        assertEquals("Updated Step Count", updatedSteps.getDisplayName());
        assertEquals("steps_unit", updatedSteps.getUnit());

        // 2. Add a new temporary data type
        DataTypeDefinition newDt = new DataTypeDefinition("test-custom-dt", "Test Custom", "https://scope/test.readonly",
                List.of("list", "get"), "test.param", true, 0.0, 100.0, "points");
        boolean addOk = tempRegistry.addDataType(newDt);
        assertTrue(addOk, "addDataType should return true");
        assertTrue(tempRegistry.hasDataType("test-custom-dt"));

        // 3. Delete the temporary data type
        boolean deleteOk = tempRegistry.deleteDataType("test-custom-dt");
        assertTrue(deleteOk, "deleteDataType should return true");
        assertFalse(tempRegistry.hasDataType("test-custom-dt"));

        // Verify non-existent update/delete return false
        assertFalse(tempRegistry.updateDataType(newDt));
        assertFalse(tempRegistry.deleteDataType("non-existent-dt"));

        java.nio.file.Files.deleteIfExists(tempPath);
    }
}

