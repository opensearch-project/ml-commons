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
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.TestHelper;
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
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class LocalRegexGuardrailTests {
    NamedXContentRegistry xContentRegistry;
    @Mock
    Client client;
    @Mock
    ThreadPool threadPool;
    ThreadContext threadContext;

    StopWords stopWords;
    String[] regex;
    List<Pattern> regexPatterns;
    LocalRegexGuardrail localRegexGuardrail;

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
        localRegexGuardrail = new LocalRegexGuardrail(List.of(stopWords), regex);
    }

    @Test
    public void writeTo() throws IOException {
        LocalRegexGuardrail localRegexGuardrail = new LocalRegexGuardrail(List.of(stopWords), regex);
        BytesStreamOutput output = new BytesStreamOutput();
        localRegexGuardrail.writeTo(output);
        LocalRegexGuardrail localRegexGuardrail1 = new LocalRegexGuardrail(output.bytes().streamInput());

        Assert.assertArrayEquals(localRegexGuardrail.getStopWords().toArray(), localRegexGuardrail1.getStopWords().toArray());
        Assert.assertArrayEquals(localRegexGuardrail.getRegex(), localRegexGuardrail1.getRegex());
    }

    @Test
    public void toXContent() throws IOException {
        LocalRegexGuardrail localRegexGuardrail = new LocalRegexGuardrail(List.of(stopWords), regex);
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        localRegexGuardrail.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);

        Assert.assertEquals("{\"stop_words\":[{\"index_name\":\"test_index\",\"source_fields\":[\"test_field\"]}],\"regex\":[\"(.|\\n)*stop words(.|\\n)*\"]}", content);
    }

    @Test
    public void parse() throws IOException {
        String jsonStr = "{\"stop_words\":[{\"index_name\":\"test_index\",\"source_fields\":[\"test_field\"]}],\"regex\":[\"(.|\\n)*stop words(.|\\n)*\"]}";
        XContentParser parser = XContentType.JSON.xContent().createParser(new NamedXContentRegistry(new SearchModule(Settings.EMPTY,
                Collections.emptyList()).getNamedXContents()), null, jsonStr);
        parser.nextToken();
        LocalRegexGuardrail localRegexGuardrail = LocalRegexGuardrail.parse(parser);

        Assert.assertArrayEquals(localRegexGuardrail.getStopWords().toArray(), List.of(stopWords).toArray());
        Assert.assertArrayEquals(localRegexGuardrail.getRegex(), regex);
    }

    @Test
    public void validateRegexListSuccess() {
        String input = "\n\nHuman:hello good words.\n\nAssistant:";
        Boolean res = localRegexGuardrail.validateRegexList(input, regexPatterns);

        Assert.assertTrue(res);
    }

    @Test
    public void validateRegexListFailed() {
        String input = "\n\nHuman:hello stop words.\n\nAssistant:";
        Boolean res = localRegexGuardrail.validateRegexList(input, regexPatterns);

        Assert.assertFalse(res);
    }

    @Test
    public void validateRegexListNull() {
        String input = "\n\nHuman:hello good words.\n\nAssistant:";
        Boolean res = localRegexGuardrail.validateRegexList(input, null);

        Assert.assertTrue(res);
    }

    @Test
    public void validateRegexListEmpty() {
        String input = "\n\nHuman:hello good words.\n\nAssistant:";
        Boolean res = localRegexGuardrail.validateRegexList(input, List.of());

        Assert.assertTrue(res);
    }

    @Test
    public void validateRegexSuccess() {
        String input = "\n\nHuman:hello good words.\n\nAssistant:";
        Boolean res = localRegexGuardrail.validateRegex(input, regexPatterns.get(0));

        Assert.assertTrue(res);
    }

    @Test
    public void validateRegexFailed() {
        String input = "\n\nHuman:hello stop words.\n\nAssistant:";
        Boolean res = localRegexGuardrail.validateRegex(input, regexPatterns.get(0));

        Assert.assertFalse(res);
    }

    @Test
    public void validateStopWords() throws IOException {
        Map<String, List<String>> stopWordsIndices = Map.of("test_index", List.of("test_field"));
        SearchResponse searchResponse = createSearchResponse(1);
        ActionFuture<SearchResponse> future = createSearchResponseFuture(searchResponse);
        when(this.client.search(any())).thenReturn(future);

        Boolean res = localRegexGuardrail.validateStopWords("hello world", stopWordsIndices);
        Assert.assertTrue(res);
    }

    @Test
    public void validateStopWordsNull() {
        Boolean res = localRegexGuardrail.validateStopWords("hello world", null);
        Assert.assertTrue(res);
    }

    @Test
    public void validateStopWordsEmpty() {
        Boolean res = localRegexGuardrail.validateStopWords("hello world", Map.of());
        Assert.assertTrue(res);
    }

    @Test
    public void validateStopWordsSingleIndex() throws IOException {
        SearchResponse searchResponse = createSearchResponse(1);
        ActionFuture<SearchResponse> future = createSearchResponseFuture(searchResponse);
        when(this.client.search(any())).thenReturn(future);

        Boolean res = localRegexGuardrail.validateStopWordsSingleIndex("hello world", "test_index", List.of("test_field"));
        Assert.assertTrue(res);
    }

    private SearchResponse createSearchResponse(int size) throws IOException {
        XContentBuilder content = localRegexGuardrail.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
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
            public SearchResponse get() {
                return searchResponse;
            }

            @Override
            public SearchResponse get(long timeout, TimeUnit unit) {
                return searchResponse;
            }
        };
    }
}