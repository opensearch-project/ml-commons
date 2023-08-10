package org.opensearch.searchpipelines.questionanswering.generative;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.OpenSearchTestCase;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class GenerativeQAParamUtilTests extends OpenSearchTestCase {

    public void testGenerativeQAParametersMissingParams() {
        GenerativeQAParamExtBuilder extBuilder = new GenerativeQAParamExtBuilder();
        SearchSourceBuilder srcBulder = SearchSourceBuilder.searchSource().ext(List.of(extBuilder));
        SearchRequest request = new SearchRequest("my_index").source(srcBulder);
        GenerativeQAParameters actual = GenerativeQAParamUtil.getGenerativeQAParameters(request);
        assertNull(actual);
    }
}
