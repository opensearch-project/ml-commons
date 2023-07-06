/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.ml.common.output.model.ModelTensor;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Connector defines how to connect to a remote service.
 */
public interface Connector extends ToXContentObject, Writeable {

    String getName();

    Map<String, String> getParameters();
    String getPredictEndpoint();

    String getPredictHttpMethod();

    <T> T createPredictPayload(Map<String, String> parameters);

    void decrypt(Function<String, String> function);
    void encrypt(Function<String, String> function);

    Connector cloneConnector();

    default void writeTo(StreamOutput out) throws IOException {
        out.writeString(getName());
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
}
