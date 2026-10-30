/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.plugin;

import org.opensearch.transport.client.Client;

/**
 * ML Commons' client that runs transport actions as this plugin's system subject.
 */
public final class PluginClient extends org.opensearch.commons.client.PluginClient {

    /**
     * Wraps a client so its actions run as the plugin subject, which is assigned later.
     *
     * @param client the client whose actions this wraps
     */
    public PluginClient(Client client) {
        super(client);
    }
}
