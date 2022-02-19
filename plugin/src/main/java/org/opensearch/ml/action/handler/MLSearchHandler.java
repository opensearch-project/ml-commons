/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.handler;

import lombok.extern.log4j.Log4j2;

import org.opensearch.client.Client;
import org.opensearch.common.xcontent.NamedXContentRegistry;

/**
 * Handle general get and search request in ml common.
 */
@Log4j2
public class MLSearchHandler {
    private final Client client;
    private NamedXContentRegistry xContentRegistry;

    public MLSearchHandler(Client client, NamedXContentRegistry xContentRegistry) {
        this.client = client;
        this.xContentRegistry = xContentRegistry;
    }
}
