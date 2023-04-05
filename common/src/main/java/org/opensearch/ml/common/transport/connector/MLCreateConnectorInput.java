/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.connector;

import lombok.Builder;
import com.google.gson.Gson;
import lombok.Data;
import lombok.Getter;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.connector.ConnectorAPIs;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.Map;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

@Data
public class MLCreateConnectorInput implements ToXContentObject, Writeable {

    public static final String CONNECTOR_PARAMETERS_FIELD = "Parameters";
    public static final String CONNECTOR_APIS_FIELD = "APIs";

    private Map<String, String> parameters;
    private ConnectorAPIs connectorAPIs;

    @Builder(toBuilder = true)
    public MLCreateConnectorInput(Map<String, String> parameters,
                                  ConnectorAPIs connectorAPIs) {
        this.parameters = parameters;
        this.connectorAPIs = connectorAPIs;
    }

    public static MLCreateConnectorInput parse(XContentParser parser) throws IOException {
        Map<String, ?> parameterObjs = new HashMap<>();
        Gson gson = new Gson();
        ConnectorAPIs connectorAPIs = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case CONNECTOR_PARAMETERS_FIELD:
                    parameterObjs = parser.map();;
                    break;
                case CONNECTOR_APIS_FIELD:
                    connectorAPIs = connectorAPIs.parse(parser);
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        Map<String, String> parameters = new HashMap<>();
        for (String key : parameterObjs.keySet()) {
            Object value = parameterObjs.get(key);
            try {
                AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                    parameters.put(key, gson.toJson(value));
                    return null;
                });
            } catch (PrivilegedActionException e) {
                throw new RuntimeException(e);
            }
        }
        return new MLCreateConnectorInput(parameters, connectorAPIs);
    }


    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (parameters != null) {
            Map<String, Object> parameterObjs = new HashMap<>();
            parameters.forEach((key, value) -> {
                parameterObjs.put(key, value);
            });
            builder.field(CONNECTOR_PARAMETERS_FIELD, parameterObjs);
        }
        if (connectorAPIs != null) {
            builder.field(CONNECTOR_APIS_FIELD, connectorAPIs);
        }
        builder.endObject();
        return builder;
    }

    @Override
    public void writeTo(StreamOutput output) throws IOException {
        if (parameters != null) {
            output.writeBoolean(true);
            output.writeMap(parameters, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            output.writeBoolean(false);
        }
        if (connectorAPIs != null) {
            output.writeBoolean(true);
            connectorAPIs.writeTo(output);
        } else {
            output.writeBoolean(false);
        }
    }

    public MLCreateConnectorInput(StreamInput input) throws IOException {
        if (input.readBoolean()) {
            parameters = input.readMap(s -> s.readString(), s-> s.readString());
        }
        if (input.readBoolean()) {
            this.connectorAPIs = new ConnectorAPIs(input);
        }
    }
}