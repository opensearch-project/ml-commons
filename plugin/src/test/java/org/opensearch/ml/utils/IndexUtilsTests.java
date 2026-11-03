/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import java.util.List;

import org.junit.Ignore;
import org.opensearch.action.index.IndexRequestBuilder;
import org.opensearch.action.support.PlainActionFuture;
import org.opensearch.action.support.clustermanager.AcknowledgedResponse;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.search.SearchModule;
import org.opensearch.test.OpenSearchIntegTestCase;

import com.google.common.collect.ImmutableMap;

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
            index(indexName, "_doc", i + "", ImmutableMap.of(randomAlphaOfLength(5), randomAlphaOfLength(5)));
        }
        flushAndRefresh(indexName);
        IndexUtils indexUtils = new IndexUtils(client(), clusterService());
        indexUtils
            .getNumberOfDocumentsInIndex(indexName, ActionListener.wrap(r -> { assertEquals((Long) count, r); }, e -> { assertNull(e); }));
    }

    public void testGetNumberOfDocumentsInIndex_SearchQuery() throws Exception {
        String indexName = "test-2";
        createIndex(indexName);

        int count = 20;
        IndexRequestBuilder[] builders = new IndexRequestBuilder[count];
        for (int i = 0; i < count; i++) {
            builders[i] = client().prepareIndex(indexName).setId(i + "").setSource(randomAlphaOfLength(5), randomAlphaOfLength(5));
        }
        indexRandom(true, builders);

        NamedXContentRegistry xContentRegistry = new NamedXContentRegistry(new SearchModule(Settings.EMPTY, List.of()).getNamedXContents());

        String searchQuery = "{}";
        IndexUtils indexUtils = new IndexUtils(client(), clusterService());

        PlainActionFuture<Long> future = PlainActionFuture.newFuture();
        indexUtils.getNumberOfDocumentsInIndex(indexName, searchQuery, xContentRegistry, future);
        assertEquals((Long) (long) count, future.actionGet());
    }

}
