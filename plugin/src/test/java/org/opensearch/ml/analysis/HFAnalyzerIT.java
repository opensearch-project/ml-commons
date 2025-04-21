/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.analysis;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.opensearch.client.Response;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.rest.MLCommonsRestTestCase;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.test.OpenSearchIntegTestCase;

import com.google.common.collect.ImmutableMap;

import lombok.SneakyThrows;

@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE, numDataNodes = 2)
public class HFAnalyzerIT extends MLCommonsRestTestCase {
    static String inputSentence = "hello 你好";

    @SneakyThrows
    public void testCallAnalyze_bert_uncased() {
        Map<String, Object> response = doAnalyze(inputSentence, "bert-uncased");
        List tokens = (List) response.get("tokens");
        assertEquals(3, tokens.size());
        assertEquals("hello", ((Map) tokens.get(0)).get("token"));
        assertEquals("[UNK]", ((Map) tokens.get(1)).get("token"));
        assertEquals("[UNK]", ((Map) tokens.get(2)).get("token"));
    }

    @SneakyThrows
    public void testCallAnalyze_mbert_uncased() {
        Map<String, Object> response = doAnalyze(inputSentence, "mbert-uncased");
        List tokens = (List) response.get("tokens");
        assertEquals(3, tokens.size());
        assertEquals("hello", ((Map) tokens.get(0)).get("token"));
        assertEquals("你", ((Map) tokens.get(1)).get("token"));
        assertEquals("好", ((Map) tokens.get(2)).get("token"));
    }

    private Map<String, Object> doAnalyze(String input, String analyzerName) throws IOException {
        Response response = TestHelper
            .makeRequest(
                client(),
                "POST",
                "/_analyze",
                ImmutableMap.of(),
                TestHelper.toHttpEntity(StringUtils.toJson(Map.of("text", input, "analyzer", analyzerName))),
                null
            );
        return gson.fromJson(TestHelper.httpEntityToString(response.getEntity(), "UTF-8"), Map.class);
    }
}
