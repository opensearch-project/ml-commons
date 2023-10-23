/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.common.transport.tools;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.ToolMetadata;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import static org.opensearch.action.ValidateActions.addValidationError;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class MLToolGetRequest extends ActionRequest {

    String toolName;

    List<ToolMetadata> externalTools;

    @Builder
    public MLToolGetRequest(String toolName, List<ToolMetadata> externalTools) {
        this.toolName = toolName;
        this.externalTools = externalTools;
    }

    public MLToolGetRequest(StreamInput in) throws IOException {
        super(in);
        this.toolName = in.readString();
        this.externalTools = in.readList(ToolMetadata::new);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(this.toolName);
        out.writeList(this.externalTools);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;

        if (this.toolName == null) {
            exception = addValidationError("Tool name can't be null", exception);
        }

        return exception;
    }

    public static MLToolGetRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLToolGetRequest) {
            return (MLToolGetRequest)actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLToolGetRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLToolGetRequest", e);
        }
    }


}
