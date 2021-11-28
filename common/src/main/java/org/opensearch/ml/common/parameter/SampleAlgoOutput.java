package org.opensearch.ml.common.parameter;

import lombok.Builder;
import lombok.Getter;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.XContentBuilder;

import java.io.IOException;

@Getter
public class SampleAlgoOutput extends MLOutput{

    public static final String SAMPLE_RESULT_FIELD = "sample_result";
    private Double sampleResult;

    @Builder
    public SampleAlgoOutput(Double sampleResult) {
        super(MLOutputType.SAMPLE_ALGO);
        this.sampleResult = sampleResult;
    }

    public SampleAlgoOutput(StreamInput in) throws IOException {
        super(MLOutputType.SAMPLE_ALGO);
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
}
