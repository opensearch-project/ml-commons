/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.plugin.grpc;

import org.opensearch.commons.authuser.User;
import org.opensearch.ml.grpc.interfaces.MLUserContextProvider;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.transport.client.Client;

/**
 * Adapter that provides user context extraction for gRPC services.
 * This breaks the circular dependency between grpc and plugin modules.
 */
public class UserContextProviderAdapter implements MLUserContextProvider {

    private final Client client;

    public UserContextProviderAdapter(Client client) {
        this.client = client;
    }

    @Override
    public User getUserContext() {
        return RestActionUtils.getUserContext(client);
    }
}
