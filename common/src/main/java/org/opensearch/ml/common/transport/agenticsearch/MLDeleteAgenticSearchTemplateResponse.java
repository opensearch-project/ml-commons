/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.agenticsearch;

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
public class MLDeleteAgenticSearchTemplateResponse extends ActionResponse implements ToXContentObject {
    public static final String TEMPLATE_ID_FIELD = "template_id";
    public static final String STATUS_FIELD = "status";

    private String templateId;
    private String status;

    public MLDeleteAgenticSearchTemplateResponse(StreamInput in) throws IOException {
        super(in);
        this.templateId = in.readString();
        this.status = in.readString();
    }

    public MLDeleteAgenticSearchTemplateResponse(String templateId, String status) {
        this.templateId = templateId;
        this.status = status;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(templateId);
        out.writeString(status);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(TEMPLATE_ID_FIELD, templateId);
        builder.field(STATUS_FIELD, status);
        builder.endObject();
        return builder;
    }

    public static MLDeleteAgenticSearchTemplateResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLDeleteAgenticSearchTemplateResponse) {
            return (MLDeleteAgenticSearchTemplateResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLDeleteAgenticSearchTemplateResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionResponse into MLDeleteAgenticSearchTemplateResponse", e);
        }
    }
}
