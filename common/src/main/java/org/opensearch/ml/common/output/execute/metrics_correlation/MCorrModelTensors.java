/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.output.execute.metrics_correlation;

import lombok.Builder;
import lombok.Getter;
import org.opensearch.common.bytes.BytesReference;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.output.model.ModelResultFilter;
import org.opensearch.ml.common.output.model.ModelTensor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

@Getter
public class MCorrModelTensors implements Writeable, ToXContentObject {
    public static final String OUTPUT_FIELD = "output";
    private List<MCorrModelTensor> mCorrModelTensors;

    @Builder
    public MCorrModelTensors(List<MCorrModelTensor> mCorrModelTensors) {
        this.mCorrModelTensors = mCorrModelTensors;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (mCorrModelTensors != null && mCorrModelTensors.size() > 0) {
            builder.startArray(OUTPUT_FIELD);
            for (MCorrModelTensor output : mCorrModelTensors) {
                output.toXContent(builder, params);
            }
            builder.endArray();
        }
        builder.endObject();
        return builder;
    }

    public MCorrModelTensors(StreamInput in) throws IOException {
        if (in.readBoolean()) {
            mCorrModelTensors = new ArrayList<>();
            int size = in.readInt();
            for (int i=0; i<size; i++) {
                mCorrModelTensors.add(new MCorrModelTensor(in));
            }
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        if (mCorrModelTensors != null && mCorrModelTensors.size() > 0) {
            out.writeBoolean(true);
            out.writeInt(mCorrModelTensors.size());
            for (MCorrModelTensor output : mCorrModelTensors) {
                output.writeTo(out);
            }
        } else {
            out.writeBoolean(false);
        }
    }

    public void filter(ModelResultFilter resultFilter) {
        boolean returnNumber = resultFilter.isReturnNumber();
        List<String> targetResponse = resultFilter.getTargetResponse();
        List<Integer> targetResponsePositions = resultFilter.getTargetResponsePositions();
        if ((targetResponse == null || targetResponse.size() == 0)
                && (targetResponsePositions == null || targetResponsePositions.size() == 0)) {
            mCorrModelTensors.forEach(output -> filter(output,  returnNumber));
            return;
        }
        List<MCorrModelTensor> targetOutput = new ArrayList<>();
        if (mCorrModelTensors != null) {
            for (int i = 0 ; i<mCorrModelTensors.size(); i++) {
                MCorrModelTensor output = mCorrModelTensors.get(i);
                if (targetResponse != null && targetResponse.contains(output.getRange())) {
                    filter(output,  returnNumber);
                    targetOutput.add(output);
                } else if (targetResponsePositions != null && targetResponsePositions.contains(i)) {
                    filter(output,  returnNumber);
                    targetOutput.add(output);
                }
            }
        }
        this.mCorrModelTensors = targetOutput;
    }

    private void filter(MCorrModelTensor output, boolean returnNUmber) {
        if (!returnNUmber) {
            output.setEvent(null);
            output.setMetrics(null);
            output.setRange(null);
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

    public static MCorrModelTensors fromBytes(byte[] bytes) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        try (StreamInput streamInput = BytesReference.fromByteBuffer(byteBuffer).streamInput()) {
            MCorrModelTensors tensorOutput = new MCorrModelTensors(streamInput);
            return tensorOutput;
        } catch (Exception e) {
            String errorMsg = "Failed to parse output";
            throw new MLException(errorMsg, e);
        }
    }
}
