/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.deploy;

import static org.opensearch.ml.common.CommonValue.VERSION_2_19_0;

import java.io.IOException;

import org.opensearch.Version;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.ml.common.MLTask;

import lombok.Builder;
import lombok.Data;

@Data
public class MLDeployModelInput implements Writeable {
    private String modelId;
    private String tenantId;
    private String taskId;
    private String modelContentHash;
    private Integer nodeCount;
    private String coordinatingNodeId;
    private Boolean isDeployToAllNodes;
    private MLTask mlTask;

    public MLDeployModelInput(StreamInput in) throws IOException {
        Version streamInputVersion = in.getVersion();
        this.modelId = in.readString();
        this.taskId = in.readString();
        this.tenantId = streamInputVersion.onOrAfter(VERSION_2_19_0) ? in.readOptionalString() : null;
        this.modelContentHash = in.readOptionalString();
        this.nodeCount = in.readInt();
        this.coordinatingNodeId = in.readString();
        this.isDeployToAllNodes = in.readOptionalBoolean();
        this.mlTask = new MLTask(in);
    }

    @Builder
    public MLDeployModelInput(
        String modelId,
        String taskId,
        String tenantId,
        String modelContentHash,
        Integer nodeCount,
        String coordinatingNodeId,
        Boolean isDeployToAllNodes,
        MLTask mlTask
    ) {
        this.modelId = modelId;
        this.taskId = taskId;
        this.tenantId = tenantId;
        this.modelContentHash = modelContentHash;
        this.nodeCount = nodeCount;
        this.coordinatingNodeId = coordinatingNodeId;
        this.isDeployToAllNodes = isDeployToAllNodes;
        this.mlTask = mlTask;
    }

    public MLDeployModelInput() {}

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        Version streamOutputVersion = out.getVersion();
        out.writeString(modelId);
        out.writeString(taskId);
        if (streamOutputVersion.onOrAfter(VERSION_2_19_0)) {
            out.writeOptionalString(tenantId);
        }
        out.writeOptionalString(modelContentHash);
        out.writeInt(nodeCount);
        out.writeString(coordinatingNodeId);
        out.writeOptionalBoolean(isDeployToAllNodes);
        mlTask.writeTo(out);
    }

}
