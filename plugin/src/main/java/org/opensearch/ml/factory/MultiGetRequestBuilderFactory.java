/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.factory;

import org.opensearch.action.get.MultiGetAction;
import org.opensearch.action.get.MultiGetRequestBuilder;
import org.opensearch.client.Client;

public class MultiGetRequestBuilderFactory {

    public MultiGetRequestBuilder createMultiGetRequestBuilder(Client client) {
        return new MultiGetRequestBuilder(client, MultiGetAction.INSTANCE);
    }
}
