package org.opensearch.ml.common.transport.mcpserver.requests.update;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.transport.mcpserver.requests.BaseMcpTool;

public class UpdateMcpTool extends BaseMcpTool {
    public UpdateMcpTool(StreamInput streamInput) throws IOException {
        super(streamInput);
        if (super.getName() == null) {
            throw new IllegalArgumentException(NAME_NOT_SHOWN_EXCEPTION_MESSAGE);
        }
    }

    public UpdateMcpTool(String name, String description, Map<String, Object> parameters, Map<String, Object> attributes, Instant createdTime, Instant lastUpdateTime) {
        super(name, null, description, parameters, attributes, createdTime, lastUpdateTime);
    }

    public static UpdateMcpTool parse(XContentParser parser) throws IOException {
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
        return new UpdateMcpTool(name, description, params, attributes, createdTime, lastUpdateTime);
    }
}
