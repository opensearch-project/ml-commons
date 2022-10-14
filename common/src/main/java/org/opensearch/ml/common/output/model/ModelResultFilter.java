package org.opensearch.ml.common.output.model;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is to filter model results.
 */
@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class ModelResultFilter implements Writeable {

    // Return model output as bytes. This could be useful if client side prefer
    // to parse the model output in its own way.
    protected boolean returnBytes;

    // Return model output as number types directly. For example float/double.
    protected boolean returnNumber;

    // Target response name which will return.
    // If it's null, will return all responses.
    protected List<String> targetResponse;

    // Target response position which will return. If model output doesn't have
    // name, user should use this to filter result.
    // If it's null, will return all responses.
    protected List<Integer> targetResponsePositions;

    @Builder
    public ModelResultFilter(boolean returnBytes,
                             boolean returnNumber,
                             List<String> targetResponse,
                             List<Integer> targetResponsePositions
    ) {
        this.returnBytes = returnBytes;
        this.returnNumber = returnNumber;
        this.targetResponse = targetResponse;
        this.targetResponsePositions = targetResponsePositions;
    }

    public ModelResultFilter(StreamInput streamInput) throws IOException {
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
