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
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.prompt.MLPrompt;

import lombok.Builder;
import lombok.Getter;

@Getter
public class MLPromptGetResponse extends ActionResponse implements ToXContentObject {
    MLPrompt mlPrompt;

    /**
     * Construct MLPromptGetResponse with MLPrompt inside the response body
     *
     * @param mlPrompt MLPrompt
     */
    @Builder
    public MLPromptGetResponse(MLPrompt mlPrompt) {
        this.mlPrompt = mlPrompt;
    }

    /**
     * Construct MLPromptGetResponse from StreamInput
     *
     * @param in Stream Input
     * @throws IOException if an I/O exception occurred while reading from input stream
     */
    public MLPromptGetResponse(StreamInput in) throws IOException {
        super(in);
        this.mlPrompt = MLPrompt.fromStream(in);
    }

    /**
     * Write MLPromptGetResponse to StreamOutput
     *
     * @param out Stream Output
     * @throws IOException if an I/O exception occurred while writing to output stream
     */
    @Override
    public void writeTo(StreamOutput out) throws IOException {
        this.mlPrompt.writeTo(out);
    }

    /**
     * Convert MLPromptGetResponse to XContent
     *
     * @param xContentBuilder XContent Builder
     * @param params Parameters
     * @return XContent
     * @throws IOException if an I/O exception occurred while converting to XContent
     */
    @Override
    public XContentBuilder toXContent(XContentBuilder xContentBuilder, ToXContent.Params params) throws IOException {
        return this.mlPrompt.toXContent(xContentBuilder, params);
    }

    /**
     * Parse ActionResponse into MLPromptGetResponse
     *
     * @param actionResponse Action Response
     * @return MLPromptGetResponse
     */
    public static MLPromptGetResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLPromptGetResponse) {
            return (MLPromptGetResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLPromptGetResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionResponse into MLPromptGetResponse", e);
        }
    }
}
