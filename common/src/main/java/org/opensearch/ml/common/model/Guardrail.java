/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import java.io.IOException;
import java.util.Map;

import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.transport.client.Client;

public abstract class Guardrail implements ToXContentObject {

    public abstract void writeTo(StreamOutput out) throws IOException;

    public abstract Boolean validate(String input, Map<String, String> parameters);

    public abstract void init(NamedXContentRegistry xContentRegistry, Client client, SdkClient sdkClient, String tenantId);
}
