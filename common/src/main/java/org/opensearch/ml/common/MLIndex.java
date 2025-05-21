/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import static org.opensearch.ml.common.CommonValue.*;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.opensearch.ml.common.utils.IndexUtils;

public enum MLIndex {
    MODEL_GROUP(ML_MODEL_GROUP_INDEX, false, ML_MODEL_GROUP_INDEX_MAPPING_PATH),
    MODEL(ML_MODEL_INDEX, false, ML_MODEL_INDEX_MAPPING_PATH),
    TASK(ML_TASK_INDEX, false, ML_TASK_INDEX_MAPPING_PATH),
    CONNECTOR(ML_CONNECTOR_INDEX, false, ML_CONNECTOR_INDEX_MAPPING_PATH),
    CONFIG(ML_CONFIG_INDEX, false, ML_CONFIG_INDEX_MAPPING_PATH),
    CONTROLLER(ML_CONTROLLER_INDEX, false, ML_CONTROLLER_INDEX_MAPPING_PATH),
    AGENT(ML_AGENT_INDEX, false, ML_AGENT_INDEX_MAPPING_PATH),
    MEMORY_META(ML_MEMORY_META_INDEX, false, ML_MEMORY_META_INDEX_MAPPING_PATH),
    MEMORY_MESSAGE(ML_MEMORY_MESSAGE_INDEX, false, ML_MEMORY_MESSAGE_INDEX_MAPPING_PATH),
    MCP_SESSION_MANAGEMENT(MCP_SESSION_MANAGEMENT_INDEX, false, ML_MCP_SESSION_MANAGEMENT_INDEX_MAPPING_PATH),
    MCP_TOOLS(MCP_TOOLS_INDEX, false, ML_MCP_TOOLS_INDEX_MAPPING_PATH);

    private final String indexName;
    // whether we use an alias for the index
    private final boolean alias;
    private final String mapping;
    private final Integer version;

    MLIndex(String name, boolean alias, String mappingPath) {
        this.indexName = name;
        this.alias = alias;
        this.mapping = getMapping(mappingPath);
        this.version = IndexUtils.getVersionFromMapping(this.mapping);
    }

    private String getMapping(String mappingPath) {
        if (mappingPath == null) {
            throw new IllegalArgumentException("Mapping path cannot be null");
        }

        try {
            return IndexUtils.getMappingFromFile(mappingPath);
        } catch (IOException e) {
            // Unchecked exception is thrown since the method is being called within a constructor
            throw new UncheckedIOException("Failed to fetch index mapping from file: " + mappingPath, e);
        }
    }

    public String getIndexName() {
        return indexName;
    }

    public boolean isAlias() {
        return alias;
    }

    public String getMapping() {
        return mapping;
    }

    public Integer getVersion() {
        return version;
    }
}
