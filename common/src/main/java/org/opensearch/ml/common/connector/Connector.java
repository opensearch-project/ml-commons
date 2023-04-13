/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;

import java.io.IOException;
import java.util.Map;
import java.util.function.Function;

/**
 * Connector defines how to connect to a remote service.
 */
public interface Connector extends ToXContentObject, Writeable {

    String getName();

    String getEndpoint();

    <T> T createPayload(Map<String, ?> parameters);

    void decrypt(Function<String, String> function);
    void encrypt(Function<String, String> function);

    Connector clone();

    default void writeTo(StreamOutput out) throws IOException {
        out.writeString(getName());
        out.writeOptionalString(getEndpoint());
    }
}
