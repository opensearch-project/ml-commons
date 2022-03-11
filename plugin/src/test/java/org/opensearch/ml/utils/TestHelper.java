/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import static org.apache.http.entity.ContentType.APPLICATION_JSON;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.math3.distribution.MultivariateNormalDistribution;
import org.apache.commons.math3.random.JDKRandomGenerator;
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
import org.opensearch.ml.common.dataframe.ColumnMeta;
import org.opensearch.ml.common.dataframe.ColumnType;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DataFrameBuilder;
import org.opensearch.rest.RestStatus;
import org.opensearch.search.SearchModule;

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
        SearchModule searchModule = new SearchModule(Settings.EMPTY, false, Collections.emptyList());
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

    public static DataFrame constructTestDataFrame(int size) {
        ColumnMeta[] columnMetas = new ColumnMeta[] { new ColumnMeta("f1", ColumnType.DOUBLE), new ColumnMeta("f2", ColumnType.DOUBLE) };
        DataFrame dataFrame = DataFrameBuilder.emptyDataFrame(columnMetas);

        Random random = new Random(1);
        MultivariateNormalDistribution g1 = new MultivariateNormalDistribution(
            new JDKRandomGenerator(random.nextInt()),
            new double[] { 0.0, 0.0 },
            new double[][] { { 2.0, 1.0 }, { 1.0, 2.0 } }
        );
        MultivariateNormalDistribution g2 = new MultivariateNormalDistribution(
            new JDKRandomGenerator(random.nextInt()),
            new double[] { 10.0, 10.0 },
            new double[][] { { 2.0, 1.0 }, { 1.0, 2.0 } }
        );
        MultivariateNormalDistribution[] normalDistributions = new MultivariateNormalDistribution[] { g1, g2 };
        for (int i = 0; i < size; ++i) {
            int id = 0;
            if (Math.random() < 0.5) {
                id = 1;
            }
            double[] sample = normalDistributions[id].sample();
            dataFrame.appendRow(Arrays.stream(sample).boxed().toArray(Double[]::new));
        }

        return dataFrame;
    }
}
