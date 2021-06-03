/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.indices;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.test.OpenSearchIntegTestCase;

public class MLIndicesHandlerTests extends OpenSearchIntegTestCase {
    ClusterService clusterService;
    Client client;
    MLIndicesHandler mlIndicesHandler;

    @Before
    public void setup() {
        clusterService = clusterService();
        client = client();
        mlIndicesHandler = new MLIndicesHandler(clusterService, client);
    }

    @Test
    public void testInitModelIndex() {
        Assert.assertFalse(mlIndicesHandler.doesModelIndexExist());
        mlIndicesHandler.initModelIndexIfAbsent();
        Assert.assertTrue(mlIndicesHandler.doesModelIndexExist());
        mlIndicesHandler.initModelIndexIfAbsent();
        Assert.assertTrue(mlIndicesHandler.doesModelIndexExist());
    }
}
