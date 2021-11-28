package org.opensearch.ml.common.parameter;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.common.xcontent.ToXContentObject;

import java.io.IOException;

/**
 * ML output data. Must specify output type and
 */
@RequiredArgsConstructor
public abstract class MLOutput implements ToXContentObject, Writeable {
    @NonNull
    MLOutputType outputType;

    public MLOutput() {

    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeEnum(outputType);
    }

    public static MLOutput fromStream(StreamInput in) throws IOException {
        MLOutput output = null;
        MLOutput.MLOutputType outputType = in.readEnum(MLOutput.MLOutputType.class);
        switch (outputType) {
            case TRAINING:
                output = new MLTrainingOutput(in);
                break;
            case PREDICTION:
                output = new MLPredictionOutput(in);
                break;
            case SAMPLE_ALGO:
                output = new SampleAlgoOutput(in);
                break;
            default:
                break;
        }
        return output;
    }

    public enum MLOutputType {
        TRAINING("TRAINING"),
        PREDICTION("PREDICTION"),
        SAMPLE_ALGO("SAMPLE_ALGO");

        private final String name;

        MLOutputType(String name) {
            this.name = name;
        }

        public String toString() {
            return name;
        }

        public static MLOutputType fromString(String name){
            for(MLOutputType e : MLOutputType.values()){
                if(e.name.equals(name)) return e;
            }
            return null;
        }
    }
}
