/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.output.model;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.exception.MLException;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
public class ModelTensors implements Writeable, ToXContentObject {
    public static final String OUTPUT_FIELD = "output";
    public static final String STATUS_CODE_FIELD = "status_code";
    private List<ModelTensor> mlModelTensors;
    @Setter
    private Integer statusCode;

    @Builder
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
        if (statusCode != null) {
            builder.field(STATUS_CODE_FIELD, statusCode);
        }
        builder.endObject();
        return builder;
    }

    public ModelTensors(StreamInput in) throws IOException {
        if (in.readBoolean()) {
            mlModelTensors = new ArrayList<>();
            int size = in.readInt();
            for (int i = 0; i < size; i++) {
                mlModelTensors.add(new ModelTensor(in));
            }
        }
        statusCode = in.readOptionalInt();
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
        out.writeOptionalInt(statusCode);
    }

    public void filter(ModelResultFilter resultFilter) {
        boolean returnBytes = resultFilter.isReturnBytes();
        boolean returnNumber = resultFilter.isReturnNumber();
        List<String> targetResponse = resultFilter.getTargetResponse();
        List<Integer> targetResponsePositions = resultFilter.getTargetResponsePositions();
        if ((targetResponse == null || targetResponse.size() == 0)
            && (targetResponsePositions == null || targetResponsePositions.size() == 0)) {
            mlModelTensors.forEach(output -> filter(output, returnBytes, returnNumber));
            return;
        }
        List<ModelTensor> targetOutput = new ArrayList<>();
        if (mlModelTensors != null) {
            for (int i = 0; i < mlModelTensors.size(); i++) {
                ModelTensor output = mlModelTensors.get(i);
                if (targetResponse != null && targetResponse.contains(output.getName())) {
                    filter(output, returnBytes, returnNumber);
                    targetOutput.add(output);
                } else if (targetResponsePositions != null && targetResponsePositions.contains(i)) {
                    filter(output, returnBytes, returnNumber);
                    targetOutput.add(output);
                }
            }
        }
        this.mlModelTensors = targetOutput;
    }

    private void filter(ModelTensor output, boolean returnBytes, boolean returnNUmber) {
        if (!returnBytes) {
            output.setByteBuffer(null);
        }
        if (!returnNUmber) {
            output.setData(null);
        }
    }

    public byte[] toBytes() {
        try (BytesStreamOutput bytesStreamOutput = new BytesStreamOutput()) {
            this.writeTo(bytesStreamOutput);
            bytesStreamOutput.flush();
            byte[] bytes = bytesStreamOutput.bytes().toBytesRef().bytes;
            return bytes;
        } catch (Exception e) {
            throw new MLException("Failed to parse result", e);
        }
    }

    public static ModelTensors fromBytes(byte[] bytes) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        try (StreamInput streamInput = BytesReference.fromByteBuffer(byteBuffer).streamInput()) {
            ModelTensors tensorOutput = new ModelTensors(streamInput);
            return tensorOutput;
        } catch (Exception e) {
            String errorMsg = "Failed to parse output";
            throw new MLException(errorMsg, e);
        }
    }
}
