package com.google.health.testsuite.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.health.testsuite.model.DataTypeDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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

    public final synchronized void load() {
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
        return getDataType(name, false);
    }

    public Optional<DataTypeDefinition> getDataType(String name, boolean enableAllEndpoints) {
        if (name == null) {
            return Optional.empty();
        }
        String clean = name.trim().toLowerCase();
        DataTypeDefinition def = dataTypesMap.get(clean);
        if (def == null && clean.contains("_")) {
            def = dataTypesMap.get(clean.replace('_', '-'));
        }
        if (def == null && clean.contains("-")) {
            def = dataTypesMap.get(clean.replace('-', '_'));
        }
        if (def != null && enableAllEndpoints) {
            return Optional.of(def.withAllEndpointsEnabled());
        }
        return Optional.ofNullable(def);
    }

    public List<DataTypeDefinition> getAllDataTypes() {
        return getAllDataTypes(false);
    }

    public List<DataTypeDefinition> getAllDataTypes(boolean enableAllEndpoints) {
        List<DataTypeDefinition> list = new ArrayList<>();
        if (!enableAllEndpoints) {
            list.addAll(dataTypesMap.values());
        } else {
            for (DataTypeDefinition def : dataTypesMap.values()) {
                list.add(def.withAllEndpointsEnabled());
            }
        }
        list.sort(Comparator.comparing(d -> d.getName() != null ? d.getName() : "", String.CASE_INSENSITIVE_ORDER));
        return list;
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
        return name != null && getDataType(name).isPresent();
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

        dataTypesMap.put(cleanName, newDef);
        boolean ok = saveAll();
        if (!ok) {
            load();
            return false;
        }
        return true;
    }

    public synchronized boolean updateDataType(DataTypeDefinition updatedDef) {
        if (updatedDef == null || updatedDef.getName() == null || updatedDef.getName().trim().isEmpty()) {
            return false;
        }
        String cleanName = updatedDef.getName().trim().toLowerCase();
        if (!dataTypesMap.containsKey(cleanName)) {
            logger.warn("Data type '{}' does not exist in registry.", cleanName);
            return false;
        }

        dataTypesMap.put(cleanName, updatedDef);
        boolean ok = saveAll();
        if (!ok) {
            load();
            return false;
        }
        return true;
    }

    public synchronized boolean deleteDataType(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        String cleanName = name.trim().toLowerCase();
        if (!dataTypesMap.containsKey(cleanName)) {
            logger.warn("Data type '{}' does not exist in registry.", cleanName);
            return false;
        }

        dataTypesMap.remove(cleanName);
        boolean ok = saveAll();
        if (!ok) {
            load();
            return false;
        }
        return true;
    }

    public synchronized boolean saveAll() {
        if (dataTypesFile == null) return false;
        try {
            String header = null;
            if (dataTypesFile.exists()) {
                String existing = Files.readString(dataTypesFile.toPath(), StandardCharsets.UTF_8);
                int idx = existing.indexOf("dataTypes:");
                if (idx != -1) {
                    header = existing.substring(0, idx + "dataTypes:".length());
                }
            }
            if (header == null || header.trim().isEmpty()) {
                header = "# ==============================================================================\n" +
                         "# Google Health API - Supported Data Types Definition\n" +
                         "# ==============================================================================\n\n" +
                         "version: \"1.0\"\n" +
                         "dataTypes:";
            }

            StringBuilder sb = new StringBuilder(header.stripTrailing());
            sb.append("\n");

            List<DataTypeDefinition> sorted = new ArrayList<>(dataTypesMap.values());
            sorted.sort(Comparator.comparing(d -> d.getName() != null ? d.getName() : "", String.CASE_INSENSITIVE_ORDER));

            for (DataTypeDefinition def : sorted) {
                serializeDefinition(def, sb);
            }

            File tempFile = new File(dataTypesFile.getAbsolutePath() + ".tmp");
            Files.writeString(tempFile.toPath(), sb.toString(), StandardCharsets.UTF_8);
            Files.move(tempFile.toPath(), dataTypesFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            load();
            return true;
        } catch (IOException e) {
            logger.error("Failed to save data types to {}: {}", dataTypesFile.getAbsolutePath(), e.getMessage(), e);
            return false;
        }
    }

    private void serializeDefinition(DataTypeDefinition def, StringBuilder sb) {
        String cleanName = def.getName() != null ? def.getName().trim().toLowerCase() : "";
        sb.append("\n  - name: \"").append(escapeYaml(cleanName)).append("\"\n");
        sb.append("    displayName: \"").append(escapeYaml(def.getDisplayName())).append("\"\n");
        sb.append("    endpointVersion: \"").append(escapeYaml(def.getEndpointVersion() != null ? def.getEndpointVersion() : "v4")).append("\"\n");
        if (def.getScopeRequired() != null && !def.getScopeRequired().trim().isEmpty()) {
            sb.append("    scopeRequired: \"").append(escapeYaml(def.getScopeRequired().trim())).append("\"\n");
        }
        if (def.getWriteScopeRequired() != null && !def.getWriteScopeRequired().trim().isEmpty()) {
            sb.append("    writeScopeRequired: \"").append(escapeYaml(def.getWriteScopeRequired().trim())).append("\"\n");
        }
        sb.append("    endpointsSupported:\n");
        Map<String, Boolean> epMap = def.getEndpointsSupported();
        if (epMap == null || epMap.isEmpty()) {
            epMap = DataTypeDefinition.createDefaultEndpointsMap();
            epMap.put("list", true);
            epMap.put("get", true);
        }
        for (String ep : DataTypeDefinition.ALL_ENDPOINTS) {
            boolean supported = Boolean.TRUE.equals(epMap.get(ep));
            sb.append("      ").append(ep).append(": ").append(supported).append("\n");
        }
        for (Map.Entry<String, Boolean> entry : epMap.entrySet()) {
            if (!DataTypeDefinition.ALL_ENDPOINTS.contains(entry.getKey())) {
                sb.append("      ").append(entry.getKey()).append(": ").append(Boolean.TRUE.equals(entry.getValue())).append("\n");
            }
        }
        if (def.getFilterParameterName() != null && !def.getFilterParameterName().trim().isEmpty()) {
            sb.append("    filterParameterName: \"").append(escapeYaml(def.getFilterParameterName().trim())).append("\"\n");
        }
        sb.append("    webhooksSupported: ").append(def.isWebhooksSupported()).append("\n");
        if (def.getMinValue() != null) {
            sb.append("    minValue: ").append(formatNumber(def.getMinValue())).append("\n");
        }
        if (def.getMaxValue() != null) {
            sb.append("    maxValue: ").append(formatNumber(def.getMaxValue())).append("\n");
        }
        if (def.getUnit() != null && !def.getUnit().trim().isEmpty()) {
            sb.append("    unit: \"").append(escapeYaml(def.getUnit().trim())).append("\"\n");
        }
        if (def.getSampleValueField() != null && !def.getSampleValueField().trim().isEmpty()) {
            sb.append("    sampleValueField: \"").append(escapeYaml(def.getSampleValueField().trim())).append("\"\n");
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
