/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.common.transport.mcpserver.requests.register;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.Getter;

/**
 * This class represents the tools that are registered in the cluster.
 * It contains a list of McpTool objects, along with the creation and last update times.
 */
@Getter
public class McpTools implements ToXContentObject, Writeable {

    private static final String TOOLS_FIELD = "tools";
    private static final String CREATED_TIME_FIELD = "create_time";
    private static final String LAST_UPDATED_TIME_FIELD = "last_updated_time";
    private List<McpTool> tools;
    private final Instant createdTime;
    private final Instant lastUpdateTime;

    public McpTools(StreamInput streamInput) throws IOException {
        if (streamInput.readBoolean()) {
            tools = streamInput.readList(si -> new McpTool(streamInput));
        }
        createdTime = streamInput.readOptionalInstant();
        lastUpdateTime = streamInput.readOptionalInstant();
    }

    public McpTools(List<McpTool> tools, Instant createdTime, Instant lastUpdateTime) {
        this.tools = tools;
        this.createdTime = createdTime;
        this.lastUpdateTime = lastUpdateTime;
    }

    public static McpTools parse(XContentParser parser) throws IOException {
        List<McpTool> tools = List.of();
        Instant createdTime = null;
        Instant lastUpdateTime = null;
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case TOOLS_FIELD:
                    tools = new ArrayList<>();
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        tools.add(McpTool.parse(parser));
                    }
                    break;
                case CREATED_TIME_FIELD:
                    createdTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case LAST_UPDATED_TIME_FIELD:
                    lastUpdateTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new McpTools(tools, createdTime, lastUpdateTime);
    }

    @Override
    public void writeTo(StreamOutput streamOutput) throws IOException {
        if (tools != null) {
            streamOutput.writeBoolean(true);
            streamOutput.writeList(tools);
        } else {
            streamOutput.writeBoolean(false);
        }
        streamOutput.writeOptionalInstant(createdTime);
        streamOutput.writeOptionalInstant(lastUpdateTime);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (tools != null && !tools.isEmpty()) {
            builder.field(TOOLS_FIELD, tools);
        }
        if (createdTime != null) {
            builder.field(CREATED_TIME_FIELD, createdTime.toEpochMilli());
        }
        if (lastUpdateTime != null) {
            builder.field(LAST_UPDATED_TIME_FIELD, lastUpdateTime.toEpochMilli());
        }
        builder.endObject();
        return builder;
    }
}
