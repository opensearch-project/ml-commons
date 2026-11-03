/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.contextmanagement;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

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
public class MLListContextManagementTemplatesResponse extends ActionResponse implements ToXContentObject {
    public static final String TEMPLATES_FIELD = "templates";
    public static final String TOTAL_FIELD = "total";

    private List<ContextManagementTemplate> templates;
    private long total;

    public MLListContextManagementTemplatesResponse(StreamInput in) throws IOException {
        super(in);
        this.templates = in.readList(ContextManagementTemplate::new);
        this.total = in.readLong();
    }

    public MLListContextManagementTemplatesResponse(List<ContextManagementTemplate> templates, long total) {
        this.templates = templates;
        this.total = total;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeList(templates);
        out.writeLong(total);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(TOTAL_FIELD, total);
        builder.startArray(TEMPLATES_FIELD);
        for (ContextManagementTemplate template : templates) {
            template.toXContent(builder, params);
        }
        builder.endArray();
        builder.endObject();
        return builder;
    }

    public static MLListContextManagementTemplatesResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLListContextManagementTemplatesResponse) {
            return (MLListContextManagementTemplatesResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLListContextManagementTemplatesResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionResponse into MLListContextManagementTemplatesResponse", e);
        }
    }
}
