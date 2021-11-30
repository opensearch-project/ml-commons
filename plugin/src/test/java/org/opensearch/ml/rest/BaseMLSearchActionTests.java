package org.opensearch.ml.rest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.rest.BaseMLAction.PARAMETER_ALGORITHM;
import static org.opensearch.ml.rest.BaseMLAction.PARAMETER_MODEL_ID;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
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
import org.opensearch.index.query.QueryStringQueryBuilder;
import org.opensearch.ml.common.dataset.SearchQueryInputDataset;
import org.opensearch.ml.common.parameter.FunctionName;
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

    private BaseMLAction baseMLSearchAction;

    private NamedWriteableRegistry namedWriteableRegistry;

    private NodeClient client;

    @Before
    public void setup() {
        baseMLSearchAction = new BaseMLAction();

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
        BaseMLAction baseMLSearchAction = new BaseMLAction();
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
        Map<String, String> param = ImmutableMap.<String, String>builder().put(PARAMETER_ALGORITHM, FunctionName.KMEANS.name()).build();
        FakeRestRequest fakeRestRequest = new FakeRestRequest.Builder(xContentRegistry()).withParams(param).build();
        String algorithm = baseMLSearchAction.getAlgorithm(fakeRestRequest);
        assertFalse(Strings.isNullOrEmpty(algorithm));
        assertEquals(algorithm, FunctionName.KMEANS.name());
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
    public void testGetSearchQueryWithoutSearchInput() throws IOException {
        Map<String, String> param = ImmutableMap
            .<String, String>builder()
            .put(PARAMETER_ALGORITHM, FunctionName.KMEANS.name())
            .put("index", "index1,index2")
            .build();
        FakeRestRequest fakeRestRequest = new FakeRestRequest.Builder(xContentRegistry()).withParams(param).build();

        SearchQueryInputDataset searchQueryInputDataset = baseMLSearchAction.buildSearchQueryInput(fakeRestRequest, client);
        assertNotNull(searchQueryInputDataset);
        assertNotNull(searchQueryInputDataset.getSearchSourceBuilder());
        assertNull(searchQueryInputDataset.getSearchSourceBuilder().query());
    }

    @Test
    public void testGetSearchQueryInvalidRequestBody() throws IOException {
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
    public void testGetSearchQueryWithSearchParams() throws IOException {
        Map<String, String> param = ImmutableMap
            .<String, String>builder()
            .put(PARAMETER_ALGORITHM, FunctionName.KMEANS.name())
            .put("index", "index1,index2")
            .put("q", "user:dilbert")
            .build();
        FakeRestRequest fakeRestRequest = new FakeRestRequest.Builder(xContentRegistry()).withParams(param).build();

        SearchQueryInputDataset searchQueryInputDataset = baseMLSearchAction.buildSearchQueryInput(fakeRestRequest, client);
        assertNotNull(searchQueryInputDataset);
        assertNotNull(searchQueryInputDataset.getSearchSourceBuilder());
        assertNotNull(searchQueryInputDataset.getSearchSourceBuilder().query());

        QueryStringQueryBuilder queryBuilder = (QueryStringQueryBuilder) searchQueryInputDataset.getSearchSourceBuilder().query();
        assertEquals("user:dilbert", queryBuilder.queryString());
    }

    @Test
    public void testGetSearchQueryWithSearchRequestBody() throws IOException {
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
