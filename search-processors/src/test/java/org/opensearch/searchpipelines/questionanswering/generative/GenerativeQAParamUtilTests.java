package org.opensearch.searchpipelines.questionanswering.generative;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.search.SearchExtBuilder;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.searchpipelines.questionanswering.generative.ext.GenerativeQAParamExtBuilder;
import org.opensearch.searchpipelines.questionanswering.generative.ext.GenerativeQAParamUtil;
import org.opensearch.searchpipelines.questionanswering.generative.ext.GenerativeQAParameters;
import org.opensearch.test.OpenSearchTestCase;

public class GenerativeQAParamUtilTests extends OpenSearchTestCase {

    public void testGenerativeQAParametersMissingParams() {
        GenerativeQAParamExtBuilder extBuilder = new GenerativeQAParamExtBuilder();
        SearchSourceBuilder srcBulder = SearchSourceBuilder.searchSource().ext(List.of(extBuilder));
        SearchRequest request = new SearchRequest("my_index").source(srcBulder);
        GenerativeQAParameters actual = GenerativeQAParamUtil.getGenerativeQAParameters(request);
        assertNull(actual);
    }

    public void testMisc() {
        SearchRequest request = new SearchRequest();
        assertNull(GenerativeQAParamUtil.getGenerativeQAParameters(request));
        request.source(new SearchSourceBuilder());
        assertNull(GenerativeQAParamUtil.getGenerativeQAParameters(request));
        request.source(new SearchSourceBuilder().ext(List.of()));
        assertNull(GenerativeQAParamUtil.getGenerativeQAParameters(request));

        SearchExtBuilder extBuilder = mock(SearchExtBuilder.class);
        when(extBuilder.getWriteableName()).thenReturn("foo");
        request.source(new SearchSourceBuilder().ext(List.of(extBuilder)));
        assertNull(GenerativeQAParamUtil.getGenerativeQAParameters(request));
    }
}
