package com.google.health.testsuite.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.health.testsuite.model.DataTypeDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Loads, maintains, and provides query methods for Google Health API data types
 * defined in config/datatypes.yaml.
 */
public class DataTypeRegistry {

    private static final Logger logger = LoggerFactory.getLogger(DataTypeRegistry.class);
    private static final String DEFAULT_DATATYPES_FILE = "config/datatypes.yaml";

    private final File dataTypesFile;
    private final ObjectMapper yamlMapper;
    private final Map<String, DataTypeDefinition> dataTypesMap = new LinkedHashMap<>();

    public DataTypeRegistry() {
        this(new File(DEFAULT_DATATYPES_FILE));
    }

    public DataTypeRegistry(File dataTypesFile) {
        this.dataTypesFile = dataTypesFile;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        load();
    }

    public synchronized void load() {
        dataTypesMap.clear();
        if (!dataTypesFile.exists()) {
            logger.warn("Data types configuration file does not exist at: {}", dataTypesFile.getAbsolutePath());
            return;
        }

        try {
            JsonNode root = yamlMapper.readTree(dataTypesFile);
            JsonNode typesNode = root.get("dataTypes");
            if (typesNode != null && typesNode.isArray()) {
                for (JsonNode node : typesNode) {
                    DataTypeDefinition def = yamlMapper.treeToValue(node, DataTypeDefinition.class);
                    if (def != null && def.getName() != null) {
                        dataTypesMap.put(def.getName().toLowerCase(), def);
                    }
                }
            }
            logger.info("Loaded {} Google Health API data types from {}", dataTypesMap.size(), dataTypesFile.getAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to parse data types file {}: {}", dataTypesFile.getAbsolutePath(), e.getMessage(), e);
        }
    }

    public Optional<DataTypeDefinition> getDataType(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(dataTypesMap.get(name.trim().toLowerCase()));
    }

    public List<DataTypeDefinition> getAllDataTypes() {
        return new ArrayList<>(dataTypesMap.values());
    }

    public List<DataTypeDefinition> getWebhooksSupportedDataTypes() {
        List<DataTypeDefinition> list = new ArrayList<>();
        for (DataTypeDefinition def : dataTypesMap.values()) {
            if (def.isWebhooksSupported()) {
                list.add(def);
            }
        }
        return list;
    }

    public boolean hasDataType(String name) {
        return name != null && dataTypesMap.containsKey(name.trim().toLowerCase());
    }

    public synchronized boolean addDataType(DataTypeDefinition newDef) {
        if (newDef == null || newDef.getName() == null || newDef.getName().trim().isEmpty()) {
            return false;
        }
        String cleanName = newDef.getName().trim().toLowerCase();
        if (dataTypesMap.containsKey(cleanName)) {
            logger.warn("Data type '{}' already exists in registry.", cleanName);
            return false;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n  - name: \"").append(escapeYaml(cleanName)).append("\"\n");
        sb.append("    displayName: \"").append(escapeYaml(newDef.getDisplayName())).append("\"\n");
        sb.append("    endpointVersion: \"").append(escapeYaml(newDef.getEndpointVersion())).append("\"\n");
        if (newDef.getScopeRequired() != null && !newDef.getScopeRequired().trim().isEmpty()) {
            sb.append("    scopeRequired: \"").append(escapeYaml(newDef.getScopeRequired().trim())).append("\"\n");
        }
        if (newDef.getWriteScopeRequired() != null && !newDef.getWriteScopeRequired().trim().isEmpty()) {
            sb.append("    writeScopeRequired: \"").append(escapeYaml(newDef.getWriteScopeRequired().trim())).append("\"\n");
        }
        if (newDef.getEndpointsSupported() != null && !newDef.getEndpointsSupported().isEmpty()) {
            sb.append("    endpointsSupported:\n");
            for (String ep : newDef.getEndpointsSupported()) {
                sb.append("      - \"").append(escapeYaml(ep.trim())).append("\"\n");
            }
        } else {
            sb.append("    endpointsSupported:\n");
            sb.append("      - \"list\"\n");
            sb.append("      - \"get\"\n");
        }
        if (newDef.getFilterParameterName() != null && !newDef.getFilterParameterName().trim().isEmpty()) {
            sb.append("    filterParameterName: \"").append(escapeYaml(newDef.getFilterParameterName().trim())).append("\"\n");
        }
        sb.append("    webhooksSupported: ").append(newDef.isWebhooksSupported()).append("\n");
        if (newDef.getMinValue() != null) {
            sb.append("    minValue: ").append(formatNumber(newDef.getMinValue())).append("\n");
        }
        if (newDef.getMaxValue() != null) {
            sb.append("    maxValue: ").append(formatNumber(newDef.getMaxValue())).append("\n");
        }
        if (newDef.getUnit() != null && !newDef.getUnit().trim().isEmpty()) {
            sb.append("    unit: \"").append(escapeYaml(newDef.getUnit().trim())).append("\"\n");
        }
        if (newDef.getSampleValueField() != null && !newDef.getSampleValueField().trim().isEmpty()) {
            sb.append("    sampleValueField: \"").append(escapeYaml(newDef.getSampleValueField().trim())).append("\"\n");
        }

        try {
            java.nio.file.Files.writeString(
                    dataTypesFile.toPath(),
                    sb.toString(),
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND
            );
            load();
            return true;
        } catch (IOException e) {
            logger.error("Failed to append new data type to {}: {}", dataTypesFile.getAbsolutePath(), e.getMessage(), e);
            return false;
        }
    }

    private String escapeYaml(String val) {
        if (val == null) return "";
        return val.replace("\"", "\\\"");
    }

    private String formatNumber(Double val) {
        if (val == null) return "0";
        if (val == Math.floor(val) && !Double.isInfinite(val)) {
            return String.valueOf(val.longValue());
        }
        return String.valueOf(val);
    }

    public List<String> getDataTypeNames() {
        return new ArrayList<>(dataTypesMap.keySet());
    }

    public File getDataTypesFile() {
        return dataTypesFile;
    }
}
