/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import org.apache.lucene.search.TotalHits;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.client.Client;
import org.opensearch.common.action.ActionFuture;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.SearchModule;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.search.internal.InternalSearchResponse;
import org.opensearch.search.profile.SearchProfileShardResults;
import org.opensearch.search.suggest.Suggest;
import org.opensearch.threadpool.ThreadPool;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class MLGuardTests {

    NamedXContentRegistry xContentRegistry;
    @Mock
    Client client;
    @Mock
    ThreadPool threadPool;
    ThreadContext threadContext;

    StopWords stopWords;
    String[] regex;
    List<Pattern> regexPatterns;
    Guardrail inputGuardrail;
    Guardrail outputGuardrail;
    Guardrails guardrails;
    MLGuard mlGuard;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        xContentRegistry = new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents());
        Settings settings = Settings.builder().build();
        this.threadContext = new ThreadContext(settings);
        when(this.client.threadPool()).thenReturn(this.threadPool);
        when(this.threadPool.getThreadContext()).thenReturn(this.threadContext);

        stopWords = new StopWords("test_index", List.of("test_field").toArray(new String[0]));
        regex = List.of("(.|\n)*stop words(.|\n)*").toArray(new String[0]);
        regexPatterns = List.of(Pattern.compile("(.|\n)*stop words(.|\n)*"));
        inputGuardrail = new Guardrail(List.of(stopWords), regex);
        outputGuardrail = new Guardrail(List.of(stopWords), regex);
        guardrails = new Guardrails("test_type", inputGuardrail, outputGuardrail);
        mlGuard = new MLGuard(guardrails, xContentRegistry, client);
    }

    @Test
    public void validateInput() {
        String input = "\n\nHuman:hello stop words.\n\nAssistant:";
        Boolean res = mlGuard.validate(input, MLGuard.Type.INPUT);

        Assert.assertFalse(res);
    }

    @Test
    public void validateInitializedStopWordsEmpty() {
        stopWords = new StopWords(null, null);
        regex = List.of("(.|\n)*stop words(.|\n)*").toArray(new String[0]);
        regexPatterns = List.of(Pattern.compile("(.|\n)*stop words(.|\n)*"));
        inputGuardrail = new Guardrail(List.of(stopWords), regex);
        outputGuardrail = new Guardrail(List.of(stopWords), regex);
        guardrails = new Guardrails("test_type", inputGuardrail, outputGuardrail);
        mlGuard = new MLGuard(guardrails, xContentRegistry, client);

        String input = "\n\nHuman:hello good words.\n\nAssistant:";
        Boolean res = mlGuard.validate(input, MLGuard.Type.INPUT);
        Assert.assertTrue(res);
    }

    @Test
    public void validateOutput() {
        String input = "\n\nHuman:hello stop words.\n\nAssistant:";
        Boolean res = mlGuard.validate(input, MLGuard.Type.OUTPUT);

        Assert.assertFalse(res);
    }

    @Test
    public void validateRegexListSuccess() {
        String input = "\n\nHuman:hello good words.\n\nAssistant:";
        Boolean res = mlGuard.validateRegexList(input, regexPatterns);

        Assert.assertTrue(res);
    }

    @Test
    public void validateRegexListFailed() {
        String input = "\n\nHuman:hello stop words.\n\nAssistant:";
        Boolean res = mlGuard.validateRegexList(input, regexPatterns);

        Assert.assertFalse(res);
    }

    @Test
    public void validateRegexListNull() {
        String input = "\n\nHuman:hello good words.\n\nAssistant:";
        Boolean res = mlGuard.validateRegexList(input, null);

        Assert.assertTrue(res);
    }

    @Test
    public void validateRegexListEmpty() {
        String input = "\n\nHuman:hello good words.\n\nAssistant:";
        Boolean res = mlGuard.validateRegexList(input, List.of());

        Assert.assertTrue(res);
    }

    @Test
    public void validateRegexSuccess() {
        String input = "\n\nHuman:hello good words.\n\nAssistant:";
        Boolean res = mlGuard.validateRegex(input, regexPatterns.get(0));

        Assert.assertTrue(res);
    }

    @Test
    public void validateRegexFailed() {
        String input = "\n\nHuman:hello stop words.\n\nAssistant:";
        Boolean res = mlGuard.validateRegex(input, regexPatterns.get(0));

        Assert.assertFalse(res);
    }

    @Test
    public void validateStopWords() throws IOException {
        Map<String, List<String>> stopWordsIndices = Map.of("test_index", List.of("test_field"));
        SearchResponse searchResponse = createSearchResponse(1);
        ActionFuture<SearchResponse> future = createSearchResponseFuture(searchResponse);
        when(this.client.search(any())).thenReturn(future);

        Boolean res = mlGuard.validateStopWords("hello world", stopWordsIndices);
        Assert.assertTrue(res);
    }

    @Test
    public void validateStopWordsNull() {
        Boolean res = mlGuard.validateStopWords("hello world", null);
        Assert.assertTrue(res);
    }

    @Test
    public void validateStopWordsEmpty() {
        Boolean res = mlGuard.validateStopWords("hello world", Map.of());
        Assert.assertTrue(res);
    }

    @Test
    public void validateStopWordsSingleIndex() throws IOException {
        SearchResponse searchResponse = createSearchResponse(1);
        ActionFuture<SearchResponse> future = createSearchResponseFuture(searchResponse);
        when(this.client.search(any())).thenReturn(future);

        Boolean res = mlGuard.validateStopWordsSingleIndex("hello world", "test_index", List.of("test_field"));
        Assert.assertTrue(res);
    }

    private SearchResponse createSearchResponse(int size) throws IOException {
        XContentBuilder content = guardrails.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        SearchHit[] hits = new SearchHit[size];
        if (size > 0) {
            hits[0] = new SearchHit(0).sourceRef(BytesReference.bytes(content));
        }
        return new SearchResponse(
                new InternalSearchResponse(
                        new SearchHits(hits, new TotalHits(size, TotalHits.Relation.EQUAL_TO), 1.0f),
                        InternalAggregations.EMPTY,
                        new Suggest(Collections.emptyList()),
                        new SearchProfileShardResults(Collections.emptyMap()),
                        false,
                        false,
                        1
                ),
                "",
                5,
                5,
                0,
                100,
                ShardSearchFailure.EMPTY_ARRAY,
                SearchResponse.Clusters.EMPTY
        );
    }

    private ActionFuture<SearchResponse> createSearchResponseFuture(SearchResponse searchResponse) {
        return new ActionFuture<>() {
            @Override
            public SearchResponse actionGet() {
                return searchResponse;
            }

            @Override
            public SearchResponse actionGet(String timeout) {
                return searchResponse;
            }

            @Override
            public SearchResponse actionGet(long timeoutMillis) {
                return searchResponse;
            }

            @Override
            public SearchResponse actionGet(long timeout, TimeUnit unit) {
                return searchResponse;
            }

            @Override
            public SearchResponse actionGet(TimeValue timeout) {
                return searchResponse;
            }

            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return false;
            }

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public boolean isDone() {
                return false;
            }

            @Override
            public SearchResponse get() throws InterruptedException, ExecutionException {
                return searchResponse;
            }

            @Override
            public SearchResponse get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                return searchResponse;
            }
        };
    }
}