/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import java.util.Map;

import org.junit.Ignore;
import org.opensearch.action.support.master.AcknowledgedResponse;
import org.opensearch.core.action.ActionListener;
import org.opensearch.test.OpenSearchIntegTestCase;

public class IndexUtilsTests extends OpenSearchIntegTestCase {

    public void testGetIndexHealth_NoIndex() {
        IndexUtils indexUtils = new IndexUtils(client(), clusterService());
        String output = indexUtils.getIndexHealthStatus("test");
        assertEquals(IndexUtils.NONEXISTENT_INDEX_STATUS, output);
    }

    public void testGetIndexHealth_Index() {
        String indexName = "test-2";
        createIndex(indexName);
        flush();
        IndexUtils indexUtils = new IndexUtils(client(), clusterService());
        String status = indexUtils.getIndexHealthStatus(indexName);
        assertTrue(status.equals("green") || status.equals("yellow"));
    }

    public void testGetIndexHealth_Alias() {
        String indexName = "test-2";
        String aliasName = "alias";
        createIndex(indexName);
        flush();
        AcknowledgedResponse response = client().admin().indices().prepareAliases().addAlias(indexName, aliasName).execute().actionGet();
        assertTrue(response.isAcknowledged());
        IndexUtils indexUtils = new IndexUtils(client(), clusterService());
        String status = indexUtils.getIndexHealthStatus(aliasName);
        assertTrue(status.equals("green") || status.equals("yellow"));
    }

    public void testGetNumberOfDocumentsInIndex_NonExistentIndex() {
        IndexUtils indexUtils = new IndexUtils(client(), clusterService());
        indexUtils.getNumberOfDocumentsInIndex("index", ActionListener.wrap(r -> { assertEquals((Long) 0L, r); }, e -> { assertNull(e); }));
    }

    @Ignore
    public void testGetNumberOfDocumentsInIndex_RegularIndex() {
        String indexName = "test-2";
        createIndex(indexName);
        flush();

        long count = 20;
        for (int i = 0; i < count; i++) {
            index(indexName, "_doc", i + "", Map.of(randomAlphaOfLength(5), randomAlphaOfLength(5)));
        }
        flushAndRefresh(indexName);
        IndexUtils indexUtils = new IndexUtils(client(), clusterService());
        indexUtils
            .getNumberOfDocumentsInIndex(indexName, ActionListener.wrap(r -> { assertEquals((Long) count, r); }, e -> { assertNull(e); }));
    }
}
