/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.indices;

import static org.opensearch.ml.indices.MLIndicesHandler.ML_TASK_INDEX;

import java.util.concurrent.ExecutionException;

import org.junit.Assert;
import org.junit.Before;
import org.opensearch.action.ActionListener;
import org.opensearch.action.admin.indices.create.CreateIndexRequest;
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

    public void testInitModelIndex() {
        Assert.assertFalse(mlIndicesHandler.doesModelIndexExist());
        mlIndicesHandler.initModelIndexIfAbsent();
        Assert.assertTrue(mlIndicesHandler.doesModelIndexExist());
        mlIndicesHandler.initModelIndexIfAbsent();
        Assert.assertTrue(mlIndicesHandler.doesModelIndexExist());
    }

    public void testInitMLTaskIndex() {
        ActionListener<Boolean> listener = ActionListener.wrap(r -> { assertTrue(r); }, e -> { throw new RuntimeException(e); });
        mlIndicesHandler.initMLTaskIndex(listener);
    }

    public void testInitMLTaskIndexWithExistingIndex() throws ExecutionException, InterruptedException {
        CreateIndexRequest request = new CreateIndexRequest(ML_TASK_INDEX);
        client.admin().indices().create(request).get();
        testInitMLTaskIndex();
    }
}
