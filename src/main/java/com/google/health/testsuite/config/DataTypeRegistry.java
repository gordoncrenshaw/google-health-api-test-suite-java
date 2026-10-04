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

    public List<String> getDataTypeNames() {
        return new ArrayList<>(dataTypesMap.keySet());
    }

    public File getDataTypesFile() {
        return dataTypesFile;
    }
}
