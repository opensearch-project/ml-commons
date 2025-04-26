package org.opensearch.ml.common.transport.prompt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.Getter;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorResponse;

@Getter
public class MLCreatePromptResponse extends ActionResponse implements ToXContentObject{
    public static final String PROMPT_ID_FIELD = "prompt_id";
    public static final String STATUS_FIELD = "status";

    private String promptId;

    public MLCreatePromptResponse(StreamInput in) throws IOException {
        super(in);
        this.promptId = in.readString();
    }

    public MLCreatePromptResponse(String promptId) {
        this.promptId = promptId;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(promptId);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(PROMPT_ID_FIELD, promptId);
        builder.endObject();
        return builder;
    }

    public static MLCreatePromptResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLCreatePromptResponse) {
            return (MLCreatePromptResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLCreatePromptResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionResponse into MLCreatePromptResponse", e);
        }

    }
}
