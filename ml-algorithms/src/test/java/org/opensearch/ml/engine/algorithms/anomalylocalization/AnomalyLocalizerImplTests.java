/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.anomalylocalization;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionListener;
import org.opensearch.action.search.MultiSearchResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.search.aggregations.AggregationBuilder;
import org.opensearch.search.aggregations.AggregationBuilders;
import org.opensearch.search.aggregations.Aggregations;
import org.opensearch.search.aggregations.bucket.composite.CompositeAggregation;
import org.opensearch.search.aggregations.bucket.filter.Filters;
import org.opensearch.search.aggregations.metrics.NumericMetricsAggregation.SingleValue;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AnomalyLocalizerImplTests {

    @Mock
    private Client client;

    @Mock
    private ActionListener<AnomalyLocalizationOutput> outputListener;

    private Settings settings;

    private AnomalyLocalizerImpl anomalyLocalizer;

    private String indexName = "indexName";
    private String attributeFieldNameOne = "attributeOne";
    private AggregationBuilder agg = AggregationBuilders.count("count").field("field");
    private String timeFieldName = "timeFieldName";
    private long startTime = 0;
    private long endTime = 2;
    private long minTimeInterval = 1;
    private int numOutput = 1;

    private AnomalyLocalizationInput input;
    private AnomalyLocalizationOutput expectedOutput;
    @Mock
    private SingleValue valueOne;
    @Mock
    private SingleValue valueTwo;
    @Mock
    private SingleValue valueThree;
    private AnomalyLocalizationOutput.Bucket expectedBucketOne;
    private AnomalyLocalizationOutput.Bucket expectedBucketTwo;
    private AnomalyLocalizationOutput.Entity entity;

    @Before
    @SuppressWarnings("unchecked")
    public void setup() {
        MockitoAnnotations.openMocks(this);
        settings = Settings.builder().build();
        anomalyLocalizer = new AnomalyLocalizerImpl(client, settings);

        input = new AnomalyLocalizationInput(indexName, Arrays.asList(attributeFieldNameOne), Arrays.asList(agg), timeFieldName,
                startTime, endTime,
                minTimeInterval, numOutput, Optional.empty(), Optional.empty());

        when(valueOne.value()).thenReturn(0.);
        when(valueOne.getName()).thenReturn(agg.getName());
        SearchResponse respOne = mock(SearchResponse.class);
        when(respOne.getAggregations()).thenReturn(new Aggregations(Arrays.asList(valueOne)));
        MultiSearchResponse.Item itemOne = new MultiSearchResponse.Item(respOne, null);
        when(valueTwo.value()).thenReturn(10.);
        when(valueTwo.getName()).thenReturn(agg.getName());
        SearchResponse respTwo = mock(SearchResponse.class);
        when(respTwo.getAggregations()).thenReturn(new Aggregations(Arrays.asList(valueTwo)));
        MultiSearchResponse.Item itemTwo = new MultiSearchResponse.Item(respTwo, null);
        MultiSearchResponse multiSearchResponse = new MultiSearchResponse(new MultiSearchResponse.Item[]{itemOne, itemTwo}, 0);
        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            ActionListener<MultiSearchResponse> listener = (ActionListener<MultiSearchResponse>) args[1];
            listener.onResponse(multiSearchResponse);
            return null;
        }
        ).when(client).multiSearch(any(), any());

        CompositeAggregation.Bucket bucketOne = mock(CompositeAggregation.Bucket.class);
        Map<String, Object> bucketOneKey = new HashMap<>();
        String bucketOneKeyValue = "bucketOneKeyValue";
        bucketOneKey.put(attributeFieldNameOne, bucketOneKeyValue);
        when(bucketOne.getKey()).thenReturn(bucketOneKey);
        when(bucketOne.getAggregations()).thenReturn(new Aggregations(Arrays.asList(valueOne)));
        CompositeAggregation compositeOne = mock(CompositeAggregation.class);
        when(compositeOne.getName()).thenReturn(agg.getName());
        doReturn(Arrays.asList(bucketOne)).when(compositeOne).getBuckets();
        when(compositeOne.afterKey()).thenReturn(bucketOneKey);
        SearchResponse respBucketOne = mock(SearchResponse.class);
        when(respBucketOne.getAggregations()).thenReturn(new Aggregations(Arrays.asList(compositeOne))).thenReturn(new Aggregations(Collections.emptyList()));

        CompositeAggregation.Bucket bucketOneNew = mock(CompositeAggregation.Bucket.class);
        when(bucketOneNew.getKey()).thenReturn(bucketOneKey);
        when(bucketOneNew.getAggregations()).thenReturn(new Aggregations(Arrays.asList(valueTwo)));
        Map<String, Object> bucketTwoKey = new HashMap<>();
        String bucketTwoKeyValue = "bucketTwoKeyValue";
        bucketTwoKey.put(attributeFieldNameOne, bucketTwoKeyValue);
        when(valueThree.value()).thenReturn(0.);
        when(valueThree.getName()).thenReturn(agg.getName());
        CompositeAggregation.Bucket bucketTwoNew = mock(CompositeAggregation.Bucket.class);
        when(bucketTwoNew.getKey()).thenReturn(bucketTwoKey);
        when(bucketTwoNew.getAggregations()).thenReturn(new Aggregations(Arrays.asList(valueThree)));
        CompositeAggregation compositeTwo = mock(CompositeAggregation.class);
        when(compositeTwo.getName()).thenReturn(agg.getName());
        doReturn(Arrays.asList(bucketTwoNew, bucketOneNew, bucketTwoNew)).when(compositeTwo).getBuckets();
        when(compositeTwo.afterKey()).thenReturn(bucketOneKey);
        SearchResponse respBucketTwo = mock(SearchResponse.class);
        when(respBucketTwo.getAggregations()).thenReturn(new Aggregations(Arrays.asList(compositeTwo))).thenReturn(new Aggregations(Collections.emptyList()));

        Filters.Bucket filterBucketOne = mock(Filters.Bucket.class);
        when(filterBucketOne.getKeyAsString()).thenReturn(String.valueOf(0));
        when(filterBucketOne.getAggregations()).thenReturn(new Aggregations(Arrays.asList(valueOne)));
        Filters filters = mock(Filters.class);
        when(filters.getName()).thenReturn(agg.getName());
        doReturn(Arrays.asList(filterBucketOne)).when(filters).getBuckets();
        SearchResponse filtersResp = mock(SearchResponse.class);
        when(filtersResp.getAggregations()).thenReturn(new Aggregations(Arrays.asList(filters)));

        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            ActionListener<SearchResponse> listener = (ActionListener<SearchResponse>) args[1];
            listener.onResponse(respBucketOne);
            return null;
        }
        ).
                doAnswer(invocation -> {
                    Object[] args = invocation.getArguments();
                    ActionListener<SearchResponse> listener = (ActionListener<SearchResponse>) args[1];
                    listener.onResponse(respBucketOne);
                    return null;
                }
                ).
                doAnswer(invocation -> {
                    Object[] args = invocation.getArguments();
                    ActionListener<SearchResponse> listener = (ActionListener<SearchResponse>) args[1];
                    listener.onResponse(respBucketTwo);
                    return null;
                }
                ).
                doAnswer(invocation -> {
                    Object[] args = invocation.getArguments();
                    ActionListener<SearchResponse> listener = (ActionListener<SearchResponse>) args[1];
                    listener.onResponse(respBucketTwo);
                    return null;
                }
                ).
                doAnswer(invocation -> {
                    Object[] args = invocation.getArguments();
                    ActionListener<SearchResponse> listener = (ActionListener<SearchResponse>) args[1];
                    listener.onResponse(filtersResp);
                    return null;
                }
                ).
                when(client).search(any(), any());

        expectedOutput = new AnomalyLocalizationOutput();
        AnomalyLocalizationOutput.Result result = new AnomalyLocalizationOutput.Result();
        expectedBucketOne = new AnomalyLocalizationOutput.Bucket();
        expectedBucketOne.setStartTime(0);
        expectedBucketOne.setEndTime(1);
        expectedBucketOne.setOverallAggValue(0);
        expectedBucketTwo = new AnomalyLocalizationOutput.Bucket();
        expectedBucketTwo.setStartTime(1);
        expectedBucketTwo.setEndTime(2);
        expectedBucketTwo.setOverallAggValue(10);
        entity = new AnomalyLocalizationOutput.Entity();
        entity.setKey(Arrays.asList(bucketOneKeyValue));
        entity.setNewValue(valueTwo.value());
        entity.setBaseValue(valueOne.value());
        entity.setContributionValue(valueTwo.value());
        expectedBucketTwo.setEntities(Arrays.asList(entity));
        result.getBuckets().add(expectedBucketOne);
        result.getBuckets().add(expectedBucketTwo);
        expectedOutput.getResults().put(agg.getName(), result);
    }

    @Test
    public void testGetLocalizedResultsGivenNoAnomaly() {
        anomalyLocalizer.getLocalizationResults(input, outputListener);

        ArgumentCaptor<AnomalyLocalizationOutput> outputCaptor = ArgumentCaptor.forClass(AnomalyLocalizationOutput.class);
        verify(outputListener).onResponse(outputCaptor.capture());
        AnomalyLocalizationOutput actualOutput = outputCaptor.getValue();
        assertEquals(expectedOutput, actualOutput);
    }

    @Test
    public void testGetLocalizedResultsGivenAnomaly() {
        when(valueThree.value()).thenReturn(Double.NaN);
        input = new AnomalyLocalizationInput(indexName, Arrays.asList(attributeFieldNameOne), Arrays.asList(agg), timeFieldName,
                startTime, endTime,
                minTimeInterval, numOutput, Optional.of(1L), Optional.of(mock(QueryBuilder.class)));

        anomalyLocalizer.getLocalizationResults(input, outputListener);

        ArgumentCaptor<AnomalyLocalizationOutput> outputCaptor = ArgumentCaptor.forClass(AnomalyLocalizationOutput.class);
        verify(outputListener).onResponse(outputCaptor.capture());
        AnomalyLocalizationOutput actualOutput = outputCaptor.getValue();
        assertEquals(expectedOutput, actualOutput);
    }

    @Test(expected = RuntimeException.class)
    public void testGetLocalizedResultsForInvalidTimeRange() {
        input = new AnomalyLocalizationInput(indexName, Arrays.asList(attributeFieldNameOne), Arrays.asList(agg), timeFieldName,
                startTime, startTime,
                minTimeInterval, numOutput, Optional.empty(), Optional.empty());

        anomalyLocalizer.getLocalizationResults(input, outputListener);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetLocalizedResultsForSearchFailure() {
        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            ActionListener<MultiSearchResponse> listener = (ActionListener<MultiSearchResponse>) args[1];
            listener.onFailure(new RuntimeException());
            return null;
        }
        ).when(client).multiSearch(any(), any());

        anomalyLocalizer.getLocalizationResults(input, outputListener);

        ArgumentCaptor<Exception> outputCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(outputListener).onFailure(outputCaptor.capture());
    }

    @Test
    public void testGetLocalizedResultsOverallDecrease() {
        when(valueOne.value()).thenReturn(10.);
        when(valueTwo.value()).thenReturn(0.);
        when(valueThree.value()).thenReturn(11.);

        anomalyLocalizer.getLocalizationResults(input, outputListener);

        ArgumentCaptor<AnomalyLocalizationOutput> outputCaptor = ArgumentCaptor.forClass(AnomalyLocalizationOutput.class);
        verify(outputListener).onResponse(outputCaptor.capture());
        AnomalyLocalizationOutput actualOutput = outputCaptor.getValue();
        expectedBucketOne.setOverallAggValue(valueOne.value());
        expectedBucketTwo.setOverallAggValue(valueTwo.value());
        entity.setNewValue(valueTwo.value());
        entity.setBaseValue(valueOne.value());
        entity.setContributionValue(valueTwo.value() - valueOne.value());
        assertEquals(expectedOutput, actualOutput);
    }

    @Test
    public void testGetLocalizedResultsOverallUnchange() {
        when(valueOne.value()).thenReturn(0.);
        when(valueTwo.value()).thenReturn(0.);

        anomalyLocalizer.getLocalizationResults(input, outputListener);

        ArgumentCaptor<AnomalyLocalizationOutput> outputCaptor = ArgumentCaptor.forClass(AnomalyLocalizationOutput.class);
        verify(outputListener).onResponse(outputCaptor.capture());
        AnomalyLocalizationOutput actualOutput = outputCaptor.getValue();
        expectedBucketOne.setOverallAggValue(valueOne.value());
        expectedBucketOne.setEntities(null);
        expectedBucketTwo.setOverallAggValue(valueTwo.value());
        expectedBucketTwo.setEntities(null);
        assertEquals(expectedOutput, actualOutput);
    }

    @Test
    public void testGetLocalizedResultsFilterEntity() {
        input = new AnomalyLocalizationInput(indexName, Arrays.asList(attributeFieldNameOne), Arrays.asList(agg), timeFieldName,
                startTime, endTime,
                minTimeInterval, 2, Optional.empty(), Optional.empty());

        anomalyLocalizer.getLocalizationResults(input, outputListener);

        ArgumentCaptor<AnomalyLocalizationOutput> outputCaptor = ArgumentCaptor.forClass(AnomalyLocalizationOutput.class);
        verify(outputListener).onResponse(outputCaptor.capture());
        AnomalyLocalizationOutput actualOutput = outputCaptor.getValue();
        assertEquals(expectedOutput, actualOutput);
    }

    @Test
    public void testExecuteSucceed() {
        AnomalyLocalizationOutput actualOutput = (AnomalyLocalizationOutput) anomalyLocalizer.execute(input);

        assertEquals(expectedOutput, actualOutput);
    }

    @SuppressWarnings("unchecked")
    @Test(expected = RuntimeException.class)
    public void testExecuteFail() {
        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            ActionListener<MultiSearchResponse> listener = (ActionListener<MultiSearchResponse>) args[1];
            listener.onFailure(new RuntimeException());
            return null;
        }
        ).when(client).multiSearch(any(), any());
        anomalyLocalizer.execute(input);
    }

    @Test(expected = RuntimeException.class)
    public void testExecuteInterrupted() {
        Thread.currentThread().interrupt();
        anomalyLocalizer.execute(input);
    }
}


