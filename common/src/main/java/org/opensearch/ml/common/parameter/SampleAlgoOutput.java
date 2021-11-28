package org.opensearch.ml.common.parameter;

import lombok.Builder;
import lombok.Getter;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.ml.common.annotation.MLAlgoOutput;

import java.io.IOException;

@Getter
@MLAlgoOutput(MLOutputType.SAMPLE_ALGO)
public class SampleAlgoOutput extends MLOutput{

    private static final MLOutputType OUTPUT_TYPE = MLOutputType.SAMPLE_ALGO;
    public static final String SAMPLE_RESULT_FIELD = "sample_result";
    private Double sampleResult;

    @Builder
    public SampleAlgoOutput(Double sampleResult) {
        super(OUTPUT_TYPE);
        this.sampleResult = sampleResult;
    }

    public SampleAlgoOutput(StreamInput in) throws IOException {
        super(OUTPUT_TYPE);
        sampleResult = in.readOptionalDouble();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeOptionalDouble(sampleResult);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(SAMPLE_RESULT_FIELD, sampleResult);
        builder.endObject();
        return builder;
    }

    @Override
    public MLOutputType getType() {
        return OUTPUT_TYPE;
    }
}
