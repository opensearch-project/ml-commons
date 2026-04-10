/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.plugin.grpc;

import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.ml.grpc.interfaces.MLClient;
import org.opensearch.transport.client.Client;

/**
 * Adapter that wraps OpenSearch Client to implement the gRPC interface.
 * This breaks the circular dependency between grpc and plugin modules.
 */
public class ClientAdapter implements MLClient {

    private final Client delegate;

    public ClientAdapter(Client delegate) {
        this.delegate = delegate;
    }

    @Override
    public ThreadContext getThreadContext() {
        return delegate.threadPool().getThreadContext();
    }

    /**
     * Gets the underlying client for passing to other components.
     *
     * @return the wrapped client
     */
    public Client getDelegate() {
        return delegate;
    }
}
