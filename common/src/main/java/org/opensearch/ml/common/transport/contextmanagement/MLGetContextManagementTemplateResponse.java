/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.contextmanagement;

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
import org.opensearch.ml.common.contextmanager.ContextManagementTemplate;

import lombok.Getter;

@Getter
public class MLGetContextManagementTemplateResponse extends ActionResponse implements ToXContentObject {

    private ContextManagementTemplate template;

    public MLGetContextManagementTemplateResponse(StreamInput in) throws IOException {
        super(in);
        this.template = new ContextManagementTemplate(in);
    }

    public MLGetContextManagementTemplateResponse(ContextManagementTemplate template) {
        this.template = template;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        template.writeTo(out);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        return template.toXContent(builder, params);
    }

    public static MLGetContextManagementTemplateResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLGetContextManagementTemplateResponse) {
            return (MLGetContextManagementTemplateResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLGetContextManagementTemplateResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionResponse into MLGetContextManagementTemplateResponse", e);
        }
    }
}
