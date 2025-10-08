/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.helper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.mockito.Answers;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.admin.indices.delete.DeleteIndexRequest;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.PlainActionFuture;
import org.opensearch.action.support.clustermanager.AcknowledgedResponse;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.reindex.BulkByScrollResponse;
import org.opensearch.index.reindex.DeleteByQueryAction;
import org.opensearch.index.reindex.DeleteByQueryRequest;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.memorycontainer.MemoryType;
import org.opensearch.remote.metadata.client.GetDataObjectResponse;
import org.opensearch.remote.metadata.client.SearchDataObjectRequest;
import org.opensearch.remote.metadata.client.SearchDataObjectResponse;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.profile.SearchProfileShardResults;
import org.opensearch.search.suggest.Suggest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.AdminClient;
import org.opensearch.transport.client.Client;
import org.opensearch.transport.client.ClusterAdminClient;
import org.opensearch.transport.client.IndicesAdminClient;

public class MemoryContainerHelperTests extends OpenSearchTestCase {

    private Client client;
    private org.opensearch.remote.metadata.client.SdkClient sdkClient;
    private ThreadPool threadPool;
    private ThreadContext threadContext;
    private MemoryContainerHelper helper;

    @Before
    public void setUpTest() {
        client = mock(Client.class, Answers.RETURNS_DEEP_STUBS);
        sdkClient = mock(org.opensearch.remote.metadata.client.SdkClient.class);
        threadPool = mock(ThreadPool.class);
        threadContext = new ThreadContext(Settings.builder().build());
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        helper = new MemoryContainerHelper(client, sdkClient, NamedXContentRegistry.EMPTY);
    }

    public void testGetMemoryContainerSuccess() throws Exception {
        MLMemoryContainer container = createContainer();
        String source = containerToJson(container);

        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(true);
        when(getResponse.getSourceAsString()).thenReturn(source);

        GetDataObjectResponse dataResponse = mock(GetDataObjectResponse.class);
        when(dataResponse.getResponse()).thenReturn(getResponse);

        CompletableFuture<GetDataObjectResponse> future = CompletableFuture.completedFuture(dataResponse);
        when(sdkClient.getDataObjectAsync(any())).thenReturn(future);

        PlainActionFuture<MLMemoryContainer> listener = PlainActionFuture.newFuture();
        helper.getMemoryContainer("container-id", listener);

        MLMemoryContainer result = listener.actionGet();
        assertEquals(container.getName(), result.getName());
        assertEquals(container.getConfiguration().getIndexPrefix(), result.getConfiguration().getIndexPrefix());
    }

    public void testGetMemoryContainerNotFound() {
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponse.isExists()).thenReturn(false);

        GetDataObjectResponse dataResponse = mock(GetDataObjectResponse.class);
        when(dataResponse.getResponse()).thenReturn(getResponse);

        CompletableFuture<GetDataObjectResponse> future = CompletableFuture.completedFuture(dataResponse);
        when(sdkClient.getDataObjectAsync(any())).thenReturn(future);

        PlainActionFuture<MLMemoryContainer> listener = PlainActionFuture.newFuture();
        helper.getMemoryContainer("missing", listener);

        OpenSearchStatusException exception = expectThrows(OpenSearchStatusException.class, listener::actionGet);
        assertEquals(RestStatus.NOT_FOUND, exception.status());
    }

    public void testGetMemoryContainerIndexMissing() {
        CompletableFuture<GetDataObjectResponse> future = new CompletableFuture<>();
        future.completeExceptionally(new IndexNotFoundException("missing"));
        when(sdkClient.getDataObjectAsync(any())).thenReturn(future);

        PlainActionFuture<MLMemoryContainer> listener = PlainActionFuture.newFuture();
        helper.getMemoryContainer("missing", listener);

        OpenSearchStatusException exception = expectThrows(OpenSearchStatusException.class, listener::actionGet);
        assertEquals(RestStatus.NOT_FOUND, exception.status());
    }

    public void testGetMemoryContainerFailure() {
        CompletableFuture<GetDataObjectResponse> future = new CompletableFuture<>();
        future.completeExceptionally(new IllegalStateException("boom"));
        when(sdkClient.getDataObjectAsync(any())).thenReturn(future);

        PlainActionFuture<MLMemoryContainer> listener = PlainActionFuture.newFuture();
        helper.getMemoryContainer("id", listener);

        IllegalStateException exception = expectThrows(IllegalStateException.class, listener::actionGet);
        assertEquals("boom", exception.getMessage());
    }

    public void testCheckMemoryContainerAccess() {
        assertTrue(helper.checkMemoryContainerAccess(null, createContainer()));

        User admin = new User("admin", Collections.emptyList(), Arrays.asList("all_access"), Map.of());
        assertTrue(helper.checkMemoryContainerAccess(admin, createContainer()));

        User owner = new User("owner", Collections.emptyList(), Collections.emptyList(), Map.of());
        MLMemoryContainer container = createContainerBuilder().owner(owner).build();
        assertTrue(helper.checkMemoryContainerAccess(owner, container));

        User userWithRole = new User("user", Arrays.asList("roleB"), Collections.emptyList(), Map.of());
        assertTrue(helper.checkMemoryContainerAccess(userWithRole, createContainer()));

        User unrelated = new User("user", Arrays.asList("roleZ"), Collections.emptyList(), Map.of());
        assertFalse(helper.checkMemoryContainerAccess(unrelated, createContainer()));

        // Test case where owner has backend roles and user has matching backend role
        User ownerWithBackendRoles = new User(
            "ownerWithRoles",
            Arrays.asList("backend-role-1", "backend-role-2"),
            Collections.emptyList(),
            Map.of()
        );
        User userWithMatchingBackendRole = new User("userWithRole", Arrays.asList("backend-role-1"), Collections.emptyList(), Map.of());
        MLMemoryContainer containerWithOwnerBackendRoles = createContainerBuilder()
            .owner(ownerWithBackendRoles)
            .backendRoles(null) // No explicit backend roles on container
            .build();
        assertTrue(helper.checkMemoryContainerAccess(userWithMatchingBackendRole, containerWithOwnerBackendRoles));

        // Test case where user has no matching backend roles
        User userWithoutMatchingRole = new User("userNoMatch", Arrays.asList("other-role"), Collections.emptyList(), Map.of());
        assertFalse(helper.checkMemoryContainerAccess(userWithoutMatchingRole, containerWithOwnerBackendRoles));

        // Test case where container has empty backend roles list
        MLMemoryContainer containerWithEmptyRoles = createContainerBuilder().backendRoles(Collections.emptyList()).owner(owner).build();
        User userWithBackendRoles = new User("userWithRoles", Arrays.asList("role1"), Collections.emptyList(), Map.of());
        assertFalse(helper.checkMemoryContainerAccess(userWithBackendRoles, containerWithEmptyRoles));

        // Test case where user has null backend roles
        User userWithNullBackendRoles = new User("userNull", null, Collections.emptyList(), Map.of());
        assertFalse(helper.checkMemoryContainerAccess(userWithNullBackendRoles, createContainer()));
    }

    public void testCheckMemoryAccess() {
        // Test with null user (security disabled)
        assertTrue(helper.checkMemoryAccess(null, "any"));

        User admin = new User("admin", Collections.emptyList(), Arrays.asList("all_access"), Map.of());
        assertTrue(helper.checkMemoryAccess(admin, "any"));

        // Test user with null roles
        User adminWithNullRoles = new User("admin", null, null, Map.of());
        assertFalse(helper.checkMemoryAccess(adminWithNullRoles, "owner"));

        User owner = new User("owner", Collections.emptyList(), Collections.emptyList(), Map.of());
        assertTrue(helper.checkMemoryAccess(owner, "owner"));

        User other = new User("other", Collections.emptyList(), Collections.emptyList(), Map.of());
        assertFalse(helper.checkMemoryAccess(other, "owner"));
    }

    public void testGetMemoryIndexName() {
        MemoryConfiguration configuration = MemoryConfiguration
            .builder()
            .indexPrefix("prefix")
            .embeddingModelId("embedding")
            .embeddingModelType(FunctionName.TEXT_EMBEDDING)
            .dimension(4)
            .build();

        MLMemoryContainer container = createContainerBuilder().configuration(configuration).build();
        assertNotNull(helper.getMemoryIndexName(container, MemoryType.LONG_TERM));
        assertNull(helper.getMemoryIndexName(container, null));

        // Test with null configuration - returns default index name
        MLMemoryContainer containerNullConfig = createContainerBuilder().configuration(null).build();
        // When configuration is null, a default index name is used
        assertNotNull(helper.getMemoryIndexName(containerNullConfig, MemoryType.LONG_TERM));
        assertEquals(".plugins-ml-am-default-memory-long-term", helper.getMemoryIndexName(containerNullConfig, MemoryType.LONG_TERM));

        // Test all memory types - by default all are enabled
        assertNotNull(helper.getMemoryIndexName(container, MemoryType.SESSIONS));
        assertNotNull(helper.getMemoryIndexName(container, MemoryType.WORKING));
        assertNotNull(helper.getMemoryIndexName(container, MemoryType.HISTORY));
    }

    public void testSearchData() {
        MemoryConfiguration configuration = MemoryConfiguration.builder().indexPrefix("prefix").build();
        SearchDataObjectRequest request = SearchDataObjectRequest
            .builder()
            .indices("index")
            .searchSourceBuilder(new SearchSourceBuilder())
            .build();

        SearchResponse searchResponse = createSearchResponse(2);
        SearchDataObjectResponse response = new SearchDataObjectResponse(searchResponse);
        when(sdkClient.searchDataObjectAsync(any())).thenReturn(CompletableFuture.completedFuture(response));

        PlainActionFuture<SearchResponse> future = PlainActionFuture.newFuture();
        helper.searchData(configuration, request, future);
        assertSame(searchResponse, future.actionGet());

        CompletableFuture<SearchDataObjectResponse> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("search failure"));
        when(sdkClient.searchDataObjectAsync(any())).thenReturn(failed);

        PlainActionFuture<SearchResponse> failure = PlainActionFuture.newFuture();
        helper.searchData(configuration, request, failure);
        RuntimeException exception = expectThrows(RuntimeException.class, failure::actionGet);
        assertEquals("search failure", exception.getMessage());
    }

    public void testSearchDataWithSystemIndex() {
        // Test search with system index
        MemoryConfiguration systemConfig = MemoryConfiguration.builder().indexPrefix("prefix").useSystemIndex(true).build();
        SearchDataObjectRequest request = SearchDataObjectRequest
            .builder()
            .indices("index")
            .searchSourceBuilder(new SearchSourceBuilder())
            .build();

        SearchResponse searchResponse = createSearchResponse(3);
        SearchDataObjectResponse response = new SearchDataObjectResponse(searchResponse);
        when(sdkClient.searchDataObjectAsync(any())).thenReturn(CompletableFuture.completedFuture(response));

        PlainActionFuture<SearchResponse> future = PlainActionFuture.newFuture();
        helper.searchData(systemConfig, request, future);
        assertSame(searchResponse, future.actionGet());

        // Test failure with system index
        CompletableFuture<SearchDataObjectResponse> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("system index search failure"));
        when(sdkClient.searchDataObjectAsync(any())).thenReturn(failed);

        PlainActionFuture<SearchResponse> failure = PlainActionFuture.newFuture();
        helper.searchData(systemConfig, request, failure);
        RuntimeException exception = expectThrows(RuntimeException.class, failure::actionGet);
        assertEquals("system index search failure", exception.getMessage());
    }

    public void testDataOperations() {
        MemoryConfiguration configuration = MemoryConfiguration.builder().indexPrefix("prefix").build();

        GetRequest getRequest = new GetRequest("index", "id");
        PlainActionFuture<GetResponse> getFuture = PlainActionFuture.newFuture();
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(mock(GetResponse.class));
            return null;
        }).when(client).get(eq(getRequest), any());
        helper.getData(configuration, getRequest, getFuture);
        assertNotNull(getFuture.actionGet());

        IndexRequest indexRequest = new IndexRequest("index");
        PlainActionFuture<IndexResponse> indexFuture = PlainActionFuture.newFuture();
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(mock(IndexResponse.class));
            return null;
        }).when(client).index(eq(indexRequest), any());
        helper.indexData(configuration, indexRequest, indexFuture);
        assertNotNull(indexFuture.actionGet());

        UpdateRequest updateRequest = new UpdateRequest("index", "id");
        PlainActionFuture<UpdateResponse> updateFuture = PlainActionFuture.newFuture();
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(mock(UpdateResponse.class));
            return null;
        }).when(client).update(eq(updateRequest), any());
        helper.updateData(configuration, updateRequest, updateFuture);
        assertNotNull(updateFuture.actionGet());

        DeleteRequest deleteRequest = new DeleteRequest("index", "id");
        PlainActionFuture<DeleteResponse> deleteFuture = PlainActionFuture.newFuture();
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(mock(DeleteResponse.class));
            return null;
        }).when(client).delete(eq(deleteRequest), any());
        helper.deleteData(configuration, deleteRequest, deleteFuture);
        assertNotNull(deleteFuture.actionGet());
    }

    public void testDataOperationsWithSystemIndex() {
        // Test operations with system index (useSystemIndex = true)
        MemoryConfiguration systemConfig = MemoryConfiguration.builder().indexPrefix("prefix").useSystemIndex(true).build();

        GetRequest getRequest = new GetRequest("index", "id");
        PlainActionFuture<GetResponse> getFuture = PlainActionFuture.newFuture();
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(mock(GetResponse.class));
            return null;
        }).when(client).get(eq(getRequest), any());
        helper.getData(systemConfig, getRequest, getFuture);
        assertNotNull(getFuture.actionGet());

        IndexRequest indexRequest = new IndexRequest("index");
        PlainActionFuture<IndexResponse> indexFuture = PlainActionFuture.newFuture();
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(mock(IndexResponse.class));
            return null;
        }).when(client).index(eq(indexRequest), any());
        helper.indexData(systemConfig, indexRequest, indexFuture);
        assertNotNull(indexFuture.actionGet());

        UpdateRequest updateRequest = new UpdateRequest("index", "id");
        PlainActionFuture<UpdateResponse> updateFuture = PlainActionFuture.newFuture();
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(mock(UpdateResponse.class));
            return null;
        }).when(client).update(eq(updateRequest), any());
        helper.updateData(systemConfig, updateRequest, updateFuture);
        assertNotNull(updateFuture.actionGet());

        DeleteRequest deleteRequest = new DeleteRequest("index", "id");
        PlainActionFuture<DeleteResponse> deleteFuture = PlainActionFuture.newFuture();
        doAnswer(invocation -> {
            ActionListener<DeleteResponse> listener = invocation.getArgument(1);
            listener.onResponse(mock(DeleteResponse.class));
            return null;
        }).when(client).delete(eq(deleteRequest), any());
        helper.deleteData(systemConfig, deleteRequest, deleteFuture);
        assertNotNull(deleteFuture.actionGet());
    }

    public void testDeleteIndexAndBulk() {
        MemoryConfiguration configuration = MemoryConfiguration.builder().indexPrefix("prefix").build();
        DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest("index");
        BulkRequest bulkRequest = new BulkRequest();

        AdminClient adminClient = mock(AdminClient.class);
        ClusterAdminClient clusterAdminClient = mock(ClusterAdminClient.class);
        IndicesAdminClient indicesAdminClient = mock(IndicesAdminClient.class);
        when(client.admin()).thenReturn(adminClient);
        when(adminClient.cluster()).thenReturn(clusterAdminClient);
        when(adminClient.indices()).thenReturn(indicesAdminClient);

        PlainActionFuture<AcknowledgedResponse> deleteIndexFuture = PlainActionFuture.newFuture();
        doAnswer(invocation -> {
            ActionListener<AcknowledgedResponse> listener = invocation.getArgument(1);
            listener.onResponse(mock(AcknowledgedResponse.class));
            return null;
        }).when(indicesAdminClient).delete(eq(deleteIndexRequest), any());
        helper.deleteIndex(configuration, deleteIndexRequest, deleteIndexFuture);
        assertNotNull(deleteIndexFuture.actionGet());

        PlainActionFuture<BulkResponse> bulkFuture = PlainActionFuture.newFuture();
        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(1);
            listener.onResponse(mock(BulkResponse.class));
            return null;
        }).when(client).bulk(eq(bulkRequest), any());
        helper.bulkIngestData(configuration, bulkRequest, bulkFuture);
        assertNotNull(bulkFuture.actionGet());
    }

    public void testDeleteIndexAndBulkWithSystemIndex() {
        // Test with system index
        MemoryConfiguration systemConfig = MemoryConfiguration.builder().indexPrefix("prefix").useSystemIndex(true).build();
        DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest("index");
        BulkRequest bulkRequest = new BulkRequest();

        AdminClient adminClient = mock(AdminClient.class);
        IndicesAdminClient indicesAdminClient = mock(IndicesAdminClient.class);
        when(client.admin()).thenReturn(adminClient);
        when(adminClient.indices()).thenReturn(indicesAdminClient);

        PlainActionFuture<AcknowledgedResponse> deleteIndexFuture = PlainActionFuture.newFuture();
        doAnswer(invocation -> {
            ActionListener<AcknowledgedResponse> listener = invocation.getArgument(1);
            listener.onResponse(mock(AcknowledgedResponse.class));
            return null;
        }).when(indicesAdminClient).delete(eq(deleteIndexRequest), any());
        helper.deleteIndex(systemConfig, deleteIndexRequest, deleteIndexFuture);
        assertNotNull(deleteIndexFuture.actionGet());

        PlainActionFuture<BulkResponse> bulkFuture = PlainActionFuture.newFuture();
        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(1);
            listener.onResponse(mock(BulkResponse.class));
            return null;
        }).when(client).bulk(eq(bulkRequest), any());
        helper.bulkIngestData(systemConfig, bulkRequest, bulkFuture);
        assertNotNull(bulkFuture.actionGet());
    }

    public void testBulkIngestData() {
        MemoryConfiguration configuration = MemoryConfiguration.builder().indexPrefix("prefix").build();
        BulkRequest bulkRequest = new BulkRequest();

        PlainActionFuture<BulkResponse> bulkFuture = PlainActionFuture.newFuture();
        doAnswer(invocation -> {
            ActionListener<BulkResponse> listener = invocation.getArgument(1);
            listener.onResponse(mock(BulkResponse.class));
            return null;
        }).when(client).bulk(eq(bulkRequest), any());
        helper.bulkIngestData(configuration, bulkRequest, bulkFuture);
        assertNotNull(bulkFuture.actionGet());
    }

    public void testDeleteByQuery() {
        MemoryConfiguration configuration = MemoryConfiguration.builder().indexPrefix("prefix").build();
        DeleteByQueryRequest request = new DeleteByQueryRequest("index");
        PlainActionFuture<BulkByScrollResponse> future = PlainActionFuture.newFuture();

        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(mock(BulkByScrollResponse.class));
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), eq(request), any());

        helper.deleteDataByQuery(configuration, request, future);
        assertNotNull(future.actionGet());
    }

    public void testDeleteByQueryWithSystemIndex() {
        // Test delete by query with system index
        MemoryConfiguration systemConfig = MemoryConfiguration.builder().indexPrefix("prefix").useSystemIndex(true).build();
        DeleteByQueryRequest request = new DeleteByQueryRequest("index");
        PlainActionFuture<BulkByScrollResponse> future = PlainActionFuture.newFuture();

        doAnswer(invocation -> {
            ActionListener<BulkByScrollResponse> listener = invocation.getArgument(2);
            listener.onResponse(mock(BulkByScrollResponse.class));
            return null;
        }).when(client).execute(eq(DeleteByQueryAction.INSTANCE), eq(request), any());

        helper.deleteDataByQuery(systemConfig, request, future);
        assertNotNull(future.actionGet());
    }

    public void testFiltersAndAdminHelpers() {
        User user = new User("alice", Arrays.asList("role1"), Collections.emptyList(), Map.of());
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        helper.addUserBackendRolesFilter(user, searchSourceBuilder);
        assertTrue(searchSourceBuilder.query() instanceof BoolQueryBuilder);

        // Test with existing BoolQueryBuilder
        SearchSourceBuilder builderWithBoolQuery = new SearchSourceBuilder();
        builderWithBoolQuery.query(QueryBuilders.boolQuery().must(QueryBuilders.matchAllQuery()));
        helper.addUserBackendRolesFilter(user, builderWithBoolQuery);
        assertTrue(builderWithBoolQuery.query() instanceof BoolQueryBuilder);

        // Test with existing non-BoolQueryBuilder
        SearchSourceBuilder builderWithMatchQuery = new SearchSourceBuilder();
        builderWithMatchQuery.query(QueryBuilders.matchAllQuery());
        helper.addUserBackendRolesFilter(user, builderWithMatchQuery);
        assertTrue(builderWithMatchQuery.query() instanceof BoolQueryBuilder);

        SearchSourceBuilder ownerBuilder = new SearchSourceBuilder();
        helper.addOwnerIdFilter(user, ownerBuilder);
        assertTrue(ownerBuilder.query() instanceof BoolQueryBuilder);

        QueryBuilder matchAll = QueryBuilders.matchAllQuery();
        QueryBuilder filtered = helper.addOwnerIdFilter(user, matchAll);
        assertTrue(filtered instanceof BoolQueryBuilder);

        // Test addOwnerIdFilter with null user (security disabled)
        QueryBuilder filteredWithNullUser = helper.addOwnerIdFilter(null, matchAll);
        assertEquals(matchAll, filteredWithNullUser);

        // Test addOwnerIdFilter with admin user
        User admin = new User("admin", Collections.emptyList(), Arrays.asList("all_access"), Map.of());
        QueryBuilder filteredWithAdmin = helper.addOwnerIdFilter(admin, matchAll);
        assertEquals(matchAll, filteredWithAdmin);

        SearchSourceBuilder containerBuilder = new SearchSourceBuilder();
        helper.addContainerIdFilter("container", containerBuilder);
        assertTrue(containerBuilder.query() instanceof BoolQueryBuilder);

        // Test addContainerIdFilter with null/blank containerId
        SearchSourceBuilder builderWithNullId = new SearchSourceBuilder();
        helper.addContainerIdFilter(null, builderWithNullId);
        assertNull(builderWithNullId.query());

        SearchSourceBuilder builderWithBlankId = new SearchSourceBuilder();
        helper.addContainerIdFilter("", builderWithBlankId);
        assertNull(builderWithBlankId.query());

        QueryBuilder containerFiltered = helper.addContainerIdFilter("container", matchAll);
        assertTrue(containerFiltered instanceof BoolQueryBuilder);

        // Test addContainerIdFilter QueryBuilder with null/blank containerId
        QueryBuilder filteredWithNullId = helper.addContainerIdFilter(null, matchAll);
        assertEquals(matchAll, filteredWithNullId);

        QueryBuilder filteredWithBlankId = helper.addContainerIdFilter("", matchAll);
        assertEquals(matchAll, filteredWithBlankId);

        assertTrue(helper.isAdminUser(admin));
        assertFalse(helper.isAdminUser(user));

        // Test isAdminUser with null user
        assertFalse(helper.isAdminUser(null));

        // Test isAdminUser with empty roles
        User userWithEmptyRoles = new User("user", Collections.emptyList(), Collections.emptyList(), Map.of());
        assertFalse(helper.isAdminUser(userWithEmptyRoles));

        // Test isAdminUser with null roles
        User userWithNullRoles = new User("user", null, null, Map.of());
        assertFalse(helper.isAdminUser(userWithNullRoles));

        assertEquals("alice", helper.getOwnerId(user));
        assertNull(helper.getOwnerId(null));
    }

    public void testCountContainersWithPrefix() {
        // Test with null prefix
        PlainActionFuture<Long> nullFuture = PlainActionFuture.newFuture();
        helper.countContainersWithPrefix(null, null, nullFuture);
        assertEquals(0L, nullFuture.actionGet().longValue());

        // Test with blank prefix
        PlainActionFuture<Long> blankFuture = PlainActionFuture.newFuture();
        helper.countContainersWithPrefix("", null, blankFuture);
        assertEquals(0L, blankFuture.actionGet().longValue());

        SearchResponse searchResponse = createSearchResponse(3);
        SearchDataObjectResponse response = new SearchDataObjectResponse(searchResponse);
        when(sdkClient.searchDataObjectAsync(any())).thenReturn(CompletableFuture.completedFuture(response));

        PlainActionFuture<Long> future = PlainActionFuture.newFuture();
        helper.countContainersWithPrefix("prefix", null, future);
        assertEquals(3L, future.actionGet().longValue());

        // Test with tenant ID
        PlainActionFuture<Long> tenantFuture = PlainActionFuture.newFuture();
        helper.countContainersWithPrefix("prefix", "tenant123", tenantFuture);
        assertEquals(3L, tenantFuture.actionGet().longValue());

        CompletableFuture<SearchDataObjectResponse> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("fail"));
        when(sdkClient.searchDataObjectAsync(any())).thenReturn(failed);

        PlainActionFuture<Long> failure = PlainActionFuture.newFuture();
        helper.countContainersWithPrefix("prefix", null, failure);
        RuntimeException exception = expectThrows(RuntimeException.class, failure::actionGet);
        assertEquals("fail", exception.getMessage());
    }

    private MLMemoryContainer createContainer() {
        return createContainerBuilder().build();
    }

    private MLMemoryContainer.MLMemoryContainerBuilder createContainerBuilder() {
        MemoryConfiguration configuration = MemoryConfiguration.builder().indexPrefix("prefix").build();
        User owner = new User("owner", Collections.emptyList(), Collections.emptyList(), Map.of());
        return MLMemoryContainer
            .builder()
            .name("container")
            .description("desc")
            .owner(owner)
            .configuration(configuration)
            .backendRoles(Arrays.asList("roleA", "roleB"));
    }

    private String containerToJson(MLMemoryContainer container) throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder();
        container.toXContent(builder, ToXContent.EMPTY_PARAMS);
        return BytesReference.bytes(builder).utf8ToString();
    }

    private SearchResponse createSearchResponse(long totalHits) {
        SearchHit[] hits = new SearchHit[0];
        SearchHits searchHits = new SearchHits(hits, new TotalHits(totalHits, TotalHits.Relation.EQUAL_TO), 1.0f);
        org.opensearch.search.internal.InternalSearchResponse internal = new org.opensearch.search.internal.InternalSearchResponse(
            searchHits,
            InternalAggregations.EMPTY,
            new Suggest(Collections.emptyList()),
            new SearchProfileShardResults(Collections.emptyMap()),
            false,
            false,
            1
        );
        return new SearchResponse(
            internal,
            "",
            1,
            1,
            0,
            0,
            org.opensearch.action.search.ShardSearchFailure.EMPTY_ARRAY,
            SearchResponse.Clusters.EMPTY
        );
    }
}
