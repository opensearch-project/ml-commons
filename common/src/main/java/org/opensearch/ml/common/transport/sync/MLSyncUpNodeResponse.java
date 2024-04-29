/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.sync;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.opensearch.Version;
import org.opensearch.action.support.nodes.BaseNodeResponse;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.model.MLDeploySetting;

import java.io.IOException;

@Log4j2
@Getter
public class MLSyncUpNodeResponse extends BaseNodeResponse  {

    private String modelStatus;
    private String[] deployedModelIds;
    private String[] runningDeployModelIds; // model ids which have deploying model task running
    private String[] runningDeployModelTaskIds; // deploy model task ids which is running
    private String[] expiredModelIds;

    public MLSyncUpNodeResponse(DiscoveryNode node, String modelStatus, String[] deployedModelIds, String[] runningDeployModelIds,
                                String[] runningDeployModelTaskIds, String[] expiredModelIds) {
        super(node);
        this.modelStatus = modelStatus;
        this.deployedModelIds = deployedModelIds;
        this.runningDeployModelIds = runningDeployModelIds;
        this.runningDeployModelTaskIds = runningDeployModelTaskIds;
        this.expiredModelIds = expiredModelIds;
    }

    public MLSyncUpNodeResponse(StreamInput in) throws IOException {
        super(in);
        Version streamInputVersion = in.getVersion();
        this.modelStatus = in.readOptionalString();
        this.deployedModelIds = in.readOptionalStringArray();
        this.runningDeployModelIds = in.readOptionalStringArray();
        this.runningDeployModelTaskIds = in.readOptionalStringArray();
        if (streamInputVersion.onOrAfter(MLDeploySetting.MINIMAL_SUPPORTED_VERSION_FOR_MODEL_TTL)) {
            this.expiredModelIds = in.readOptionalStringArray();
        }
    }

    public static MLSyncUpNodeResponse readStats(StreamInput in) throws IOException {
        return new MLSyncUpNodeResponse(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        Version streamOutputVersion = out.getVersion();
        super.writeTo(out);
        out.writeOptionalString(modelStatus);
        out.writeOptionalStringArray(deployedModelIds);
        out.writeOptionalStringArray(runningDeployModelIds);
        out.writeOptionalStringArray(runningDeployModelTaskIds);
        if (streamOutputVersion.onOrAfter(MLDeploySetting.MINIMAL_SUPPORTED_VERSION_FOR_MODEL_TTL)) {
            out.writeOptionalStringArray(expiredModelIds);
        }
    }
}
