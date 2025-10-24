/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.memory;

import java.io.IOException;

import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.memory.Message;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

public class BaseMessage implements Message, ToXContentObject {

    @Getter
    @Setter
    protected String type;
    @Getter
    @Setter
    protected String content;

    @Builder
    public BaseMessage(String type, String content) {
        this.type = type;
        this.content = content;
    }

    @Override
    public String toString() {
        return type + ": " + content;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field("type", type);
        builder.field("content", content);
        builder.endObject();
        return builder;
    }
}
