/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.sync;

import lombok.Builder;
import lombok.Data;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

@Data
public class MLSyncUpInput implements Writeable {
    private boolean getLoadedModels;
    // key is model id, value is set of added worker node ids
    private Map<String, String[]> addedWorkerNodes;
    // key is model id, value is set of removed worker node ids
    private Map<String, String[]> removedWorkerNodes;
    // key is model id, value is set of worker node ids
    private Map<String, Set<String>> modelRoutingTable;
    // key is task id, value is set of worker node ids
    private Map<String, Set<String>> runningLoadModelTasks;
    // clear model routing table if no running model in cluster
    private boolean clearRoutingTable;
    // sync running load model tasks
    private boolean syncRunningLoadModelTasks;

    @Builder
    public MLSyncUpInput(boolean getLoadedModels,
                         Map<String, String[]> addedWorkerNodes,
                         Map<String, String[]> removedWorkerNodes,
                         Map<String, Set<String>> modelRoutingTable,
                         Map<String, Set<String>> runningLoadModelTasks,
                         boolean clearRoutingTable,
                         boolean syncRunningLoadModelTasks) {
        this.getLoadedModels = getLoadedModels;
        this.addedWorkerNodes = addedWorkerNodes;
        this.removedWorkerNodes = removedWorkerNodes;
        this.modelRoutingTable = modelRoutingTable;
        this.runningLoadModelTasks = runningLoadModelTasks;
        this.clearRoutingTable = clearRoutingTable;
        this.syncRunningLoadModelTasks = syncRunningLoadModelTasks;
    }

    public MLSyncUpInput(){}

    public MLSyncUpInput(StreamInput in) throws IOException {
        this.getLoadedModels = in.readBoolean();
        if (in.readBoolean()) {
            this.addedWorkerNodes = in.readMap(StreamInput::readString, StreamInput::readStringArray);
        }
        if (in.readBoolean()) {
            this.removedWorkerNodes = in.readMap(StreamInput::readString, StreamInput::readStringArray);
        }
        if (in.readBoolean()) {
            modelRoutingTable = in.readMap(StreamInput::readString, s -> s.readSet(StreamInput::readString));
        }
        if (in.readBoolean()) {
            runningLoadModelTasks = in.readMap(StreamInput::readString, s -> s.readSet(StreamInput::readString));
        }
        this.clearRoutingTable = in.readBoolean();
        this.syncRunningLoadModelTasks = in.readBoolean();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeBoolean(getLoadedModels);
        if (addedWorkerNodes != null && addedWorkerNodes.size() > 0) {
            out.writeBoolean(true);
            out.writeMap(addedWorkerNodes, StreamOutput::writeString, StreamOutput::writeStringArray);
        } else {
            out.writeBoolean(false);
        }
        if (removedWorkerNodes != null && removedWorkerNodes.size() > 0) {
            out.writeBoolean(true);
            out.writeMap(removedWorkerNodes, StreamOutput::writeString, StreamOutput::writeStringArray);
        } else {
            out.writeBoolean(false);
        }
        if (modelRoutingTable != null && modelRoutingTable.size() > 0) {
            out.writeBoolean(true);
            out.writeMap(modelRoutingTable, StreamOutput::writeString, StreamOutput::writeStringCollection);
        } else {
            out.writeBoolean(false);
        }
        if (runningLoadModelTasks != null && runningLoadModelTasks.size() > 0) {
            out.writeBoolean(true);
            out.writeMap(runningLoadModelTasks, StreamOutput::writeString, StreamOutput::writeStringCollection);
        } else {
            out.writeBoolean(false);
        }
        out.writeBoolean(clearRoutingTable);
        out.writeBoolean(syncRunningLoadModelTasks);
    }

}
