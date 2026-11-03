/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.agenticsearch;

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
import org.opensearch.ml.common.agenticsearch.AgenticSearchTemplate;

import lombok.Getter;

@Getter
public class MLListAgenticSearchTemplatesResponse extends ActionResponse implements ToXContentObject {
    public static final String TOTAL_FIELD = "total";
    public static final String TEMPLATES_FIELD = "templates";

    private List<AgenticSearchTemplate> templates;
    private long total;

    public MLListAgenticSearchTemplatesResponse(StreamInput in) throws IOException {
        super(in);
        this.templates = in.readList(AgenticSearchTemplate::new);
        this.total = in.readLong();
    }

    public MLListAgenticSearchTemplatesResponse(List<AgenticSearchTemplate> templates, long total) {
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
        for (AgenticSearchTemplate template : templates) {
            template.toXContent(builder, params);
        }
        builder.endArray();
        builder.endObject();
        return builder;
    }

    public static MLListAgenticSearchTemplatesResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLListAgenticSearchTemplatesResponse) {
            return (MLListAgenticSearchTemplatesResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLListAgenticSearchTemplatesResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionResponse into MLListAgenticSearchTemplatesResponse", e);
        }
    }
}
