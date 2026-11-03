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

import lombok.Getter;

@Getter
public class MLDeleteContextManagementTemplateResponse extends ActionResponse implements ToXContentObject {
    public static final String TEMPLATE_NAME_FIELD = "context_management_name";
    public static final String STATUS_FIELD = "status";

    private String templateName;
    private String status;

    public MLDeleteContextManagementTemplateResponse(StreamInput in) throws IOException {
        super(in);
        this.templateName = in.readString();
        this.status = in.readString();
    }

    public MLDeleteContextManagementTemplateResponse(String templateName, String status) {
        this.templateName = templateName;
        this.status = status;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(templateName);
        out.writeString(status);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(TEMPLATE_NAME_FIELD, templateName);
        builder.field(STATUS_FIELD, status);
        builder.endObject();
        return builder;
    }

    public static MLDeleteContextManagementTemplateResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLDeleteContextManagementTemplateResponse) {
            return (MLDeleteContextManagementTemplateResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLDeleteContextManagementTemplateResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionResponse into MLDeleteContextManagementTemplateResponse", e);
        }
    }
}
