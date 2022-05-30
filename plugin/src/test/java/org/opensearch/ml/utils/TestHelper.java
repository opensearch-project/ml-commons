/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.junit.Assert.assertEquals;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_ALGORITHM;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.logging.log4j.util.Strings;
import org.opensearch.client.Request;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.Response;
import org.opensearch.client.RestClient;
import org.opensearch.client.WarningsHandler;
import org.opensearch.common.bytes.BytesArray;
import org.opensearch.common.bytes.BytesReference;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.ToXContent;
import org.opensearch.common.xcontent.ToXContentObject;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.dataset.SearchQueryInputDataset;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.execute.samplecalculator.LocalSampleCalculatorInput;
import org.opensearch.ml.common.input.parameter.clustering.KMeansParams;
import org.opensearch.ml.stats.MLStatsInput;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.RestStatus;
import org.opensearch.search.SearchModule;
import org.opensearch.test.rest.FakeRestRequest;

import com.google.common.collect.ImmutableMap;

public class TestHelper {
    public static XContentParser parser(String xc) throws IOException {
        return parser(xc, true);
    }

    public static XContentParser parser(String xc, boolean skipFirstToken) throws IOException {
        XContentParser parser = XContentType.JSON.xContent().createParser(xContentRegistry(), LoggingDeprecationHandler.INSTANCE, xc);
        if (skipFirstToken) {
            parser.nextToken();
        }
        return parser;
    }

    public static NamedXContentRegistry xContentRegistry() {
        SearchModule searchModule = new SearchModule(Settings.EMPTY, Collections.emptyList());
        return new NamedXContentRegistry(searchModule.getNamedXContents());
    }

    public static String toJsonString(ToXContentObject object) throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder();
        return xContentBuilderToString(object.toXContent(builder, ToXContent.EMPTY_PARAMS));
    }

    public static String xContentBuilderToString(XContentBuilder builder) {
        return BytesReference.bytes(builder).utf8ToString();
    }

    public static Response makeRequest(
        RestClient client,
        String method,
        String endpoint,
        Map<String, String> params,
        String jsonEntity,
        List<Header> headers
    ) throws IOException {
        HttpEntity httpEntity = Strings.isBlank(jsonEntity) ? null : new NStringEntity(jsonEntity, ContentType.APPLICATION_JSON);
        return makeRequest(client, method, endpoint, params, httpEntity, headers);
    }

    public static Response makeRequest(
        RestClient client,
        String method,
        String endpoint,
        Map<String, String> params,
        HttpEntity entity,
        List<Header> headers
    ) throws IOException {
        return makeRequest(client, method, endpoint, params, entity, headers, false);
    }

    public static Response makeRequest(
        RestClient client,
        String method,
        String endpoint,
        Map<String, String> params,
        HttpEntity entity,
        List<Header> headers,
        boolean strictDeprecationMode
    ) throws IOException {
        Request request = new Request(method, endpoint);

        RequestOptions.Builder options = RequestOptions.DEFAULT.toBuilder();
        if (headers != null) {
            headers.forEach(header -> options.addHeader(header.getName(), header.getValue()));
        }
        options.setWarningsHandler(strictDeprecationMode ? WarningsHandler.STRICT : WarningsHandler.PERMISSIVE);
        request.setOptions(options.build());

        if (params != null) {
            params.entrySet().forEach(it -> request.addParameter(it.getKey(), it.getValue()));
        }
        if (entity != null) {
            request.setEntity(entity);
        }
        return client.performRequest(request);
    }

    public static HttpEntity toHttpEntity(ToXContentObject object) throws IOException {
        return new StringEntity(toJsonString(object), APPLICATION_JSON);
    }

    public static HttpEntity toHttpEntity(String jsonString) throws IOException {
        return new StringEntity(jsonString, APPLICATION_JSON);
    }

    public static RestStatus restStatus(Response response) {
        return RestStatus.fromCode(response.getStatusLine().getStatusCode());
    }

    public static String httpEntityToString(HttpEntity entity) throws IOException {
        InputStream inputStream = entity.getContent();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "iso-8859-1"));
        StringBuilder sb = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            sb.append(line + "\n");
        }
        return sb.toString();
    }

    public static RestRequest getKMeansRestRequest() {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_ALGORITHM, FunctionName.KMEANS.name());
        final String requestContent = "{\"parameters\":{\"centroids\":3,\"iterations\":10,\"distance_type\":"
            + "\"COSINE\"},\"input_query\":{\"_source\":[\"petal_length_in_cm\",\"petal_width_in_cm\"],"
            + "\"size\":10000},\"input_index\":[\"iris_data\"]}";
        RestRequest request = new FakeRestRequest.Builder(getXContentRegistry())
            .withParams(params)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();
        return request;
    }

    public static RestRequest getStatsRestRequest(MLStatsInput input) throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder();
        input.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String requestContent = TestHelper.xContentBuilderToString(builder);

        RestRequest request = new FakeRestRequest.Builder(getXContentRegistry())
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();
        return request;
    }

    public static RestRequest getStatsRestRequest() {
        RestRequest request = new FakeRestRequest.Builder(getXContentRegistry()).build();
        return request;
    }

    public static RestRequest getStatsRestRequest(String nodeId, String stat) {
        RestRequest request = new FakeRestRequest.Builder(getXContentRegistry())
            .withParams(ImmutableMap.of("nodeId", nodeId, "stat", stat))
            .build();
        return request;
    }

    public static RestRequest getLocalSampleCalculatorRestRequest() {
        Map<String, String> params = new HashMap<>();
        params.put(PARAMETER_ALGORITHM, FunctionName.LOCAL_SAMPLE_CALCULATOR.name());
        final String requestContent = "{\"operation\": \"max\",\"input_data\":[1.0, 2.0, 3.0]}";
        RestRequest request = new FakeRestRequest.Builder(getXContentRegistry())
            .withParams(params)
            .withContent(new BytesArray(requestContent), XContentType.JSON)
            .build();
        return request;
    }

    public static RestRequest getSearchAllRestRequest() {
        RestRequest request = new FakeRestRequest.Builder(getXContentRegistry())
            .withContent(new BytesArray(TestData.matchAllSearchQuery()), XContentType.JSON)
            .build();
        return request;
    }

    public static void verifyParsedKMeansMLInput(MLInput mlInput) {
        assertEquals(FunctionName.KMEANS, mlInput.getAlgorithm());
        assertEquals(MLInputDataType.SEARCH_QUERY, mlInput.getInputDataset().getInputDataType());
        SearchQueryInputDataset inputDataset = (SearchQueryInputDataset) mlInput.getInputDataset();
        assertEquals(1, inputDataset.getIndices().size());
        assertEquals("iris_data", inputDataset.getIndices().get(0));
        KMeansParams kMeansParams = (KMeansParams) mlInput.getParameters();
        assertEquals(3, kMeansParams.getCentroids().intValue());
    }

    private static NamedXContentRegistry getXContentRegistry() {
        SearchModule searchModule = new SearchModule(Settings.EMPTY, Collections.emptyList());
        List<NamedXContentRegistry.Entry> entries = new ArrayList<>();
        entries.addAll(searchModule.getNamedXContents());
        entries.add(KMeansParams.XCONTENT_REGISTRY);
        entries.add(LocalSampleCalculatorInput.XCONTENT_REGISTRY);
        return new NamedXContentRegistry(entries);
    }
}
