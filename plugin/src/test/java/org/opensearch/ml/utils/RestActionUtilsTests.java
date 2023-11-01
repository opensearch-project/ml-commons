/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import static org.opensearch.ml.utils.RestActionUtils.OPENSEARCH_DASHBOARDS_USER_AGENT;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_ALGORITHM;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_ASYNC;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_MODEL_ID;
import static org.opensearch.ml.utils.RestActionUtils.UI_METADATA_EXCLUDE;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.plugin.MachineLearningPlugin;
import org.opensearch.ml.repackage.com.google.common.collect.ImmutableMap;
import org.opensearch.rest.RestRequest;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;

public class RestActionUtilsTests extends OpenSearchTestCase {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    Map<String, String> param;
    FakeRestRequest fakeRestRequest;
    String algoName = FunctionName.KMEANS.name();
    String urlPath = MachineLearningPlugin.ML_BASE_URI + "/_train/" + algoName;

    @Before
    public void setup() {
        param = ImmutableMap.<String, String>builder().put(PARAMETER_ALGORITHM, algoName).build();
        fakeRestRequest = createRestRequest(param);
    }

    private FakeRestRequest createRestRequest(Map<String, String> param) {
        return createRestRequest(param, urlPath, RestRequest.Method.POST);
    }

    private FakeRestRequest createRestRequest(Map<String, String> param, String urlPath, RestRequest.Method method) {
        return new FakeRestRequest.Builder(xContentRegistry()).withMethod(method).withPath(urlPath).withParams(param).build();
    }

    public void testGetAlgorithm() {
        String paramValue = RestActionUtils.getAlgorithm(fakeRestRequest);
        assertEquals(algoName, paramValue);
    }

    public void testGetAlgorithm_EmptyValue() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Request should contain algorithm!");
        fakeRestRequest = createRestRequest(ImmutableMap.<String, String>builder().put(PARAMETER_ALGORITHM, "").build());
        RestActionUtils.getAlgorithm(fakeRestRequest);
    }

    public void testIsAsync() {
        fakeRestRequest = createRestRequest(ImmutableMap.<String, String>builder().put(PARAMETER_ASYNC, "true").build());
        boolean isAsync = RestActionUtils.isAsync(fakeRestRequest);
        assertTrue(isAsync);
    }

    public void testGetParameterId() {
        String modelId = "testModelId";
        param = ImmutableMap.<String, String>builder().put(PARAMETER_MODEL_ID, modelId).build();
        fakeRestRequest = createRestRequest(param, "_plugins/_ml/models/" + modelId, RestRequest.Method.GET);
        String paramValue = RestActionUtils.getParameterId(fakeRestRequest, PARAMETER_MODEL_ID);
        assertEquals(modelId, paramValue);
    }

    public void testGetParameterId_EmptyValue() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Request should contain " + PARAMETER_MODEL_ID);
        param = ImmutableMap.<String, String>builder().put(PARAMETER_MODEL_ID, "").build();
        fakeRestRequest = createRestRequest(param, "_plugins/_ml/models/testModelId", RestRequest.Method.GET);
        RestActionUtils.getParameterId(fakeRestRequest, PARAMETER_MODEL_ID);
    }

    public void testGetSourceContext_FromDashboards() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("User-Agent", Arrays.asList(OPENSEARCH_DASHBOARDS_USER_AGENT));
        FakeRestRequest request = new FakeRestRequest.Builder(xContentRegistry())
            .withMethod(RestRequest.Method.POST)
            .withPath(urlPath)
            .withParams(param)
            .withHeaders(headers)
            .build();
        SearchSourceBuilder testSearchSourceBuilder = new SearchSourceBuilder();
        testSearchSourceBuilder.fetchSource(new String[] { "a" }, new String[] { "b" });
        FetchSourceContext sourceContext = RestActionUtils.getSourceContext(request, testSearchSourceBuilder);
        assertNotNull(sourceContext);
    }

    public void testGetSourceContext_FromClient_EmptyExcludes() {
        FakeRestRequest request = new FakeRestRequest.Builder(xContentRegistry())
            .withMethod(RestRequest.Method.POST)
            .withPath(urlPath)
            .withParams(param)
            .build();
        SearchSourceBuilder testSearchSourceBuilder = new SearchSourceBuilder();
        testSearchSourceBuilder.fetchSource(new String[] { "a" }, new String[0]);
        FetchSourceContext sourceContext = RestActionUtils.getSourceContext(request, testSearchSourceBuilder);
        assertArrayEquals(UI_METADATA_EXCLUDE, sourceContext.excludes());
    }

    public void testGetSourceContext_FromClient_WithExcludes() {
        FakeRestRequest request = new FakeRestRequest.Builder(xContentRegistry())
            .withMethod(RestRequest.Method.POST)
            .withPath(urlPath)
            .withParams(param)
            .build();
        SearchSourceBuilder testSearchSourceBuilder = new SearchSourceBuilder();
        testSearchSourceBuilder.fetchSource(new String[] { "a" }, new String[] { "b" });
        FetchSourceContext sourceContext = RestActionUtils.getSourceContext(request, testSearchSourceBuilder);
        assertEquals(sourceContext.excludes().length, 2);
    }
}
