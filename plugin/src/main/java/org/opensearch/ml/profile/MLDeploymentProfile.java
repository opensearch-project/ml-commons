/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.profile;

import java.io.IOException;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

import org.opensearch.common.Strings;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.common.xcontent.ToXContentFragment;
import org.opensearch.common.xcontent.XContentBuilder;

@Getter
@Log4j2
public class MLDeploymentProfile implements ToXContentFragment, Writeable {

    private String modelId;

    private String modelName;

    @Setter
    private List<String> targetNodeIds;

    @Setter
    private List<String> notDeployedNodeIds;

    public MLDeploymentProfile(String modelName, String modelId, List<String> targetNodeIds, List<String> notDeployedNodeIds) {
        this.modelName = modelName;
        this.modelId = modelId;
        this.targetNodeIds = targetNodeIds;
        this.notDeployedNodeIds = notDeployedNodeIds;
    }

    public MLDeploymentProfile(String modelName, String modelId) {
        this.modelName = modelName;
        this.modelId = modelId;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (!Strings.isNullOrEmpty(modelName)) {
            builder.field("model_name", modelName);
        }
        if (targetNodeIds != null && targetNodeIds.size() > 0) {
            builder.field("target_node_ids", targetNodeIds);
        }
        if (notDeployedNodeIds != null && notDeployedNodeIds.size() > 0) {
            builder.field("not_deployed_node_ids", notDeployedNodeIds);
        }
        builder.endObject();
        return builder;
    }

    public MLDeploymentProfile(StreamInput in) throws IOException {
        this.modelName = in.readOptionalString();
        this.targetNodeIds = in.readOptionalStringList();
        this.notDeployedNodeIds = in.readOptionalStringList();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(modelName);
        out.writeOptionalStringCollection(targetNodeIds);
        out.writeOptionalStringCollection(notDeployedNodeIds);
    }
}
