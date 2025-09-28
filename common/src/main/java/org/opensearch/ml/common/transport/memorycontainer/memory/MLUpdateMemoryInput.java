/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.util.Map;

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

    private Map<String, Object> updateContent;

    @Builder
    public MLUpdateMemoryInput(Map<String, Object> updateContent) {
        if (updateContent == null || updateContent.size() == 0) {
            throw new IllegalArgumentException("Text cannot be null or empty");
        }
        this.updateContent = updateContent;
    }

    public MLUpdateMemoryInput(StreamInput in) throws IOException {
        this.updateContent = in.readMap();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeMap(updateContent);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        return builder.map(updateContent);
    }

    public static MLUpdateMemoryInput parse(XContentParser parser) throws IOException {
        Map<String, Object> updateContent = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        updateContent = parser.map();

        return MLUpdateMemoryInput.builder().updateContent(updateContent).build();
    }
}
