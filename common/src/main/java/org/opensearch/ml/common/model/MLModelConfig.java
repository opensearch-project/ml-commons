/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import lombok.Getter;
import lombok.Setter;
import org.opensearch.common.io.stream.NamedWriteable;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.ToXContentObject;

import java.io.IOException;

@Getter
public abstract class MLModelConfig implements ToXContentObject, NamedWriteable {

    public static final String MODEL_TYPE_FIELD = "model_type";
    public static final String ALL_CONFIG_FIELD = "all_config";

    protected String modelType;
    protected String allConfig;

    public MLModelConfig(String modelType, String allConfig) {
        if (modelType == null) {
            throw new IllegalArgumentException("model type is null");
        }
        this.modelType = modelType;
        this.allConfig = allConfig;
    }

    public MLModelConfig(StreamInput in) throws IOException {
        this.modelType = in.readString();
        this.allConfig = in.readOptionalString();
    }

    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(modelType);
        out.writeOptionalString(allConfig);
    }
}
