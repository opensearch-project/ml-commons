/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import static org.opensearch.ml.common.CommonValue.VERSION_3_1_0;

import java.io.IOException;
import java.util.Map;

import org.opensearch.core.common.io.stream.NamedWriteable;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public abstract class MLModelConfig implements ToXContentObject, NamedWriteable {

    public static final String MODEL_TYPE_FIELD = "model_type";
    public static final String ALL_CONFIG_FIELD = "all_config";
    public static final String ADDITIONAL_CONFIG_FIELD = "additional_config";

    protected String modelType;
    protected String allConfig;
    protected Map<String, Object> additionalConfig;

    public MLModelConfig(String modelType, String allConfig) {
        if (modelType == null) {
            throw new IllegalArgumentException("model type is null");
        }
        this.modelType = modelType;
        this.allConfig = allConfig;
    }

    public MLModelConfig(String modelType, String allConfig, Map<String, Object> additionalConfig) {
        this(modelType, allConfig);
        this.additionalConfig = additionalConfig;
    }

    public MLModelConfig(StreamInput in) throws IOException {
        this.modelType = in.readString();
        this.allConfig = in.readOptionalString();
        if (in.getVersion().onOrAfter(VERSION_3_1_0)) {
            this.additionalConfig = in.readMap();
        }

    }

    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(modelType);
        out.writeOptionalString(allConfig);
        if (out.getVersion().onOrAfter(VERSION_3_1_0)) {
            out.writeMap(additionalConfig);
        }
    }

    public Map<String, Object> getAdditionalConfig() {
        return this.additionalConfig;
    }

    public void setAdditionalConfig(Map<String, Object> additionalConfig) {
        this.additionalConfig = additionalConfig;
    }
}
