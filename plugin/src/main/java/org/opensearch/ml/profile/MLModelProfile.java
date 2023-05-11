/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.profile;

import java.io.IOException;

import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentFragment;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.model.MLModelState;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Getter
@Log4j2
public class MLModelProfile implements ToXContentFragment, Writeable {

    private final MLModelState modelState;
    private final String predictor;
    private final String[] targetWorkerNodes;
    private final String[] workerNodes;
    private final MLPredictRequestStats modelInferenceStats;
    private final MLPredictRequestStats predictRequestStats;

    @Builder
    public MLModelProfile(
        MLModelState modelState,
        String predictor,
        String[] targetWorkerNodes,
        String[] workerNodes,
        MLPredictRequestStats modelInferenceStats,
        MLPredictRequestStats predictRequestStats
    ) {
        this.modelState = modelState;
        this.predictor = predictor;
        this.targetWorkerNodes = targetWorkerNodes;
        this.workerNodes = workerNodes;
        this.modelInferenceStats = modelInferenceStats;
        this.predictRequestStats = predictRequestStats;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (modelState != null) {
            builder.field("model_state", modelState);
        }
        if (predictor != null) {
            builder.field("predictor", predictor);
        }
        if (targetWorkerNodes != null) {
            builder.field("target_worker_nodes", targetWorkerNodes);
        }
        if (workerNodes != null) {
            builder.field("worker_nodes", workerNodes);
        }
        if (modelInferenceStats != null) {
            builder.field("model_inference_stats", modelInferenceStats);
        }
        if (predictRequestStats != null) {
            builder.field("predict_request_stats", predictRequestStats);
        }
        builder.endObject();
        return builder;
    }

    public MLModelProfile(StreamInput in) throws IOException {
        if (in.readBoolean()) {
            this.modelState = in.readEnum(MLModelState.class);
        } else {
            this.modelState = null;
        }
        this.predictor = in.readOptionalString();
        this.targetWorkerNodes = in.readOptionalStringArray();
        this.workerNodes = in.readOptionalStringArray();
        if (in.readBoolean()) {
            this.modelInferenceStats = new MLPredictRequestStats(in);
        } else {
            this.modelInferenceStats = null;
        }
        if (in.readBoolean()) {
            this.predictRequestStats = new MLPredictRequestStats(in);
        } else {
            this.predictRequestStats = null;
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        if (this.modelState != null) {
            out.writeBoolean(true);
            out.writeEnum(modelState);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalString(predictor);
        out.writeOptionalStringArray(targetWorkerNodes);
        out.writeOptionalStringArray(workerNodes);
        if (modelInferenceStats != null) {
            out.writeBoolean(true);
            modelInferenceStats.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        if (predictRequestStats != null) {
            out.writeBoolean(true);
            predictRequestStats.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
    }
}
