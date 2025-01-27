/*
 * SPDX-License-Identifier: Apache-2.0
 *
 *  The OpenSearch Contributors require contributions made to
 *  this file be licensed under the Apache-2.0 license or a
 *  compatible open source license.
 *
 */
package org.opensearch.ml.searchext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.search.builder.SearchSourceBuilder;

public class MLInferenceRequestParametersUtilTests {
    @Test
    public void testExtractParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("query_text", "foo");
        MLInferenceRequestParameters expected = new MLInferenceRequestParameters(params);
        MLInferenceRequestParametersExtBuilder extBuilder = new MLInferenceRequestParametersExtBuilder();
        extBuilder.setRequestParameters(expected);
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource().ext(List.of(extBuilder));
        SearchRequest request = new SearchRequest("my_index").source(sourceBuilder);
        MLInferenceRequestParameters actual = MLInferenceRequestParametersUtil.getMLInferenceRequestParameters(request);
        assertEquals(expected, actual);
    }

    @Test
    public void testExtractParametersWithNullSource() {
        SearchRequest request = new SearchRequest();
        MLInferenceRequestParameters result = MLInferenceRequestParametersUtil.getMLInferenceRequestParameters(request);
        assertNull(result);
    }

    @Test
    public void testExtractParametersWithEmptyExt() {
        SearchRequest request = new SearchRequest();
        request.source(new SearchSourceBuilder());
        MLInferenceRequestParameters result = MLInferenceRequestParametersUtil.getMLInferenceRequestParameters(request);
        assertNull(result);
    }

}
