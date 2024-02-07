/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import java.io.IOException;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

@Getter
@EqualsAndHashCode
public class Configuration implements ToXContentObject, Writeable {

    public static final String ROOT_AGENT_ID = "agent_id";

    @Setter
    private String agentId;

    @Builder(toBuilder = true)
    public Configuration(
        String agentId
    ) {
        this.agentId = agentId;
    }

    public Configuration(StreamInput input) throws IOException {
        this.agentId = input.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(agentId);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder xContentBuilder, Params params) throws IOException {
        XContentBuilder builder = xContentBuilder.startObject();
        if (agentId != null) {
            builder.field(ROOT_AGENT_ID, agentId);
        }
        return builder.endObject();
    }

    public static Configuration fromStream(StreamInput in) throws IOException {
        Configuration configuration = new Configuration(in);
        return configuration;
    }

    public static Configuration parse(XContentParser parser) throws IOException {
        String agentId = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case ROOT_AGENT_ID:
                    agentId = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return Configuration.builder()
                .agentId(agentId)
                .build();
    }
}
