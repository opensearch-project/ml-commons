/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.factory;

import static org.mockito.ArgumentMatchers.any;

import org.opensearch.action.get.MultiGetRequestBuilder;
import org.opensearch.client.Client;
import org.opensearch.test.OpenSearchTestCase;

public class MultiGetRequestBuilderFactoryTests extends OpenSearchTestCase {

    private final MultiGetRequestBuilderFactory multiGetRequestBuilderFactory = new MultiGetRequestBuilderFactory();

    public void testCreateMultiGetRequestBuilder() {
        MultiGetRequestBuilder builder = multiGetRequestBuilderFactory.createMultiGetRequestBuilder(any(Client.class));
        assertNotNull(builder);
    }
}
