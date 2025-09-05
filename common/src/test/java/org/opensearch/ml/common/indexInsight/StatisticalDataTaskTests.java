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
import static org.opensearch.ml.common.indexInsight.StatisticalDataTask.EXAMPLE_DOC_KEYWORD;
import static org.opensearch.ml.common.indexInsight.StatisticalDataTask.NOT_NULL_KEYWORD;
import static org.opensearch.ml.common.utils.StringUtils.gson;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.opensearch.action.admin.indices.mapping.get.GetMappingsRequest;
import org.opensearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.Aggregations;
import org.opensearch.search.aggregations.InternalAggregation;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.search.aggregations.bucket.filter.InternalFilters;
import org.opensearch.search.aggregations.bucket.sampler.InternalSampler;
import org.opensearch.search.aggregations.metrics.InternalTopHits;
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

        task.runTask("tenant-id", listener);

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
        task.runTask("tenant-id", listener);

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
        task.runTask("tenant-id", listener);

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
        task.handlePatternMatchedDoc(patternSource, "tenant-id", listener);

        verify(task).runTask(eq("tenant-id"), eq(listener), eq(false));
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

    @Test
    public void test_parseSearchResult() throws IOException {
        Client client = setupBasicClientMocks();
        GetMappingsResponse getMappingsResponse = setupMappingResponse();
        ActionListener<IndexInsight> listener = mock(ActionListener.class);

        setupGetMappingsCall(client, getMappingsResponse);
        StatisticalDataTask task = new StatisticalDataTask("test-index", client, sdkClient);
        SearchResponse searchResponse = mock(SearchResponse.class);

        XContentBuilder sourceContent = XContentBuilder
            .builder(XContentType.JSON.xContent())
            .startObject()
            .field(IndexInsight.INDEX_NAME_FIELD, "test-*")
            .endObject();

        SearchHit searchHit = new SearchHit(0, "pattern-doc", Map.of(), Map.of());
        searchHit.sourceRef(BytesReference.bytes(sourceContent));

        SearchHit[] hits = new SearchHit[] { searchHit };
        SearchHits searchHits = new SearchHits(hits, null, 0);

        // prepare sparse filter

        InternalSampler sampler = mock(InternalSampler.class);
        // Map<String, Aggregation> topAggregationMap = Map.of("sample", sampler);

        Aggregations aggregations = new Aggregations(List.of(sampler));

        when(sampler.getDocCount()).thenReturn(1L);
        when(sampler.getName()).thenReturn("sample");

        InternalFilters internalFilters = mock(InternalFilters.class);
        InternalFilters.InternalBucket bucket = mock(InternalFilters.InternalBucket.class);
        when(bucket.getKey()).thenReturn("field1_not_null");
        when(bucket.getDocCount()).thenReturn(1L);
        List<InternalFilters.InternalBucket> buckets = List.of(bucket);
        when(internalFilters.getBuckets()).thenReturn(buckets);
        when(internalFilters.getName()).thenReturn(NOT_NULL_KEYWORD);

        InternalTopHits internalTopHits = mock(InternalTopHits.class);
        when(internalTopHits.getName()).thenReturn(EXAMPLE_DOC_KEYWORD);
        when(internalTopHits.getHits()).thenReturn(searchHits);

        InternalAggregation uniqueAggregation = mock(InternalAggregation.class);
        when(uniqueAggregation.getName()).thenReturn("unique_terms_field1");
        when(uniqueAggregation.toString())
            .thenReturn(gson.toJson(Map.of("unique_terms_field1", Map.of("buckets", List.of(Map.of("key", "demo"))))));

        InternalAggregation uniqueCountAggregation = mock(InternalAggregation.class);
        when(uniqueCountAggregation.getName()).thenReturn("unique_count_field1");
        when(uniqueCountAggregation.toString()).thenReturn(gson.toJson(Map.of("unique_count_field1", Map.of("value_as_string", "demo2"))));

        InternalAggregation maxAggregation = mock(InternalAggregation.class);
        when(maxAggregation.getName()).thenReturn("max_value_field1");
        when(maxAggregation.toString()).thenReturn(gson.toJson(Map.of("max_value_field1", Map.of("value", "demo2"))));

        InternalAggregations sampleAggregations = new InternalAggregations(
            List.of(internalFilters, uniqueAggregation, uniqueCountAggregation, maxAggregation, internalTopHits)
        );
        when(sampler.getAggregations()).thenReturn(sampleAggregations);

        when(searchResponse.getAggregations()).thenReturn(aggregations);

        when(searchResponse.getHits()).thenReturn(searchHits);

        doAnswer(invocation -> {
            ActionListener<SearchResponse> responseListener = invocation.getArgument(1);
            responseListener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), any());
        sdkClient = mock(SdkClient.class);

        task.runTask("", listener, false);

        ArgumentCaptor<IndexInsight> argumentCaptor = ArgumentCaptor.forClass(IndexInsight.class);
        verify(listener).onResponse(argumentCaptor.capture());
        IndexInsight response = argumentCaptor.getValue();
        Map<String, Object> expectedContent = gson
            .fromJson(
                "{\"example_docs\":[{\"index_name\":\"test-*\"}],\"important_column_and_distribution\":{\"field1\":{\"type\":\"text\",\"unique_count\":\"demo2\",\"unique_terms\":[\"demo\"],\"max_value\":\"demo2\"}}}",
                Map.class
            );
        assertEquals(expectedContent, gson.fromJson(response.getContent(), Map.class));
    }
}
