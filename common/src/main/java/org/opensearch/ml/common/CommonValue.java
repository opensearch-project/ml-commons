/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import java.util.Set;

import org.opensearch.Version;

import com.google.common.collect.ImmutableSet;

public class CommonValue {

    public static Integer NO_SCHEMA_VERSION = 0;
    public static final String REMOTE_SERVICE_ERROR = "Error from remote service: ";
    public static final String USER = "user";
    public static final String META = "_meta";
    public static final String SCHEMA_VERSION_FIELD = "schema_version";
    public static final String UNDEPLOYED = "undeployed";
    public static final String NOT_FOUND = "not_found";

    public static final String MASTER_KEY = "master_key";
    public static final String CREATE_TIME_FIELD = "create_time";
    public static final String LAST_UPDATE_TIME_FIELD = "last_update_time";

    public static final String BOX_TYPE_KEY = "box_type";
    // hot node
    public static String HOT_BOX_TYPE = "hot";
    // warm node
    public static String WARM_BOX_TYPE = "warm";
    public static final String ML_MODEL_GROUP_INDEX = ".plugins-ml-model-group";
    public static final String ML_MODEL_INDEX = ".plugins-ml-model";
    public static final String ML_TASK_INDEX = ".plugins-ml-task";
    public static final String ML_CONNECTOR_INDEX = ".plugins-ml-connector";
    public static final String ML_CONFIG_INDEX = ".plugins-ml-config";
    public static final String ML_CONTROLLER_INDEX = ".plugins-ml-controller";
    public static final String ML_MAP_RESPONSE_KEY = "response";
    public static final String ML_AGENT_INDEX = ".plugins-ml-agent";
    public static final String ML_MEMORY_META_INDEX = ".plugins-ml-memory-meta";
    public static final String ML_MEMORY_MESSAGE_INDEX = ".plugins-ml-memory-message";
    public static final String ML_STOP_WORDS_INDEX = ".plugins-ml-stop-words";
    public static final Set<String> stopWordsIndices = ImmutableSet.of(".plugins-ml-stop-words");

    // Index mapping paths
    public static final String ML_MODEL_GROUP_INDEX_MAPPING_PATH = "index-mappings/ml-model-group.json";
    public static final String ML_MODEL_INDEX_MAPPING_PATH = "index-mappings/ml-model.json";
    public static final String ML_TASK_INDEX_MAPPING_PATH = "index-mappings/ml-task.json";
    public static final String ML_CONNECTOR_INDEX_MAPPING_PATH = "index-mappings/ml-connector.json";
    public static final String ML_CONFIG_INDEX_MAPPING_PATH = "index-mappings/ml-config.json";
    public static final String ML_CONTROLLER_INDEX_MAPPING_PATH = "index-mappings/ml-controller.json";
    public static final String ML_AGENT_INDEX_MAPPING_PATH = "index-mappings/ml-agent.json";
    public static final String ML_MEMORY_META_INDEX_MAPPING_PATH = "index-mappings/ml-memory-meta.json";
    public static final String ML_MEMORY_MESSAGE_INDEX_MAPPING_PATH = "index-mappings/ml-memory-message.json";

    // Calculate Versions independently of OpenSearch core version
    public static final Version VERSION_2_11_0 = Version.fromString("2.11.0");
    public static final Version VERSION_2_12_0 = Version.fromString("2.12.0");
    public static final Version VERSION_2_13_0 = Version.fromString("2.13.0");
    public static final Version VERSION_2_14_0 = Version.fromString("2.14.0");
    public static final Version VERSION_2_15_0 = Version.fromString("2.15.0");
    public static final Version VERSION_2_16_0 = Version.fromString("2.16.0");
    public static final Version VERSION_2_17_0 = Version.fromString("2.17.0");
    public static final Version VERSION_2_18_0 = Version.fromString("2.18.0");
}
