/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.plugin.grpc;

import org.opensearch.ml.grpc.interfaces.MLSdkClient;
import org.opensearch.remote.metadata.client.SdkClient;

/**
 * Adapter that wraps SDK Client to implement the gRPC interface.
 * This breaks the circular dependency between grpc and plugin modules.
 *
 * This is primarily a pass-through adapter as the SDK client is mainly used
 * for multi-tenant operations that are passed to other components.
 */
public class SdkClientAdapter implements MLSdkClient {

    private final SdkClient delegate;

    public SdkClientAdapter(SdkClient delegate) {
        this.delegate = delegate;
    }

    /**
     * Gets the underlying SDK client for passing to other components.
     *
     * @return the wrapped SDK client
     */
    public SdkClient getDelegate() {
        return delegate;
    }
}
