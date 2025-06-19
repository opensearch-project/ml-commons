/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.prompt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

public class MLImportPromptResponse extends ActionResponse implements ToXContentObject {
    public static final String IMPORTED_PROMPTS_FIELD = "imported_prompts";

    private Map<String, String> importedPrompts;

    public MLImportPromptResponse(Map<String, String> importedPrompts) {
        this.importedPrompts = importedPrompts;
    }

    public MLImportPromptResponse(StreamInput in) throws IOException {
        super(in);
        this.importedPrompts = in.readMap(s -> s.readString(), s -> s.readString());
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeMap(importedPrompts, StreamOutput::writeString, StreamOutput::writeString);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(IMPORTED_PROMPTS_FIELD, importedPrompts);
        builder.endObject();
        return builder;
    }

    public static MLImportPromptResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLImportPromptResponse) {
            return (MLImportPromptResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLImportPromptResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionResponse into MLImportPromptResponse", e);
        }
    }
}
