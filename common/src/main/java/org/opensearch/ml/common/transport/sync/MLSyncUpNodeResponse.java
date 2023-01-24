/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.sync;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.opensearch.action.support.nodes.BaseNodeResponse;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;

import java.io.IOException;
import java.util.Map;

@Log4j2
@Getter
public class MLSyncUpNodeResponse extends BaseNodeResponse  {

    private String modelStatus;
    private String[] loadedModelIds;
    private String[] runningLoadModelIds; // model ids which have loading model task running
    private String[] runningLoadModelTaskIds; // load model task ids which is running

    public MLSyncUpNodeResponse(DiscoveryNode node, String modelStatus, String[] loadedModelIds, String[] runningLoadModelIds,
                                String[] runningLoadModelTaskIds) {
        super(node);
        this.modelStatus = modelStatus;
        this.loadedModelIds = loadedModelIds;
        this.runningLoadModelIds = runningLoadModelIds;
        this.runningLoadModelTaskIds = runningLoadModelTaskIds;
    }

    public MLSyncUpNodeResponse(StreamInput in) throws IOException {
        super(in);
        this.modelStatus = in.readOptionalString();
        this.loadedModelIds = in.readOptionalStringArray();
        this.runningLoadModelIds = in.readOptionalStringArray();
        this.runningLoadModelTaskIds = in.readOptionalStringArray();
    }

    public static MLSyncUpNodeResponse readStats(StreamInput in) throws IOException {
        return new MLSyncUpNodeResponse(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeOptionalString(modelStatus);
        out.writeOptionalStringArray(loadedModelIds);
        out.writeOptionalStringArray(runningLoadModelIds);
        out.writeOptionalStringArray(runningLoadModelTaskIds);
    }

}
