/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.output.custom_model;

import lombok.Getter;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.common.xcontent.ToXContentObject;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.ml.common.dataset.MLModelResultFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Getter
public class MLModelTensorOutput implements Writeable, ToXContentObject {
    private List<MLModelTensor> mlModelTensors;

    public MLModelTensorOutput(List<MLModelTensor> mlModelTensors) {
        this.mlModelTensors = mlModelTensors;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (mlModelTensors != null && mlModelTensors.size() > 0) {
            builder.startArray("modelTensorOutputs");
            for (MLModelTensor output : mlModelTensors) {
                output.toXContent(builder, params);
            }
            builder.endArray();
        }
        builder.endObject();
        return builder;
    }

    public MLModelTensorOutput(StreamInput in) throws IOException {
        if (in.readBoolean()) {
            mlModelTensors = new ArrayList<>();
            int size = in.readInt();
            for (int i=0; i<size; i++) {
                mlModelTensors.add(new MLModelTensor(in));
            }
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        if (mlModelTensors != null && mlModelTensors.size() > 0) {
            out.writeBoolean(true);
            out.writeInt(mlModelTensors.size());
            for (MLModelTensor output : mlModelTensors) {
                output.writeTo(out);
            }
        } else {
            out.writeBoolean(false);
        }
    }

    public void filter(MLModelResultFilter resultFilter) {
        boolean returnBytes = resultFilter.isReturnBytes();
        boolean returnNUmber = resultFilter.isReturnNumber();
        List<String> targetResponse = resultFilter.getTargetResponse();
        List<Integer> targetResponsePositions = resultFilter.getTargetResponsePositions();
        if ((targetResponse == null || targetResponse.size() == 0)
                && (targetResponsePositions == null || targetResponsePositions.size() == 0)) {
            return;
        }
        List<MLModelTensor> targetOutput = new ArrayList<>();
        if (mlModelTensors != null) {
            for (int i = 0 ; i<mlModelTensors.size(); i++) {
                MLModelTensor output = mlModelTensors.get(i);
                if (targetResponse != null && targetResponse.contains(output.getName())) {
                    filter(returnBytes, output, returnNUmber);
                    targetOutput.add(output);
                } else if (targetResponsePositions != null && targetResponsePositions.contains(i)) {
                    filter(returnBytes, output, returnNUmber);
                    targetOutput.add(output);
                }
            }
        }
        this.mlModelTensors = targetOutput;
    }

    private void filter(boolean returnBytes, MLModelTensor output, boolean returnNUmber) {
        if (!returnBytes) {
            output.setByteBuffer(null);
        }
        if (!returnNUmber) {
            output.setData(null);
        }
    }
}
