package org.opensearch.searchpipelines.questionanswering.generative;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.OpenSearchTestCase;

import java.util.List;

public class GenerativeQAParametersTests extends OpenSearchTestCase {

    public void testGenerativeQAParameters() {
        GenerativeQAParameters params = new GenerativeQAParameters("conversation_id", "llm_model", "llm_question");
        GenerativeQAParamExtBuilder extBuilder = new GenerativeQAParamExtBuilder();
        extBuilder.setParams(params);
        SearchSourceBuilder srcBulder = SearchSourceBuilder.searchSource().ext(List.of(extBuilder));
        SearchRequest request = new SearchRequest("my_index").source(srcBulder);
        GenerativeQAParameters actual = GenerativeQAParamUtil.getGenerativeQAParameters(request);
        assertEquals(params, actual);
    }
}
