/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.indexInsight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.common.indexInsight.IndexInsightTestHelper.mockGetSuccess;
import static org.opensearch.ml.common.indexInsight.IndexInsightTestHelper.mockUpdateSuccess;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.opensearch.action.admin.indices.mapping.get.GetMappingsRequest;
import org.opensearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.core.action.ActionListener;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.search.aggregations.Aggregations;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.search.aggregations.bucket.filter.InternalFilters;
import org.opensearch.search.aggregations.bucket.sampler.InternalSampler;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.transport.client.AdminClient;
import org.opensearch.transport.client.Client;
import org.opensearch.transport.client.IndicesAdminClient;

public class StatisticalDataTaskTests {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Mock
    public SdkClient sdkClient;

    private Client setupBasicClientMocks() {
        Client client = mock(Client.class);
        AdminClient adminClient = mock(AdminClient.class);
        IndicesAdminClient indicesAdminClient = mock(IndicesAdminClient.class);

        when(client.admin()).thenReturn(adminClient);
        when(adminClient.indices()).thenReturn(indicesAdminClient);

        return client;
    }

    private GetMappingsResponse setupMappingResponse() {
        GetMappingsResponse getMappingsResponse = mock(GetMappingsResponse.class);
        Map<String, MappingMetadata> mappings = new HashMap<>();
        MappingMetadata mappingMetadata = mock(MappingMetadata.class);
        Map<String, Object> sourceAsMap = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();
        properties.put("field1", Map.of("type", "text"));
        sourceAsMap.put("properties", properties);

        mappings.put("test-index", mappingMetadata);
        when(getMappingsResponse.getMappings()).thenReturn(mappings);
        when(mappingMetadata.getSourceAsMap()).thenReturn(sourceAsMap);

        return getMappingsResponse;
    }

    private void setupGetMappingsCall(Client client, GetMappingsResponse response) {
        AdminClient adminClient = client.admin();
        IndicesAdminClient indicesAdminClient = adminClient.indices();

        doAnswer(invocation -> {
            ActionListener<GetMappingsResponse> responseListener = invocation.getArgument(1);
            responseListener.onResponse(response);
            return null;
        }).when(indicesAdminClient).getMappings(any(GetMappingsRequest.class), any());
    }

    @Test
    public void testGetTaskType() {
        Client client = mock(Client.class);
        StatisticalDataTask task = new StatisticalDataTask("test-index", client, sdkClient);

        assertEquals(MLIndexInsightType.STATISTICAL_DATA, task.getTaskType());
    }

    @Test
    public void testGetSourceIndex() {
        Client client = mock(Client.class);
        StatisticalDataTask task = new StatisticalDataTask("test-index", client, sdkClient);

        assertEquals("test-index", task.getSourceIndex());
    }

    @Test
    public void testGetClient() {
        Client client = mock(Client.class);
        StatisticalDataTask task = new StatisticalDataTask("test-index", client, sdkClient);

        assertEquals(client, task.getClient());
    }

    @Test
    public void testGetPrerequisites() {
        Client client = mock(Client.class);
        StatisticalDataTask task = new StatisticalDataTask("test-index", client, sdkClient);

        assertTrue(task.getPrerequisites().isEmpty());
    }

    @Test
    public void testCreatePrerequisiteTask_ThrowsException() {
        Client client = mock(Client.class);
        StatisticalDataTask task = new StatisticalDataTask("test-index", client, sdkClient);

        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("StatisticalDataTask has no prerequisites");
        task.createPrerequisiteTask(MLIndexInsightType.FIELD_DESCRIPTION);
    }

    @Test
    public void testBuildQuery() {
        Client client = mock(Client.class);
        StatisticalDataTask task = new StatisticalDataTask("test-index", client, sdkClient);

        Map<String, String> fields = new HashMap<>();
        fields.put("text_field", "text");
        fields.put("keyword_field", "keyword");
        fields.put("number_field", "integer");

        SearchSourceBuilder query = task.buildQuery(fields);

        assertTrue(query.toString().contains("sample"));
        assertTrue(query.toString().contains("example_docs"));
    }

    @Test
    public void testRunTask_CallsGetMappings() {
        Client client = setupBasicClientMocks();
        AdminClient adminClient = client.admin();
        IndicesAdminClient indicesAdminClient = adminClient.indices();

        doAnswer(invocation -> { return null; }).when(indicesAdminClient).getMappings(any(GetMappingsRequest.class), any());

        StatisticalDataTask task = new StatisticalDataTask("test-index", client, sdkClient);
        ActionListener<IndexInsight> listener = mock(ActionListener.class);

        task.runTask("storage-index", "tenant-id", listener);

        verify(indicesAdminClient).getMappings(any(GetMappingsRequest.class), any());
    }

    @Test
    public void testRunTask_WithEmptyMappings() {
        Client client = setupBasicClientMocks();
        GetMappingsResponse getMappingsResponse = mock(GetMappingsResponse.class);
        ActionListener<IndexInsight> listener = mock(ActionListener.class);

        when(getMappingsResponse.getMappings()).thenReturn(new HashMap<>());
        setupGetMappingsCall(client, getMappingsResponse);

        StatisticalDataTask task = new StatisticalDataTask("test-index", client, sdkClient);
        task.runTask("storage-index", "tenant-id", listener);

        verify(listener).onFailure(any(IllegalArgumentException.class));
    }

    @Test
    public void testRunTask_WithSearchFailure() {
        Client client = setupBasicClientMocks();
        GetMappingsResponse getMappingsResponse = setupMappingResponse();
        ActionListener<IndexInsight> listener = mock(ActionListener.class);

        setupGetMappingsCall(client, getMappingsResponse);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> responseListener = invocation.getArgument(1);
            responseListener.onFailure(new RuntimeException("Search failed"));
            return null;
        }).when(client).search(any(SearchRequest.class), any());
        sdkClient = mock(SdkClient.class);

        when(sdkClient.searchDataObjectAsync(any())).thenThrow(new RuntimeException("Search failed"));
        mockGetSuccess(sdkClient, "");
        mockUpdateSuccess(sdkClient);

        StatisticalDataTask task = new StatisticalDataTask("test-index", client, sdkClient);
        task.runTask("storage-index", "tenant-id", listener);

        verify(listener).onFailure(any(RuntimeException.class));
    }

    @Test
    public void testHandlePatternMatchedDoc_RunWithoutStoring() {
        Client client = setupBasicClientMocks();
        GetMappingsResponse getMappingsResponse = setupMappingResponse();
        ActionListener<IndexInsight> listener = mock(ActionListener.class);

        Map<String, Object> patternSource = new HashMap<>();
        patternSource.put(IndexInsight.STATUS_FIELD, "COMPLETED");
        patternSource.put(IndexInsight.CONTENT_FIELD, "{\"test\": \"data\"}");

        setupGetMappingsCall(client, getMappingsResponse);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> responseListener = invocation.getArgument(1);
            SearchResponse searchResponse = mock(SearchResponse.class);
            responseListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), any());

        StatisticalDataTask task = spy(new StatisticalDataTask("test-index", client, sdkClient));
        task.handlePatternMatchedDoc(patternSource, "storage-index", "tenant-id", listener);

        verify(task).runTask(eq("storage-index"), eq("tenant-id"), eq(listener), eq(false));
    }

    @Test
    public void testParseSearchResult_WithEmptyAggregations() throws Exception {
        Client client = mock(Client.class);
        StatisticalDataTask task = new StatisticalDataTask("test-index", client, sdkClient);

        Map<String, String> fieldsToType = Map.of("field1", "text");
        Set<String> filteredNames = Set.of("field1");

        InternalSampler mockSampler = mock(InternalSampler.class);
        when(mockSampler.getName()).thenReturn("sample");
        when(mockSampler.getAggregations()).thenReturn(InternalAggregations.EMPTY);

        SearchResponse mockResponse = mock(SearchResponse.class);
        when(mockResponse.getAggregations()).thenReturn(new Aggregations(Arrays.asList(mockSampler)));

        Method parseMethod = StatisticalDataTask.class.getDeclaredMethod("parseSearchResult", Map.class, Set.class, SearchResponse.class);
        parseMethod.setAccessible(true);

        Map<String, Object> result = (Map<String, Object>) parseMethod.invoke(task, fieldsToType, filteredNames, mockResponse);

        assertNotNull(result);
        assertTrue(result.containsKey(StatisticalDataTask.IMPORTANT_COLUMN_KEYWORD));
    }

    @Test
    public void testFilterColumns_WithFiltersAggregation() throws Exception {
        Client client = mock(Client.class);
        StatisticalDataTask task = new StatisticalDataTask("test-index", client, sdkClient);

        Map<String, String> fieldsToType = Map.of("field1", "text");

        InternalFilters mockFilters = mock(InternalFilters.class);
        when(mockFilters.getName()).thenReturn("not_null");
        when(mockFilters.getBuckets()).thenReturn(Arrays.asList());

        InternalSampler mockSampler = mock(InternalSampler.class);
        when(mockSampler.getName()).thenReturn("sample");
        when(mockSampler.getDocCount()).thenReturn(1000L);
        when(mockSampler.getAggregations()).thenReturn(new InternalAggregations(Arrays.asList(mockFilters)));

        SearchResponse mockResponse = mock(SearchResponse.class);
        when(mockResponse.getAggregations()).thenReturn(new Aggregations(Arrays.asList(mockSampler)));

        Method filterMethod = StatisticalDataTask.class.getDeclaredMethod("filterColumns", Map.class, SearchResponse.class);
        filterMethod.setAccessible(true);

        Set<String> result = (Set<String>) filterMethod.invoke(task, fieldsToType, mockResponse);

        assertNotNull(result);
    }
}
