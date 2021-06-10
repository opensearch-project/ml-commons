package org.opensearch.ml.rest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.rest.BaseMLSearchAction.ML_PARAMETERS;
import static org.opensearch.ml.rest.BaseMLSearchAction.PARAMETER_ALGORITHM;
import static org.opensearch.ml.rest.BaseMLSearchAction.PARAMETER_MODEL_ID;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.OpenSearchParseException;
import org.opensearch.client.node.NodeClient;
import org.opensearch.cluster.metadata.ComponentTemplateMetadata;
import org.opensearch.common.ParsingException;
import org.opensearch.common.Strings;
import org.opensearch.common.bytes.BytesArray;
import org.opensearch.common.bytes.BytesReference;
import org.opensearch.common.io.stream.NamedWriteableRegistry;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.ml.common.dataset.SearchQueryInputDataset;
import org.opensearch.ml.common.parameter.MLParameter;
import org.opensearch.ml.plugin.MachineLearningPlugin;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;

import com.google.common.collect.ImmutableMap;

public class BaseMLSearchActionTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private BaseMLSearchAction baseMLSearchAction;

    private NamedWriteableRegistry namedWriteableRegistry;

    private NodeClient client;

    @Before
    public void setup() {
        baseMLSearchAction = new BaseMLSearchAction();

        namedWriteableRegistry = new NamedWriteableRegistry(
            Collections
                .singletonList(
                    new NamedWriteableRegistry.Entry(
                        ComponentTemplateMetadata.class,
                        ComponentTemplateMetadata.TYPE,
                        ComponentTemplateMetadata::new
                    )
                )
        );

        client = mock(NodeClient.class);
        when(client.getNamedWriteableRegistry()).thenReturn(namedWriteableRegistry);
    }

    @Test
    public void testConstructor() {
        BaseMLSearchAction baseMLSearchAction = new BaseMLSearchAction();
        assertNotNull(baseMLSearchAction);
    }

    @Test
    public void testGetName() {
        String actionName = baseMLSearchAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("base_ml_search_action", actionName);
    }

    @Test
    public void testRoutes() {
        List<RestHandler.Route> routes = baseMLSearchAction.routes();
        assertNotNull(routes);
        assertTrue(routes.isEmpty());
    }

    @Test
    public void testGetAlgorithmWithoutInput() {
        thrown.expect(IllegalArgumentException.class);
        Map<String, String> param = ImmutableMap.of();
        FakeRestRequest fakeRestRequest = new FakeRestRequest.Builder(xContentRegistry()).withParams(param).build();
        String algorithm = baseMLSearchAction.getAlgorithm(fakeRestRequest);
        assertTrue(false);
    }

    @Test
    public void testGetAlgorithmWithEmptyInput() {
        thrown.expect(IllegalArgumentException.class);
        Map<String, String> param = ImmutableMap.<String, String>builder().put(PARAMETER_ALGORITHM, "").build();
        FakeRestRequest fakeRestRequest = new FakeRestRequest.Builder(xContentRegistry()).withParams(param).build();
        String algorithm = baseMLSearchAction.getAlgorithm(fakeRestRequest);
        assertTrue(false);
    }

    @Test
    public void testGetAlgorithmWithValidInput() {
        Map<String, String> param = ImmutableMap.<String, String>builder().put(PARAMETER_ALGORITHM, "kmeans").build();
        FakeRestRequest fakeRestRequest = new FakeRestRequest.Builder(xContentRegistry()).withParams(param).build();
        String algorithm = baseMLSearchAction.getAlgorithm(fakeRestRequest);
        assertFalse(Strings.isNullOrEmpty(algorithm));
        assertEquals(algorithm, "kmeans");
    }

    @Test
    public void testGetModelIdmWithoutInput() {
        thrown.expect(IllegalArgumentException.class);
        Map<String, String> param = ImmutableMap.of();
        FakeRestRequest fakeRestRequest = new FakeRestRequest.Builder(xContentRegistry()).withParams(param).build();
        String modelId = baseMLSearchAction.getModelId(fakeRestRequest);
        assertTrue(false);
    }

    @Test
    public void testGetModelIdWithEmptyInput() {
        thrown.expect(IllegalArgumentException.class);
        Map<String, String> param = ImmutableMap.<String, String>builder().put(PARAMETER_MODEL_ID, "").build();
        FakeRestRequest fakeRestRequest = new FakeRestRequest.Builder(xContentRegistry()).withParams(param).build();
        String modelId = baseMLSearchAction.getModelId(fakeRestRequest);
        assertTrue(false);
    }

    @Test
    public void testGetModelIdWithValidInput() {
        Map<String, String> param = ImmutableMap.<String, String>builder().put(PARAMETER_MODEL_ID, "mock_model_id").build();
        FakeRestRequest fakeRestRequest = new FakeRestRequest.Builder(xContentRegistry()).withParams(param).build();
        String modelId = baseMLSearchAction.getModelId(fakeRestRequest);
        assertFalse(Strings.isNullOrEmpty(modelId));
        assertEquals(modelId, "mock_model_id");
    }

    @Test
    public void testGetMLParametersWithoutRequestBody() throws IOException {
        thrown.expect(OpenSearchParseException.class);
        Map<String, String> param = ImmutableMap.of();
        FakeRestRequest fakeRestRequest = new FakeRestRequest.Builder(xContentRegistry()).withParams(param).build();
        List<MLParameter> mlParameters = baseMLSearchAction.getMLParameters(fakeRestRequest);
        assertTrue(mlParameters.isEmpty());
    }

    @Test
    public void testGetMLParametersWithoutInput() throws IOException {
        XContentBuilder xContentBuilder = XContentFactory
            .jsonBuilder()
            .startObject()
            .startObject("type1")
            .startObject("properties")
            .startObject("location")
            .field("type", "geo_point")
            .endObject()
            .endObject()
            .endObject()
            .endObject();

        Map<String, String> param = ImmutableMap.of();
        FakeRestRequest fakeRestRequest = buildFakeRestRequest(param, xContentBuilder);

        List<MLParameter> mlParameters = baseMLSearchAction.getMLParameters(fakeRestRequest);
        assertTrue(mlParameters.isEmpty());
    }

    @Test
    public void testGetMLParametersWithEmptyInput() throws IOException {
        XContentBuilder xContentBuilder = XContentFactory.jsonBuilder().startObject().startObject(ML_PARAMETERS).endObject().endObject();

        Map<String, String> param = ImmutableMap.of();
        FakeRestRequest fakeRestRequest = buildFakeRestRequest(param, xContentBuilder);

        List<MLParameter> mlParameters = baseMLSearchAction.getMLParameters(fakeRestRequest);
        assertTrue(mlParameters.isEmpty());
    }

    @Test
    public void testGetMLParametersWithValidInput() throws IOException {
        XContentBuilder xContentBuilder = XContentFactory
            .jsonBuilder()
            .startObject()
            .startObject(ML_PARAMETERS)
            .field("paramName1", "value1")
            .field("paramName2", 123)
            .endObject()
            .endObject();

        Map<String, String> param = ImmutableMap.of();
        FakeRestRequest fakeRestRequest = buildFakeRestRequest(param, xContentBuilder);

        List<MLParameter> mlParameters = baseMLSearchAction.getMLParameters(fakeRestRequest);
        assertFalse(mlParameters.isEmpty());
        assertEquals(2, mlParameters.size());

        MLParameter mlParam2 = mlParameters.get(0);
        assertNotNull(mlParam2);
        assertEquals("paramName2", mlParam2.getName());
        assertEquals(123, mlParam2.getValue());

        MLParameter mlParam1 = mlParameters.get(1);
        assertNotNull(mlParam1);
        assertEquals("paramName1", mlParam1.getName());
        assertEquals("value1", mlParam1.getValue());
    }

    @Test
    public void testGetSearchQueryWithoutInput() throws IOException {
        thrown.expect(ParsingException.class);
        XContentBuilder xContentBuilder = XContentFactory
            .jsonBuilder()
            .startObject()
            .startObject("type1")
            .startObject("properties")
            .startObject("location")
            .field("type", "geo_point")
            .endObject()
            .endObject()
            .endObject()
            .endObject();

        Map<String, String> param = ImmutableMap.of();
        FakeRestRequest fakeRestRequest = buildFakeRestRequest(param, xContentBuilder);

        baseMLSearchAction.buildSearchQueryInput(fakeRestRequest, client);
        assertTrue(false);
    }

    @Test
    public void testGetSearchQueryWithoutIndices() throws IOException {
        thrown.expect(IllegalArgumentException.class);

        String restContent = " { \"_source\": { \"includes\": \"include\", \"excludes\": \"*.field2\"}}";
        BytesArray bytesContent = new BytesArray(restContent.getBytes(StandardCharsets.UTF_8));

        Map<String, String> param = ImmutableMap.of();
        FakeRestRequest fakeRestRequest = new FakeRestRequest.Builder(xContentRegistry())
            .withMethod(RestRequest.Method.GET)
            .withPath(MachineLearningPlugin.ML_BASE_URI + "/training/")
            .withParams(param)
            .withContent(bytesContent, XContentType.JSON)
            .build();

        baseMLSearchAction.buildSearchQueryInput(fakeRestRequest, client);
        assertTrue(false);
    }

    @Test
    public void testGetSearchQueryWithValidInput() throws IOException {
        String restContent = " { \"_source\": { \"includes\": \"include\", \"excludes\": \"*.field2\"}}";
        BytesArray bytesContent = new BytesArray(restContent.getBytes(StandardCharsets.UTF_8));

        Map<String, String> param = ImmutableMap.<String, String>builder().put("index", "index1,index2").build();
        FakeRestRequest fakeRestRequest = new FakeRestRequest.Builder(xContentRegistry())
            .withMethod(RestRequest.Method.GET)
            .withPath(MachineLearningPlugin.ML_BASE_URI + "/training/")
            .withParams(param)
            .withContent(bytesContent, XContentType.JSON)
            .build();

        SearchQueryInputDataset searchQueryInputDataset = baseMLSearchAction.buildSearchQueryInput(fakeRestRequest, client);
        assertNotNull(searchQueryInputDataset);

        assertNotNull(searchQueryInputDataset.getIndices());
        assertEquals(2, searchQueryInputDataset.getIndices().size());
        assertEquals("index1", searchQueryInputDataset.getIndices().get(0));
        assertEquals("index2", searchQueryInputDataset.getIndices().get(1));

        SearchSourceBuilder searchSourceBuilder = searchQueryInputDataset.getSearchSourceBuilder();
        assertNotNull(searchSourceBuilder);
        assertEquals("include", searchSourceBuilder.fetchSource().includes()[0]);
        assertEquals("*.field2", searchSourceBuilder.fetchSource().excludes()[0]);
    }

    private FakeRestRequest buildFakeRestRequest(Map<String, String> param, XContentBuilder xContentBuilder) {
        return new FakeRestRequest.Builder(xContentRegistry())
            .withMethod(RestRequest.Method.GET)
            .withPath(MachineLearningPlugin.ML_BASE_URI + "/training/")
            .withParams(param)
            .withContent(BytesReference.bytes(xContentBuilder), xContentBuilder.contentType())
            .build();
    }
}
