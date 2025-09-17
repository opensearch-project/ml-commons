/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import org.apache.lucene.search.TotalHits;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.TestHelper;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.SearchDataObjectRequest;
import org.opensearch.remote.metadata.client.SearchDataObjectResponse;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.SearchModule;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.search.internal.InternalSearchResponse;
import org.opensearch.search.profile.SearchProfileShardResults;
import org.opensearch.search.suggest.Suggest;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

public class LocalRegexGuardrailTests {
    NamedXContentRegistry xContentRegistry;
    @Mock
    Client client;
    @Mock
    ThreadPool threadPool;
    @Mock
    SdkClient sdkClient;
    ThreadContext threadContext;

    StopWords stopWords;
    String[] regex;
    List<Pattern> regexPatterns;
    LocalRegexGuardrail localRegexGuardrail;
    final String tenantId = "tenant_id";
    final String indexName = "test_index";
    final String testField = "test_field";

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        xContentRegistry = spy(new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()));
        Settings settings = Settings.builder().build();
        this.threadContext = new ThreadContext(settings);
        when(this.client.threadPool()).thenReturn(this.threadPool);
        when(this.threadPool.getThreadContext()).thenReturn(this.threadContext);

        stopWords = new StopWords(indexName, List.of(testField).toArray(new String[0]));
        regex = List.of("(.|\n)*stop words(.|\n)*").toArray(new String[0]);
        regexPatterns = List.of(Pattern.compile("(.|\n)*stop words(.|\n)*"));
        localRegexGuardrail = new LocalRegexGuardrail(List.of(stopWords), regex);
        localRegexGuardrail.init(xContentRegistry, client, sdkClient, tenantId);
    }

    @Test
    public void readWriteEmptyContent() throws IOException {
        LocalRegexGuardrail localRegexGuardrail = new LocalRegexGuardrail(Collections.emptyList(), new String[0]);
        BytesStreamOutput output = new BytesStreamOutput();
        localRegexGuardrail.writeTo(output);
        LocalRegexGuardrail localRegexGuardrail1 = new LocalRegexGuardrail(output.bytes().streamInput());

        Assert.assertNull(localRegexGuardrail1.getStopWords());
        Assert.assertArrayEquals(Collections.emptyList().toArray(), localRegexGuardrail1.getRegex());
    }

    @Test
    public void readWriteNullContent() throws IOException {
        LocalRegexGuardrail localRegexGuardrail = new LocalRegexGuardrail(null, null);
        BytesStreamOutput output = new BytesStreamOutput();
        localRegexGuardrail.writeTo(output);
        LocalRegexGuardrail localRegexGuardrail1 = new LocalRegexGuardrail(output.bytes().streamInput());

        Assert.assertNull(localRegexGuardrail1.getStopWords());
        Assert.assertNull(localRegexGuardrail1.getRegex());
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

        Assert
            .assertEquals(
                "{\"stop_words\":[{\"index_name\":\"test_index\",\"source_fields\":[\"test_field\"]}],\"regex\":[\"(.|\\n)*stop words(.|\\n)*\"]}",
                content
            );
    }

    @Test
    public void parse() throws IOException {
        String jsonStr =
            "{\"stop_words\":[{\"index_name\":\"test_index\",\"source_fields\":[\"test_field\"]}],\"regex\":[\"(.|\\n)*stop words(.|\\n)*\"]}";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                null,
                jsonStr
            );
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
    public void testValidateStopWordsPass() {
        Map<String, List<String>> stopWordsIndices = Map.of(indexName, List.of(testField));
        LocalRegexGuardrail spyGuardrail = spy(localRegexGuardrail);
        doReturn(true).when(spyGuardrail).validateStopWordsSingleIndex("hello world", indexName, List.of(testField));

        Boolean resPass = spyGuardrail.validateStopWords("hello world", stopWordsIndices);
        Assert.assertTrue(resPass);
    }

    @Test
    public void testValidateStopWordsFail() {
        Map<String, List<String>> stopWordsIndices = Map.of(indexName, List.of(testField));
        LocalRegexGuardrail spyGuardrail = spy(localRegexGuardrail);
        doReturn(false).when(spyGuardrail).validateStopWordsSingleIndex("stop word", indexName, List.of(testField));

        Boolean resFail = spyGuardrail.validateStopWords("stop word", stopWordsIndices);
        Assert.assertFalse(resFail);
    }

    @Test
    public void testValidateStopWordsNull() {
        Boolean res = localRegexGuardrail.validateStopWords("hello world", null);
        Assert.assertTrue(res);
    }

    @Test
    public void testValidateStopWordsEmpty() {
        Boolean res = localRegexGuardrail.validateStopWords("hello world", Map.of());
        Assert.assertTrue(res);
    }

    @Test
    public void testValidateStopWordsSingleIndexWithoutHit() throws Exception {
        LocalRegexGuardrail spyGuardrail = spy(localRegexGuardrail);
        doReturn(mock(SearchDataObjectRequest.class)).when(spyGuardrail).buildSearchDataObjectRequest(any(), any());

        SearchResponse emptySearchResponse = createSearchResponse(0);
        SearchDataObjectResponse searchDataObjectResponse = new SearchDataObjectResponse(emptySearchResponse);
        CompletableFuture<SearchDataObjectResponse> completedFuture = CompletableFuture.completedFuture(searchDataObjectResponse);

        // Mock the searchDataObjectAsync to return our future with empty response
        when(sdkClient.searchDataObjectAsync(any())).thenReturn(completedFuture);

        Boolean res = spyGuardrail.validateStopWordsSingleIndex("hello world", indexName, List.of(testField));
        Assert.assertTrue(res);
        Mockito.verify(sdkClient, Mockito.times(1)).searchDataObjectAsync(any());
    }

    @Test
    public void testValidateStopWordsSingleIndexWithStopWordHit() throws Exception {
        LocalRegexGuardrail spyGuardrail = spy(localRegexGuardrail);
        doReturn(mock(SearchDataObjectRequest.class)).when(spyGuardrail).buildSearchDataObjectRequest(any(), any());

        // Mock the sdkClient response - search returns a hit (stop word found)
        SearchResponse searchResponseWithHit = createSearchResponse(1);
        SearchDataObjectResponse searchDataObjectResponse = new SearchDataObjectResponse(searchResponseWithHit);
        // Create a completable future that will immediately execute the callback with a response containing hits
        CompletableFuture<SearchDataObjectResponse> completedFuture = CompletableFuture.completedFuture(searchDataObjectResponse);

        // Mock the searchDataObjectAsync to return our future that has hits
        when(sdkClient.searchDataObjectAsync(any())).thenReturn(completedFuture);

        Boolean res = spyGuardrail.validateStopWordsSingleIndex("hello bad word", indexName, List.of(testField));
        Assert.assertFalse(res);
        Mockito.verify(sdkClient, Mockito.times(1)).searchDataObjectAsync(any());
    }

    @Test
    public void testValidateStopWordsSingleIndexFailedSearchingIndex() throws IOException {
        LocalRegexGuardrail spyGuardrail = spy(localRegexGuardrail);
        doReturn(mock(SearchDataObjectRequest.class)).when(spyGuardrail).buildSearchDataObjectRequest(any(), any());

        // Create a completable future that throws an exception when get() is called
        CompletableFuture<SearchDataObjectResponse> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new IOException("Index not found"));
        when(sdkClient.searchDataObjectAsync(any())).thenReturn(failedFuture);

        // Covers error "Failed to search stop words index test_index"
        Boolean res = spyGuardrail.validateStopWordsSingleIndex("hello world", indexName, List.of(testField));
        Assert.assertTrue(res);
        Mockito.verify(sdkClient, Mockito.times(1)).searchDataObjectAsync(any());
    }

    @Test
    public void testValidateStopWordsSingleIndexFailed() throws IOException {
        LocalRegexGuardrail spyGuardrail = spy(localRegexGuardrail);
        doReturn(mock(SearchDataObjectRequest.class)).when(spyGuardrail).buildSearchDataObjectRequest(any(), any());

        when(sdkClient.searchDataObjectAsync(any())).thenThrow(new RuntimeException("test exception"));
        // Covers error "[validateStopWords] Searching stop words index failed."
        Boolean res = spyGuardrail.validateStopWordsSingleIndex("hello world", indexName, List.of(testField));
        Assert.assertTrue(res);
        Mockito.verify(sdkClient, Mockito.times(1)).searchDataObjectAsync(any());
    }

    @Test
    public void testBuildSearchDataObjectRequest() throws IOException {
        SearchDataObjectRequest request = localRegexGuardrail.buildSearchDataObjectRequest(indexName, "{}");
        Assert.assertEquals(indexName, request.indices()[0]);
        Assert.assertEquals(tenantId, request.tenantId());
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
}
