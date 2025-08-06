/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.TEXT_FIELD;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Input data for updating a memory
 */
@Getter
@Setter
public class MLUpdateMemoryInput implements ToXContentObject, Writeable {

    private String text;

    @Builder
    public MLUpdateMemoryInput(String text) {
        if (StringUtils.isBlank(text)) {
            throw new IllegalArgumentException("Text cannot be null or empty");
        }
        this.text = text.trim();
    }

    public MLUpdateMemoryInput(StreamInput in) throws IOException {
        this.text = in.readString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(text);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(TEXT_FIELD, text);
        builder.endObject();
        return builder;
    }

    public static MLUpdateMemoryInput parse(XContentParser parser) throws IOException {
        String text = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            if (TEXT_FIELD.equals(fieldName)) {
                text = parser.text();
            } else {
                parser.skipChildren();
            }
        }

        return MLUpdateMemoryInput.builder().text(text).build();
    }
}
