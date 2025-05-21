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
import java.util.Map;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.XContentParser;

import lombok.extern.log4j.Log4j2;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.transport.mcpserver.requests.BaseMcpTool;

/**
 *  This class represents a tool that can be registered with OpenSearch. It contains information about the tool's name,
 * description, parameters, and schema.
 */
@Log4j2
public class RegisterMcpTool extends BaseMcpTool {
    public RegisterMcpTool(StreamInput streamInput) throws IOException {
        super(streamInput);
        if (super.getType() == null) {
            throw new IllegalArgumentException(TYPE_NOT_SHOWN_EXCEPTION_MESSAGE);
        }
    }

    public RegisterMcpTool(String name, String type, String description, Map<String, Object> parameters, Map<String, Object> attributes, Instant createdTime, Instant lastUpdateTime) {
        super(name, type, description, parameters, attributes, createdTime, lastUpdateTime);
    }

    public static RegisterMcpTool parse(XContentParser parser) throws IOException {
        String type = null;
        String name = null;
        String description = null;
        Map<String, Object> params = null;
        Map<String, Object> attributes = null;
        Instant createdTime = null;
        Instant lastUpdateTime = null;
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case TYPE_FIELD:
                    type = parser.text();
                    break;
                case NAME_FIELD:
                    name = parser.text();
                    break;
                case DESCRIPTION_FIELD:
                    description = parser.text();
                    break;
                case PARAMS_FIELD:
                    params = parser.map();
                    break;
                case ATTRIBUTES_FIELD:
                    attributes = parser.map();
                    break;
                case CommonValue.CREATE_TIME_FIELD:
                    createdTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case CommonValue.LAST_UPDATE_TIME_FIELD:
                    lastUpdateTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new RegisterMcpTool(name, type, description, params, attributes, createdTime, lastUpdateTime);
    }
}
