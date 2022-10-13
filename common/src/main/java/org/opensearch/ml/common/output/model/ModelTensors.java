/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.output.model;

import lombok.Getter;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.common.xcontent.ToXContentObject;
import org.opensearch.common.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Getter
public class ModelTensors implements Writeable, ToXContentObject {
    public static final String OUTPUT_FIELD = "output";
    private List<ModelTensor> mlModelTensors;

    public ModelTensors(List<ModelTensor> mlModelTensors) {
        this.mlModelTensors = mlModelTensors;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (mlModelTensors != null && mlModelTensors.size() > 0) {
            builder.startArray(OUTPUT_FIELD);
            for (ModelTensor output : mlModelTensors) {
                output.toXContent(builder, params);
            }
            builder.endArray();
        }
        builder.endObject();
        return builder;
    }

    public ModelTensors(StreamInput in) throws IOException {
        if (in.readBoolean()) {
            mlModelTensors = new ArrayList<>();
            int size = in.readInt();
            for (int i=0; i<size; i++) {
                mlModelTensors.add(new ModelTensor(in));
            }
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        if (mlModelTensors != null && mlModelTensors.size() > 0) {
            out.writeBoolean(true);
            out.writeInt(mlModelTensors.size());
            for (ModelTensor output : mlModelTensors) {
                output.writeTo(out);
            }
        } else {
            out.writeBoolean(false);
        }
    }

    public void filter(ModelResultFilter resultFilter) {
        boolean returnBytes = resultFilter.isReturnBytes();
        boolean returnNUmber = resultFilter.isReturnNumber();
        List<String> targetResponse = resultFilter.getTargetResponse();
        List<Integer> targetResponsePositions = resultFilter.getTargetResponsePositions();
        if ((targetResponse == null || targetResponse.size() == 0)
                && (targetResponsePositions == null || targetResponsePositions.size() == 0)) {
            mlModelTensors.forEach(output -> filter(output, returnBytes,  returnNUmber));
            return;
        }
        List<ModelTensor> targetOutput = new ArrayList<>();
        if (mlModelTensors != null) {
            for (int i = 0 ; i<mlModelTensors.size(); i++) {
                ModelTensor output = mlModelTensors.get(i);
                if (targetResponse != null && targetResponse.contains(output.getName())) {
                    filter(output, returnBytes,  returnNUmber);
                    targetOutput.add(output);
                } else if (targetResponsePositions != null && targetResponsePositions.contains(i)) {
                    filter(output, returnBytes,  returnNUmber);
                    targetOutput.add(output);
                }
            }
        }
        this.mlModelTensors = targetOutput;
    }

    private void filter(ModelTensor output, boolean returnBytes,  boolean returnNUmber) {
        if (!returnBytes) {
            output.setByteBuffer(null);
        }
        if (!returnNUmber) {
            output.setData(null);
        }
    }
}
