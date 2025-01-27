/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.ml.searchext;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.searchext.MLInferenceRequestParameters.ML_INFERENCE_FIELD;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentHelper;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.plugins.SearchPlugin;
import org.opensearch.search.SearchModule;
import org.opensearch.searchpipelines.questionanswering.generative.ext.GenerativeQAParamExtBuilder;
import org.opensearch.test.OpenSearchTestCase;

public class MLInferenceRequestParametersExtBuilderTests extends OpenSearchTestCase {

    public NamedXContentRegistry xContentRegistry = new NamedXContentRegistry(new SearchModule(Settings.EMPTY, List.of(new SearchPlugin() {
        @Override
        public List<SearchPlugin.SearchExtSpec<?>> getSearchExts() {
            return List
                .of(
                    new SearchPlugin.SearchExtSpec<>(
                        MLInferenceRequestParametersExtBuilder.NAME,
                        MLInferenceRequestParametersExtBuilder::new,
                        parser -> MLInferenceRequestParametersExtBuilder.parse(parser)
                    )
                );
        }
    })).getNamedXContents());

    public void testParse() throws IOException {
        String requiredJsonStr = "{\"llm_question\":\"this is test llm question\"}";

        XContentParser parser = XContentType.JSON.xContent().createParser(xContentRegistry, null, requiredJsonStr);

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        MLInferenceRequestParametersExtBuilder builder = MLInferenceRequestParametersExtBuilder.parse(parser);
        assertNotNull(builder);
        assertNotNull(builder.getRequestParameters());
        MLInferenceRequestParameters params = builder.getRequestParameters();
        Assert.assertEquals("this is test llm question", params.getParams().get("llm_question"));
    }

    @Test
    public void testMultipleParameters() throws IOException {
        Map<String, Object> params = new HashMap<>();
        params.put("query_text", "foo");
        params.put("model_id", "model1");
        params.put("max_tokens", 100);
        MLInferenceRequestParameters requestParameters = new MLInferenceRequestParameters(params);
        MLInferenceRequestParametersExtBuilder builder = new MLInferenceRequestParametersExtBuilder();
        builder.setRequestParameters(requestParameters);

        BytesStreamOutput out = new BytesStreamOutput();
        builder.writeTo(out);

        MLInferenceRequestParametersExtBuilder deserialized = new MLInferenceRequestParametersExtBuilder(out.bytes().streamInput());
        assertEquals(builder, deserialized);
        assertEquals(params, deserialized.getRequestParameters().getParams());
    }

    @Test
    public void testParseWithEmptyObject() throws IOException {
        String emptyJsonStr = "{}";
        XContentParser parser = XContentType.JSON.xContent().createParser(xContentRegistry, null, emptyJsonStr);
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        MLInferenceRequestParametersExtBuilder builder = MLInferenceRequestParametersExtBuilder.parse(parser);
        assertNotNull(builder);
        assertNotNull(builder.getRequestParameters());
        assertTrue(builder.getRequestParameters().getParams().isEmpty());
    }

    @Test
    public void testWriteableName() throws IOException {
        MLInferenceRequestParametersExtBuilder builder = new MLInferenceRequestParametersExtBuilder();
        assertEquals(builder.getWriteableName(), ML_INFERENCE_FIELD);
    }

    @Test
    public void testEquals() throws IOException {
        MLInferenceRequestParametersExtBuilder MlInferenceParamBuilder = new MLInferenceRequestParametersExtBuilder();
        GenerativeQAParamExtBuilder qaParamExtBuilder = new GenerativeQAParamExtBuilder();
        assertEquals(MlInferenceParamBuilder.equals(qaParamExtBuilder), false);
        assertEquals(MlInferenceParamBuilder.equals(null), false);
    }

    @Test
    public void testMLInferenceRequestParametersEqualsWithNull() {
        Map<String, Object> params = new HashMap<>();
        params.put("query_text", "foo");
        MLInferenceRequestParameters parameters = new MLInferenceRequestParameters(params);
        assertFalse(parameters.equals(null));
    }

    @Test
    public void testMLInferenceRequestParametersEqualsWithDifferentClass() {
        Map<String, Object> params = new HashMap<>();
        params.put("query_text", "foo");
        MLInferenceRequestParameters parameters = new MLInferenceRequestParameters(params);
        assertFalse(parameters.equals("not a MLInferenceRequestParameters object"));
    }

    @Test
    public void testMLInferenceRequestParametersToXContentWithEmptyParams() throws IOException {
        MLInferenceRequestParameters parameters = new MLInferenceRequestParameters(new HashMap<>());
        XContentBuilder builder = XContentFactory.jsonBuilder();
        builder.startObject();
        parameters.toXContent(builder, ToXContent.EMPTY_PARAMS);
        builder.endObject();
        assertEquals("{\"ml_inference\":{}}", builder.toString());
    }

    @Test
    public void testMLInferenceRequestParametersExtBuilderToXContentWithEmptyParams() throws IOException {
        MLInferenceRequestParameters parameters = new MLInferenceRequestParameters(new HashMap<>());
        MLInferenceRequestParametersExtBuilder builder = new MLInferenceRequestParametersExtBuilder();
        builder.setRequestParameters(parameters);
        XContentBuilder xContentBuilder = XContentFactory.jsonBuilder();
        xContentBuilder.startObject();
        builder.toXContent(xContentBuilder, ToXContent.EMPTY_PARAMS);
        xContentBuilder.endObject();
        assertEquals("{\"ml_inference\":{}}", xContentBuilder.toString());
    }

    @Test
    public void testMLInferenceRequestParametersStreamRoundTripWithNullParams() throws IOException {
        MLInferenceRequestParameters original = new MLInferenceRequestParameters();
        original.setParams(null);
        BytesStreamOutput out = new BytesStreamOutput();
        original.writeTo(out);
        MLInferenceRequestParameters deserialized = new MLInferenceRequestParameters(out.bytes().streamInput());
        assertNull(deserialized.getParams());
    }

    @Test
    public void testMLInferenceRequestParametersExtBuilderStreamRoundTripWithNullParams() throws IOException {
        MLInferenceRequestParametersExtBuilder original = new MLInferenceRequestParametersExtBuilder();
        original.setRequestParameters(null);
        BytesStreamOutput out = new BytesStreamOutput();
        assertThrows(NullPointerException.class, () -> original.writeTo(out));
    }

    @Test
    public void testEqualsAndHashCode() {
        Map<String, Object> params1 = new HashMap<>();
        params1.put("query_text", "foo");
        MLInferenceRequestParameters requestParameters1 = new MLInferenceRequestParameters(params1);
        MLInferenceRequestParametersExtBuilder builder1 = new MLInferenceRequestParametersExtBuilder();
        builder1.setRequestParameters(requestParameters1);

        Map<String, Object> params2 = new HashMap<>();
        params2.put("query_text", "foo");
        MLInferenceRequestParameters requestParameters2 = new MLInferenceRequestParameters(params2);
        MLInferenceRequestParametersExtBuilder builder2 = new MLInferenceRequestParametersExtBuilder();
        builder2.setRequestParameters(requestParameters2);

        assertEquals(builder1, builder2);
        assertEquals(builder1.hashCode(), builder2.hashCode());

        Map<String, Object> params3 = new HashMap<>();
        params3.put("query_text", "bar");
        MLInferenceRequestParameters requestParameters3 = new MLInferenceRequestParameters(params3);
        MLInferenceRequestParametersExtBuilder builder3 = new MLInferenceRequestParametersExtBuilder();
        builder3.setRequestParameters(requestParameters3);

        assertNotEquals(builder1, builder3);
        assertNotEquals(builder1.hashCode(), builder3.hashCode());
    }

    @Test
    public void testXContentRoundTrip() throws IOException {
        Map<String, Object> params = new HashMap<>();
        params.put("query_text", "foo");
        MLInferenceRequestParameters requestParameters = new MLInferenceRequestParameters(params);
        MLInferenceRequestParametersExtBuilder mlInferenceExtBuilder = new MLInferenceRequestParametersExtBuilder();
        mlInferenceExtBuilder.setRequestParameters(requestParameters);

        XContentType xContentType = randomFrom(XContentType.values());
        BytesReference serialized = XContentHelper.toXContent(mlInferenceExtBuilder, xContentType, true);

        XContentParser parser = createParser(xContentType.xContent(), serialized);

        MLInferenceRequestParametersExtBuilder deserialized = MLInferenceRequestParametersExtBuilder.parse(parser);

        assertEquals(deserialized.getRequestParameters().getParams().get(ML_INFERENCE_FIELD), params);

    }

    @Test
    public void testStreamRoundTrip() throws IOException {
        Map<String, Object> params = new HashMap<>();
        params.put("query_text", "foo");
        MLInferenceRequestParameters requestParameters = new MLInferenceRequestParameters();
        requestParameters.setParams(params);
        MLInferenceRequestParametersExtBuilder mlInferenceExtBuilder = new MLInferenceRequestParametersExtBuilder();
        mlInferenceExtBuilder.setRequestParameters(requestParameters);
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        mlInferenceExtBuilder.writeTo(bytesStreamOutput);

        MLInferenceRequestParametersExtBuilder deserialized = new MLInferenceRequestParametersExtBuilder(
            bytesStreamOutput.bytes().streamInput()
        );
        assertEquals(mlInferenceExtBuilder, deserialized);
    }

    @Test
    public void testNullRequestParameters() throws IOException {
        MLInferenceRequestParametersExtBuilder builder = new MLInferenceRequestParametersExtBuilder();
        assertNull(builder.getRequestParameters());

        BytesStreamOutput out = new BytesStreamOutput();

        // Expect NullPointerException when writing null requestParameters
        assertThrows(NullPointerException.class, () -> builder.writeTo(out));

        // Test that we can still create a new builder with null requestParameters
        MLInferenceRequestParametersExtBuilder newBuilder = new MLInferenceRequestParametersExtBuilder();
        assertNull(newBuilder.getRequestParameters());
    }

    @Test
    public void testEmptyRequestParameters() throws IOException {
        MLInferenceRequestParameters emptyParams = new MLInferenceRequestParameters(new HashMap<>());
        MLInferenceRequestParametersExtBuilder builder = new MLInferenceRequestParametersExtBuilder();
        builder.setRequestParameters(emptyParams);

        BytesStreamOutput out = new BytesStreamOutput();
        builder.writeTo(out);

        MLInferenceRequestParametersExtBuilder deserialized = new MLInferenceRequestParametersExtBuilder(out.bytes().streamInput());
        assertNotNull(deserialized.getRequestParameters());
        assertTrue(deserialized.getRequestParameters().getParams().isEmpty());
    }

    @Test
    public void testToXContent() throws IOException {
        Map<String, Object> params = new HashMap<>();
        params.put("query_text", "foo");
        MLInferenceRequestParameters requestParameters = new MLInferenceRequestParameters(params);
        MLInferenceRequestParametersExtBuilder builder = new MLInferenceRequestParametersExtBuilder();
        builder.setRequestParameters(requestParameters);

        XContentBuilder xContentBuilder = XContentFactory.jsonBuilder();
        xContentBuilder.startObject();
        builder.toXContent(xContentBuilder, ToXContent.EMPTY_PARAMS);
        xContentBuilder.endObject();

        String expected = "{\"ml_inference\":{\"query_text\":\"foo\"}}";
        assertEquals(expected, xContentBuilder.toString());
    }

    @Test
    public void testMLInferenceRequestParametersEqualsAndHashCode() {
        Map<String, Object> params1 = new HashMap<>();
        params1.put("query_text", "foo");
        MLInferenceRequestParameters requestParameters1 = new MLInferenceRequestParameters(params1);

        Map<String, Object> params2 = new HashMap<>();
        params2.put("query_text", "foo");
        MLInferenceRequestParameters requestParameters2 = new MLInferenceRequestParameters(params2);

        Map<String, Object> params3 = new HashMap<>();
        params3.put("query_text", "bar");
        MLInferenceRequestParameters requestParameters3 = new MLInferenceRequestParameters(params3);

        assertEquals(requestParameters1, requestParameters2);
        assertEquals(requestParameters1.hashCode(), requestParameters2.hashCode());
        assertNotEquals(requestParameters1, requestParameters3);
        assertNotEquals(requestParameters1.hashCode(), requestParameters3.hashCode());
    }

    @Test
    public void testMLInferenceRequestParametersToXContent() throws IOException {
        Map<String, Object> params = new HashMap<>();
        params.put("query_text", "foo");
        MLInferenceRequestParameters requestParameters = new MLInferenceRequestParameters(params);

        XContentBuilder xContentBuilder = XContentFactory.jsonBuilder();
        xContentBuilder.startObject();
        requestParameters.toXContent(xContentBuilder, ToXContent.EMPTY_PARAMS);
        xContentBuilder.endObject();

        String expected = "{\"ml_inference\":{\"query_text\":\"foo\"}}";
        assertEquals(expected, xContentBuilder.toString());
    }
}
