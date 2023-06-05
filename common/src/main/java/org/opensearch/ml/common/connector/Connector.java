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

/**
 * Connector defines how to connect to a remote service.
 */
public interface Connector extends ToXContentObject, Writeable {

    String getName();

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

    default String getPreProcessFunction() {
        return null;
    }

    default String getPostProcessFunction() {
        return null;
    }

    default <T> void parseResponse(T orElse, List<ModelTensor> modelTensors, boolean b) throws IOException {}
}
