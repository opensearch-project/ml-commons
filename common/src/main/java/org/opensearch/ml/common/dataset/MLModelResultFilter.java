/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.dataset;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class MLModelResultFilter implements Writeable {

    public static final String MODEL_TASK_TYPE_FIELD = "model_task_type";

    protected boolean returnBytes;
    protected boolean returnNumber;
    protected List<String> targetResponse;
    protected List<Integer> targetResponsePositions;

    public MLModelResultFilter(boolean returnBytes,
                               boolean returnNumber,
                               List<String> targetResponse,
                               List<Integer> targetResponsePositions
    ) {
        this.returnBytes = returnBytes;
        this.returnNumber = returnNumber;
        this.targetResponse = targetResponse;
        this.targetResponsePositions = targetResponsePositions;
    }

    public MLModelResultFilter(StreamInput streamInput) throws IOException {
        this.returnBytes = streamInput.readBoolean();
        this.returnNumber = streamInput.readBoolean();
        targetResponse = streamInput.readOptionalStringList();
        if (streamInput.readBoolean()) {
            int size = streamInput.readInt();
            targetResponsePositions = new ArrayList<>();
            for (int i=0;i<size;i++) {
                targetResponsePositions.add(streamInput.readInt());
            }
        } else {
            targetResponsePositions = null;
        }
    }

    @Override
    public void writeTo(StreamOutput streamOutput) throws IOException {
        streamOutput.writeBoolean(returnBytes);
        streamOutput.writeBoolean(returnNumber);
        streamOutput.writeOptionalStringCollection(targetResponse);
        if (targetResponsePositions != null && targetResponsePositions.size() > 0) {
            streamOutput.writeBoolean(true);
            streamOutput.writeInt(targetResponsePositions.size());
            for (Integer value : targetResponsePositions) {
                streamOutput.writeInt(value);
            }
        } else {
            streamOutput.writeBoolean(false);
        }
    }
}
