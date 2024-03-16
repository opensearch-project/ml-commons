package org.opensearch.ml.common.model;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Client;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;

import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.opensearch.ml.common.utils.StringUtils.gson;

@Log4j2
@Getter
public class MLGuard {
    private Map<String, List<String>> stopWordsIndicesInput = new HashMap<>();
    private Map<String, List<String>> stopWordsIndicesOutput = new HashMap<>();
    private List<String> inputRegex;
    private List<String> outputRegex;
    private NamedXContentRegistry xContentRegistry;
    private Client client;

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
        }
        if (outputGuardrail != null) {
            fillStopWordsToMap(outputGuardrail, stopWordsIndicesOutput);
            outputRegex = outputGuardrail.getRegex() == null ? new ArrayList<>() : Arrays.asList(outputGuardrail.getRegex());
        }
    }

    private void fillStopWordsToMap(@NonNull Guardrail guardrail, Map<String, List<String>> map) {
        List<StopWords> stopWords = guardrail.getStopWords();
        if (stopWords == null || stopWords.isEmpty()) {
            return;
        }
        for (StopWords e : stopWords) {
            map.put(e.getIndex(), Arrays.asList(e.getSourceFields()));
        }
    }

    public Boolean validate(String input, int type) {
        switch (type) {
            case 0: // validate input
                return validateRegexList(input, inputRegex) && validateStopWords(input, stopWordsIndicesInput);
            case 1: // validate output
                return validateRegexList(input, outputRegex) && validateStopWords(input, stopWordsIndicesOutput);
            default:
                return true;
        }
    }

    public Boolean validateRegexList(String input, List<String> regexList) {
        for (String regex : regexList) {
            if (!validateRegex(input, regex)) {
                return false;
            }
        }
        return true;
    }

    public Boolean validateRegex(String input, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);
        return matcher.matches();
    }

    public Boolean validateStopWords(String input, Map<String, List<String>> stopWordsIndices) {
        for (Map.Entry entry : stopWordsIndices.entrySet()) {
            if (!validateStopWordsSingleIndex(input, (String) entry.getKey(), (List<String>) entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    public Boolean validateStopWordsSingleIndex(String input, String indexName, List<String> fieldNames) {
        SearchRequest searchRequest;
        SearchResponse searchResponse;
        String queryBody;
        Map<String, String> documentMap = new HashMap<>();
        for (String field : fieldNames) {
            documentMap.put(field, input);
        }
        Map<String, Object> queryBodyMap = Map
                .of("query", Map.of("percolate", Map.of("field", "query", "document", documentMap)));

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            queryBody = AccessController.doPrivileged((PrivilegedExceptionAction<String>) () -> gson.toJson(queryBodyMap));
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            XContentParser queryParser = XContentType.JSON.xContent().createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, queryBody);
            searchSourceBuilder.parseXContent(queryParser);
            searchSourceBuilder.size(1); //Only need 1 doc returned, if hit.
            searchRequest = new SearchRequest().source(searchSourceBuilder).indices(indexName);
            searchResponse = client.search(searchRequest).actionGet(1000l * 30);
            context.restore();
        } catch (Exception e) {
            log.error("[validateStopWords] Searching stop words index failed.", e);
            return false;
        }

        SearchHit[] hits = searchResponse.getHits().getHits();
        if (hits != null && hits.length > 0) {
            return false;
        }
        return true;
    }
}
