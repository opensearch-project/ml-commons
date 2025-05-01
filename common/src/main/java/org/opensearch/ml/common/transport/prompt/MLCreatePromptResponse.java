/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

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

@Getter
public class MLCreatePromptResponse extends ActionResponse implements ToXContentObject {
    public static final String PROMPT_ID_FIELD = "prompt_id";

    private String promptId;

    /**
     * Construct MLCreatePromptResponse from StreamInput
     *
     * @param in Stream Input
     * @throws IOException if an I/O exception occurred while reading from input stream
     */
    public MLCreatePromptResponse(StreamInput in) throws IOException {
        super(in);
        this.promptId = in.readString();
    }

    /**
     * Construct MLCreatePromptResponse
     *
     * @param promptId The prompt id of the MLPrompt
     */
    public MLCreatePromptResponse(String promptId) {
        this.promptId = promptId;
    }

    /**
     * Write MLCreatePromptResponse to StreamOutput
     *
     * @param out Stream Output
     * @throws IOException if an I/O exception occurred while writing to output stream
     */
    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(promptId);
    }

    /**
     * Convert MLCreatePromptResponse to XContent
     *
     * @param builder XContent Builder
     * @param params Parameters
     * @return XContent
     * @throws IOException if an I/O exception occurred while converting to XContent
     */
    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(PROMPT_ID_FIELD, promptId);
        builder.endObject();
        return builder;
    }

    /**
     * Parse ActionResponse into MLCreatePromptResponse
     *
     * @param actionResponse Action Response
     * @return MLCreatePromptResponse
     */
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
