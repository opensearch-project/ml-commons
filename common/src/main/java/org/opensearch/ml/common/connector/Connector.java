/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.MLCommonsClassLoader;
import org.opensearch.ml.common.output.model.ModelTensor;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.utils.StringUtils.gson;

/**
 * Connector defines how to connect to a remote service.
 */
public interface Connector extends ToXContentObject, Writeable {

    String getName();
    String getProtocol();

    Map<String, String> getParameters();
    String getPredictEndpoint();
    String getPredictEndpoint(Map<String, String> parameters);

    String getPredictHttpMethod();

    <T> T createPredictPayload(Map<String, String> parameters);

    void decrypt(Function<String, String> function);
    void encrypt(Function<String, String> function);

    Connector cloneConnector();

    default void writeTo(StreamOutput out) throws IOException {
        out.writeString(getProtocol());
        out.writeOptionalString(getPredictEndpoint());
    }

    default <T> void parseResponse(T orElse, List<ModelTensor> modelTensors, boolean b) throws IOException {}

    default void validatePayload(String payload) {
        if (payload != null && payload.contains("${parameters")) {
            Pattern pattern = Pattern.compile("\\$\\{parameters\\.([^}]+)}");
            Matcher matcher = pattern.matcher(payload);

            StringBuilder errorBuilder = new StringBuilder();
            while (matcher.find()) {
                String parameter = matcher.group(1);
                errorBuilder.append(parameter).append(", ");
            }
            String error = errorBuilder.substring(0, errorBuilder.length() - 2).toString();
            throw new IllegalArgumentException("Some parameter placeholder not filled in payload: " + error);
        }
    }

    static Connector createConnector(XContentParser parser) throws IOException {
        Connector connector;
        Map<String, Object> connectorMap = parser.map();
        String jsonStr;
        try {
            jsonStr = AccessController.doPrivileged((PrivilegedExceptionAction<String>) () -> gson.toJson(connectorMap));
        } catch (PrivilegedActionException e) {
            throw new IllegalArgumentException("wrong connector");
        }
        try (XContentParser connectorParser = XContentType.JSON.xContent().createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, jsonStr)) {
            ensureExpectedToken(XContentParser.Token.START_OBJECT, connectorParser.nextToken(), parser);
            String connectorProtocol = (String)connectorMap.get("protocol");
            if (connectorProtocol == null) {
                throw new IllegalArgumentException("connector protocol is null");
            }
            connector = MLCommonsClassLoader.initConnector(connectorProtocol, new Object[]{connectorProtocol, connectorParser}, String.class, XContentParser.class);
        }
        return connector;
    }
}
