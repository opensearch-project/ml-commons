/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.utils.StringUtils.gson;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.opensearch.action.LatchedActionListener;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.SearchDataObjectRequest;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.transport.client.Client;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

@Log4j2
@EqualsAndHashCode
@Getter
public class LocalRegexGuardrail extends Guardrail {
    public static final String STOP_WORDS_FIELD = "stop_words";
    public static final String REGEX_FIELD = "regex";

    private List<StopWords> stopWords;
    private String[] regex;
    private List<Pattern> regexPattern;
    private Map<String, List<String>> stopWordsIndicesInput;
    private NamedXContentRegistry xContentRegistry;
    private Client client;
    private SdkClient sdkClient;
    private String tenantId;

    @Builder(toBuilder = true)
    public LocalRegexGuardrail(List<StopWords> stopWords, String[] regex) {
        this.stopWords = stopWords;
        this.regex = regex;
    }

    public LocalRegexGuardrail(@NonNull Map<String, Object> params) {
        List<Map> words = (List<Map>) params.get(STOP_WORDS_FIELD);
        stopWords = new ArrayList<>();
        if (words != null && !words.isEmpty()) {
            for (Map e : words) {
                stopWords.add(new StopWords(e));
            }
        }
        List<String> regexes = (List<String>) params.get(REGEX_FIELD);
        if (regexes != null && !regexes.isEmpty()) {
            this.regex = regexes.toArray(new String[0]);
        }
    }

    public LocalRegexGuardrail(StreamInput input) throws IOException {
        if (input.readBoolean()) {
            stopWords = new ArrayList<>();
            int size = input.readInt();
            for (int i = 0; i < size; i++) {
                stopWords.add(new StopWords(input));
            }
        }
        regex = input.readOptionalStringArray();
    }

    public void writeTo(StreamOutput out) throws IOException {
        if (stopWords != null && stopWords.size() > 0) {
            out.writeBoolean(true);
            out.writeInt(stopWords.size());
            for (StopWords e : stopWords) {
                e.writeTo(out);
            }
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalStringArray(regex);
    }

    @Override
    public Boolean validate(String input, Map<String, String> parameters) {
        return validateRegexList(input, regexPattern) && validateStopWords(input, stopWordsIndicesInput);
    }

    @Override
    public void init(NamedXContentRegistry xContentRegistry, Client client, SdkClient sdkClient, String tenantId) {
        this.xContentRegistry = xContentRegistry;
        this.client = client;
        this.sdkClient = sdkClient;
        this.tenantId = tenantId;
        init();
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (stopWords != null && stopWords.size() > 0) {
            builder.field(STOP_WORDS_FIELD, stopWords);
        }
        if (regex != null) {
            builder.field(REGEX_FIELD, regex);
        }
        builder.endObject();
        return builder;
    }

    public static LocalRegexGuardrail parse(XContentParser parser) throws IOException {
        List<StopWords> stopWords = null;
        String[] regex = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case STOP_WORDS_FIELD:
                    stopWords = new ArrayList<>();
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        stopWords.add(StopWords.parse(parser));
                    }
                    break;
                case REGEX_FIELD:
                    regex = parser.list().toArray(new String[0]);
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return LocalRegexGuardrail.builder().stopWords(stopWords).regex(regex).build();
    }

    private void init() {
        stopWordsIndicesInput = stopWordsToMap();
        List<String> regexList = regex == null ? new ArrayList<>() : Arrays.asList(regex);
        regexPattern = regexList.stream().map(reg -> Pattern.compile(reg)).collect(Collectors.toList());
    }

    private Map<String, List<String>> stopWordsToMap() {
        Map<String, List<String>> map = new HashMap<>();
        if (stopWords != null && !stopWords.isEmpty()) {
            for (StopWords e : stopWords) {
                if (e.getIndex() != null && e.getSourceFields() != null) {
                    map.put(e.getIndex(), Arrays.asList(e.getSourceFields()));
                }
            }
        }
        return map;
    }

    public Boolean validateRegexList(String input, List<Pattern> regexPatterns) {
        if (regexPatterns == null || regexPatterns.isEmpty()) {
            return true;
        }
        for (Pattern pattern : regexPatterns) {
            if (!validateRegex(input, pattern)) {
                return false;
            }
        }
        return true;
    }

    public Boolean validateRegex(String input, Pattern pattern) {
        Matcher matcher = pattern.matcher(input);
        return !matcher.matches();
    }

    public Boolean validateStopWords(String input, Map<String, List<String>> stopWordsIndices) {
        if (stopWordsIndices == null || stopWordsIndices.isEmpty()) {
            return true;
        }
        for (Map.Entry entry : stopWordsIndices.entrySet()) {
            if (!validateStopWordsSingleIndex(input, (String) entry.getKey(), (List<String>) entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Validate the input string against stop words
     * @param input the string to validate against stop words
     * @param indexName the index containing stop words
     * @param fieldNames a list of field names containing stop words
     * @return true if no stop words matching, otherwise false.
     */
    public Boolean validateStopWordsSingleIndex(String input, String indexName, List<String> fieldNames) {
        AtomicBoolean passedStopWordCheck = new AtomicBoolean(false);
        String queryBody;
        Map<String, String> documentMap = new HashMap<>();
        for (String field : fieldNames) {
            documentMap.put(field, input);
        }
        Map<String, Object> queryBodyMap = Map.of("query", Map.of("percolate", Map.of("field", "query", "document", documentMap)));
        CountDownLatch latch = new CountDownLatch(1);
        try {
            queryBody = AccessController.doPrivileged((PrivilegedExceptionAction<String>) () -> gson.toJson(queryBodyMap));
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            XContentParser queryParser = XContentType.JSON
                .xContent()
                .createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, queryBody);
            searchSourceBuilder.parseXContent(queryParser);
            searchSourceBuilder.size(1); // Only need 1 doc returned, if hit.
            var responseListener = new LatchedActionListener<>(ActionListener.<SearchResponse>wrap(r -> {
                if (r == null || r.getHits() == null || r.getHits().getTotalHits() == null || r.getHits().getTotalHits().value() == 0) {
                    passedStopWordCheck.set(true);
                }
            }, e -> {
                log.error("Failed to search stop words index {}", indexName, e);
                passedStopWordCheck.set(true);
            }), latch);
            SearchDataObjectRequest searchDataObjectRequest = SearchDataObjectRequest
                .builder()
                .indices(indexName)
                .searchSourceBuilder(searchSourceBuilder)
                .tenantId(tenantId)
                .build();
            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                sdkClient
                    .searchDataObjectAsync(searchDataObjectRequest)
                    .whenComplete(SdkClientUtils.wrapSearchCompletion(ActionListener.runBefore(responseListener, context::restore)));
            }
        } catch (Exception e) {
            log.error("[validateStopWords] Searching stop words index failed.", e);
            latch.countDown();
            passedStopWordCheck.set(true);
        }

        try {
            latch.await(5, SECONDS);
        } catch (InterruptedException e) {
            log.error("[validateStopWords] Searching stop words index was timeout.", e);
            throw new IllegalStateException(e);
        }
        return passedStopWordCheck.get();
    }
}
