/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import com.google.common.collect.ImmutableSet;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.opensearch.action.LatchedActionListener;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Client;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.search.builder.SearchSourceBuilder;

import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.opensearch.ml.common.utils.StringUtils.gson;

@Log4j2
@Getter
public class MLGuard {
    private Map<String, List<String>> stopWordsIndicesInput = new HashMap<>();
    private Map<String, List<String>> stopWordsIndicesOutput = new HashMap<>();
    private List<String> inputRegex;
    private List<String> outputRegex;
    private List<Pattern> inputRegexPattern;
    private List<Pattern> outputRegexPattern;
    private NamedXContentRegistry xContentRegistry;
    private Client client;
    private Set<String> stopWordsIndices = ImmutableSet.of(".plugins-ml-stop-words");

    public MLGuard(Guardrails guardrails, NamedXContentRegistry xContentRegistry, Client client) {
        this.xContentRegistry = xContentRegistry;
        this.client = client;
        if (guardrails == null) {
            return;
        }
        Guardrail inputGuardrail = guardrails.getInputGuardrail();
        Guardrail outputGuardrail = guardrails.getOutputGuardrail();
        if (inputGuardrail != null) {
            fillStopWordsToMap(inputGuardrail, stopWordsIndicesInput);
            inputRegex = inputGuardrail.getRegex() == null ? new ArrayList<>() : Arrays.asList(inputGuardrail.getRegex());
            inputRegexPattern = inputRegex.stream().map(reg -> Pattern.compile(reg)).collect(Collectors.toList());
        }
        if (outputGuardrail != null) {
            fillStopWordsToMap(outputGuardrail, stopWordsIndicesOutput);
            outputRegex = outputGuardrail.getRegex() == null ? new ArrayList<>() : Arrays.asList(outputGuardrail.getRegex());
            outputRegexPattern = outputRegex.stream().map(reg -> Pattern.compile(reg)).collect(Collectors.toList());
        }
    }

    private void fillStopWordsToMap(@NonNull Guardrail guardrail, Map<String, List<String>> map) {
        List<StopWords> stopWords = guardrail.getStopWords();
        if (stopWords == null || stopWords.isEmpty()) {
            return;
        }
        for (StopWords e : stopWords) {
            if (e.getIndex() != null && e.getSourceFields() != null) {
                map.put(e.getIndex(), Arrays.asList(e.getSourceFields()));
            }
        }
    }

    public Boolean validate(String input, Type type) {
        switch (type) {
            case INPUT: // validate input
                return validateRegexList(input, inputRegexPattern) && validateStopWords(input, stopWordsIndicesInput);
            case OUTPUT: // validate output
                return validateRegexList(input, outputRegexPattern) && validateStopWords(input, stopWordsIndicesOutput);
            default:
                throw new IllegalArgumentException("Unsupported type to validate for guardrails.");
        }
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

    public Boolean validateStopWordsSingleIndex(String input, String indexName, List<String> fieldNames) {
        SearchRequest searchRequest;
        AtomicBoolean hitStopWords = new AtomicBoolean(false);
        String queryBody;
        Map<String, String> documentMap = new HashMap<>();
        for (String field : fieldNames) {
            documentMap.put(field, input);
        }
        Map<String, Object> queryBodyMap = Map
                .of("query", Map.of("percolate", Map.of("field", "query", "document", documentMap)));
        CountDownLatch latch = new CountDownLatch(1);
        ThreadContext.StoredContext context = null;

        try {
            queryBody = AccessController.doPrivileged((PrivilegedExceptionAction<String>) () -> gson.toJson(queryBodyMap));
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            XContentParser queryParser = XContentType.JSON.xContent().createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, queryBody);
            searchSourceBuilder.parseXContent(queryParser);
            searchSourceBuilder.size(1); //Only need 1 doc returned, if hit.
            searchRequest = new SearchRequest().source(searchSourceBuilder).indices(indexName);
            if (isStopWordsSystemIndex(indexName)) {
                context = client.threadPool().getThreadContext().stashContext();
                ThreadContext.StoredContext finalContext = context;
                client.search(searchRequest, ActionListener.runBefore(new LatchedActionListener(ActionListener.<SearchResponse>wrap(r -> {
                    if (r == null || r.getHits() == null || r.getHits().getTotalHits() == null || r.getHits().getTotalHits().value == 0) {
                        hitStopWords.set(true);
                    }
                }, e -> {
                    log.error("Failed to search stop words index {}", indexName, e);
                    hitStopWords.set(true);
                }), latch), () -> finalContext.restore()));
            } else {
                client.search(searchRequest, new LatchedActionListener(ActionListener.<SearchResponse>wrap(r -> {
                    if (r == null || r.getHits() == null || r.getHits().getTotalHits() == null || r.getHits().getTotalHits().value == 0) {
                        hitStopWords.set(true);
                    }
                }, e -> {
                    log.error("Failed to search stop words index {}", indexName, e);
                    hitStopWords.set(true);
                }), latch));
            }
        } catch (Exception e) {
            log.error("[validateStopWords] Searching stop words index failed.", e);
            latch.countDown();
            hitStopWords.set(true);
        } finally {
            if (context != null) {
                context.close();
            }
        }

        try {
            latch.await(5, SECONDS);
        } catch (InterruptedException e) {
            log.error("[validateStopWords] Searching stop words index was timeout.", e);
            throw new IllegalStateException(e);
        }
        return hitStopWords.get();
    }

    private boolean isStopWordsSystemIndex(String index) {
        return stopWordsIndices.contains(index);
    }

    public enum Type {
        INPUT,
        OUTPUT
    }
}
