/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.processor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.opensearch.ml.common.utils.StringUtils.toJson;
import static org.opensearch.ml.processor.InferenceProcessorAttributes.INPUT_MAP;
import static org.opensearch.ml.processor.InferenceProcessorAttributes.MAX_PREDICTION_TASKS;
import static org.opensearch.ml.processor.InferenceProcessorAttributes.MODEL_CONFIG;
import static org.opensearch.ml.processor.InferenceProcessorAttributes.MODEL_ID;
import static org.opensearch.ml.processor.InferenceProcessorAttributes.OUTPUT_MAP;
import static org.opensearch.ml.processor.MLInferenceSearchRequestProcessor.DEFAULT_MAX_PREDICTION_TASKS;
import static org.opensearch.ml.processor.MLInferenceSearchRequestProcessor.FULL_RESPONSE_PATH;
import static org.opensearch.ml.processor.MLInferenceSearchRequestProcessor.FUNCTION_NAME;
import static org.opensearch.ml.processor.MLInferenceSearchRequestProcessor.MODEL_INPUT;
import static org.opensearch.ml.processor.MLInferenceSearchRequestProcessor.OPTIONAL_INPUT_MAP;
import static org.opensearch.ml.processor.MLInferenceSearchRequestProcessor.OPTIONAL_OUTPUT_MAP;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchParseException;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.RangeQueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.index.query.TermsQueryBuilder;
import org.opensearch.index.query.functionscore.ScriptScoreQueryBuilder;
import org.opensearch.ingest.Processor;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.repackage.com.google.common.collect.ImmutableMap;
import org.opensearch.ml.searchext.MLInferenceRequestParameters;
import org.opensearch.ml.searchext.MLInferenceRequestParametersExtBuilder;
import org.opensearch.plugins.SearchPlugin;
import org.opensearch.script.Script;
import org.opensearch.script.ScriptType;
import org.opensearch.search.SearchModule;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.pipeline.PipelineProcessingContext;
import org.opensearch.test.AbstractBuilderTestCase;
import org.opensearch.transport.client.Client;

public class MLInferenceSearchRequestProcessorTests extends AbstractBuilderTestCase {

    @Mock
    private Client client;

    @Mock
    private PipelineProcessingContext requestContext;

    static public NamedXContentRegistry TEST_XCONTENT_REGISTRY_FOR_QUERY;
    private static final String PROCESSOR_TAG = "inference";
    private static final String DESCRIPTION = "inference_test";

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

        TEST_XCONTENT_REGISTRY_FOR_QUERY = new NamedXContentRegistry(new SearchModule(Settings.EMPTY, List.of(new SearchPlugin() {
            @Override
            public List<SearchExtSpec<?>> getSearchExts() {
                return List
                    .of(
                        new SearchExtSpec<>(
                            MLInferenceRequestParametersExtBuilder.NAME,
                            MLInferenceRequestParametersExtBuilder::new,
                            parser -> MLInferenceRequestParametersExtBuilder.parse(parser)
                        )
                    );
            }
        })).getNamedXContents());
    }

    /**
     * Tests that an exception is thrown when the `processRequest` method is called, as this processor
     * makes asynchronous calls and does not support synchronous processing.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testProcessRequestException() throws Exception {

        String modelInputField = "inputs";
        String originalQueryField = "query.term.text.value";
        String newQueryField = "query.term.text.value";
        String modelOutputField = "response";
        MLInferenceSearchRequestProcessor requestProcessor = getMlInferenceSearchRequestProcessor(
            null,
            modelInputField,
            originalQueryField,
            newQueryField,
            modelOutputField,
            false,
            false
        );
        QueryBuilder incomingQuery = new TermQueryBuilder("text", "foo");
        SearchSourceBuilder source = new SearchSourceBuilder().query(incomingQuery);
        SearchRequest request = new SearchRequest().source(source);

        try {
            requestProcessor.processRequest(request);

        } catch (Exception e) {
            assertEquals("ML inference search request processor make asynchronous calls and does not call processRequest", e.getMessage());
        }
    }

    /**
     * Tests the case where no input or output mappings are provided. The original search request
     * should be returned without any modifications.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testProcessRequestAsyncWithNoMappings() throws Exception {

        List<Map<String, String>> outputMaps;
        MLInferenceSearchRequestProcessor requestProcessor = new MLInferenceSearchRequestProcessor(
            "model1",
            null,
            null,
            null,
            null,
            null,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY
        );
        QueryBuilder incomingQuery = new TermQueryBuilder("text", "foo");
        SearchSourceBuilder source = new SearchSourceBuilder().query(incomingQuery);
        SearchRequest request = new SearchRequest().source(source);
        ActionListener<SearchRequest> Listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchRequest newSearchRequest) {
                assertEquals(incomingQuery, request.source().query());
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException("Failed in executing processRequestAsync.");
            }
        };

        requestProcessor.processRequestAsync(request, requestContext, Listener);

    }

    /**
     * Tests the successful rewriting of a single string in a term query based on the model output.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testExecute_rewriteSingleStringTermQuerySuccess() throws Exception {

        /**
         * example term query: {"query":{"term":{"text":{"value":"foo","boost":1.0}}}}
         */
        String modelInputField = "inputs";
        String originalQueryField = "query.term.text.value";
        String newQueryField = "query.term.text.value";
        String modelOutputField = "response";
        MLInferenceSearchRequestProcessor requestProcessor = getMlInferenceSearchRequestProcessor(
            null,
            modelInputField,
            originalQueryField,
            newQueryField,
            modelOutputField,
            false,
            false
        );
        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", "eng")).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        QueryBuilder incomingQuery = new TermQueryBuilder("text", "foo");
        SearchSourceBuilder source = new SearchSourceBuilder().query(incomingQuery);
        SearchRequest request = new SearchRequest().source(source);
        QueryBuilder expectedQuery = new TermQueryBuilder("text", "eng");

        ActionListener<SearchRequest> Listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchRequest newSearchRequest) {
                assertEquals(expectedQuery, newSearchRequest.source().query());
                assertEquals(request.toString(), newSearchRequest.toString());
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException("Failed in executing processRequestAsync." + e.getMessage());
            }
        };

        requestProcessor.processRequestAsync(request, requestContext, Listener);

    }

    /**
     * Tests the successful rewriting of multiple string in a term query based on the model output.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testExecute_rewriteMultipleStringTermQuerySuccess() throws Exception {
        /**
         * example term query: {"query":{"term":{"text":{"value":"foo","boost":1.0}}}}
         */
        String modelInputField = "inputs";
        String originalQueryField = "query.term.text.value";
        String newQueryField = "query.term.text.value";
        String modelOutputField = "response";
        MLInferenceSearchRequestProcessor requestProcessor = getMlInferenceSearchRequestProcessor(
            null,
            modelInputField,
            originalQueryField,
            newQueryField,
            modelOutputField,
            false,
            false
        );
        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", "car, truck, vehicle")).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        QueryBuilder incomingQuery = new TermQueryBuilder("text", "foo");
        SearchSourceBuilder source = new SearchSourceBuilder().query(incomingQuery);
        SearchRequest request = new SearchRequest().source(source);
        /**
         * example term query: {"query":{"term":{"text":{"value":"car, truck, vehicle","boost":1.0}}}}
         */

        ActionListener<SearchRequest> Listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchRequest newSearchRequest) {
                QueryBuilder expectedQuery = new TermQueryBuilder("text", "car, truck, vehicle");
                assertEquals(expectedQuery, newSearchRequest.source().query());
                assertEquals(request.toString(), newSearchRequest.toString());
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException("Failed in executing processRequestAsync." + e.getMessage());
            }
        };

        requestProcessor.processRequestAsync(request, requestContext, Listener);

    }

    /**
     * Tests the successful rewriting of multiple string in terms query based on the model output.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testExecute_rewriteTermsQuerySuccess() throws Exception {
        /**
         * example term query: {"query":{"terms":{"text":["foo","bar],"boost":1.0}}}
         */
        String modelInputField = "inputs";
        String originalQueryField = "query.terms.text";
        String newQueryField = "query.terms.text";
        String modelOutputField = "response";
        MLInferenceSearchRequestProcessor requestProcessor = getMlInferenceSearchRequestProcessor(
            null,
            modelInputField,
            originalQueryField,
            newQueryField,
            modelOutputField,
            false,
            false
        );
        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(ImmutableMap.of("response", Arrays.asList("car", "vehicle", "truck")))
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        QueryBuilder incomingQuery = new TermsQueryBuilder("text", Arrays.asList("foo", "bar"));
        SearchSourceBuilder source = new SearchSourceBuilder().query(incomingQuery);
        SearchRequest request = new SearchRequest().source(source);
        /**
         * example terms query: {"query":{"terms":{"text":["car","vehicle","truck"],"boost":1.0}}}
         */

        ActionListener<SearchRequest> Listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchRequest newSearchRequest) {
                QueryBuilder expectedQuery = new TermsQueryBuilder("text", Arrays.asList("car", "vehicle", "truck"));
                assertEquals(expectedQuery, newSearchRequest.source().query());
                assertEquals(request.toString(), newSearchRequest.toString());
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException("Failed in executing processRequestAsync.");
            }
        };

        requestProcessor.processRequestAsync(request, requestContext, Listener);

    }

    /**
     * Tests the successful rewriting of a double in a term query based on the model output.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testExecute_rewriteDoubleQuerySuccess() throws Exception {

        /**
         * example term query: {"query":{"term":{"text":{"value":"foo","boost":1.0}}}}
         */
        String modelInputField = "inputs";
        String originalQueryField = "query.term.text.value";
        String newQueryField = "query.term.text.value";
        String modelOutputField = "response";
        MLInferenceSearchRequestProcessor requestProcessor = getMlInferenceSearchRequestProcessor(
            null,
            modelInputField,
            originalQueryField,
            newQueryField,
            modelOutputField,
            false,
            false
        );

        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", 0.123)).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        QueryBuilder incomingQuery = new TermQueryBuilder("text", "foo");
        SearchSourceBuilder source = new SearchSourceBuilder().query(incomingQuery);
        SearchRequest request = new SearchRequest().source(source);

        /**
         * example term query: {"query":{"term":{"text":{"value":0.123,"boost":1.0}}}}
         */
        ActionListener<SearchRequest> Listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchRequest newSearchRequest) {
                QueryBuilder expectedQuery = new TermQueryBuilder("text", 0.123);
                assertEquals(expectedQuery, newSearchRequest.source().query());
                assertEquals(request.toString(), newSearchRequest.toString());
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException("Failed in executing processRequestAsync.");
            }
        };

        requestProcessor.processRequestAsync(request, requestContext, Listener);

    }

    /**
     * Tests the successful rewriting of a term query to a range query based on the model output
     * and the provided query template.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testExecute_rewriteStringFromTermQueryToRangeQuerySuccess() throws Exception {
        /**
         * example term query: {"query":{"term":{"text":{"value":"foo","boost":1.0}}}}
         */
        String modelInputField = "inputs";
        String originalQueryField = "query.term.text.value";
        String newQueryField = "modelPredictionScore";
        String modelOutputField = "response";
        String queryTemplate = "{\"query\":{\"range\":{\"text\":{\"gte\":${modelPredictionScore}}}}}";

        MLInferenceSearchRequestProcessor requestProcessor = getMlInferenceSearchRequestProcessor(
            queryTemplate,
            modelInputField,
            originalQueryField,
            newQueryField,
            modelOutputField,
            false,
            false
        );

        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", "0.123")).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        QueryBuilder incomingQuery = new TermQueryBuilder("text", "foo");
        SearchSourceBuilder source = new SearchSourceBuilder().query(incomingQuery);
        SearchRequest request = new SearchRequest().source(source);

        /**
         * example input term query: {"query":{"term":{"text":{"value":foo,"boost":1.0}}}}
         */
        /**
         * example output range query: {"query":{"range":{"text":{"gte":"2"}}}}
         */

        ActionListener<SearchRequest> Listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchRequest newSearchRequest) {
                RangeQueryBuilder expectedQuery = new RangeQueryBuilder("text");
                expectedQuery.from(0.123);
                expectedQuery.includeLower(true);
                assertEquals(expectedQuery, newSearchRequest.source().query());
                assertEquals(request.toString(), newSearchRequest.toString());
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException("Failed in executing processRequestAsync.");
            }
        };

        requestProcessor.processRequestAsync(request, requestContext, Listener);

    }

    /**
     * Tests the successful rewriting of a term query to a range query based on the model output
     * and the provided query template, where the model output is a double value.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testExecute_rewriteDoubleFromTermQueryToRangeQuerySuccess() throws Exception {
        String modelInputField = "inputs";
        String originalQueryField = "query.term.text.value";
        String newQueryField = "modelPredictionScore";
        String modelOutputField = "response";
        String queryTemplate = "{\"query\":{\"range\":{\"text\":{\"gte\":${modelPredictionScore}}}}}";

        MLInferenceSearchRequestProcessor requestProcessor = getMlInferenceSearchRequestProcessor(
            queryTemplate,
            modelInputField,
            originalQueryField,
            newQueryField,
            modelOutputField,
            false,
            false
        );

        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", 0.123)).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        QueryBuilder incomingQuery = new TermQueryBuilder("text", "foo");
        SearchSourceBuilder source = new SearchSourceBuilder().query(incomingQuery);
        SearchRequest request = new SearchRequest().source(source);

        /**
         * example input term query: {"query":{"term":{"text":{"value":"foo","boost":1.0}}}}
         */
        /**
         * example output range query: {"query":{"range":{"text":{"gte":0.123}}}}
         */

        ActionListener<SearchRequest> Listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchRequest newSearchRequest) {
                RangeQueryBuilder expectedQuery = new RangeQueryBuilder("text");
                expectedQuery.from(0.123);
                expectedQuery.includeLower(true);
                assertEquals(expectedQuery, newSearchRequest.source().query());
                assertEquals(request.toString(), newSearchRequest.toString());
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException("Failed in executing processRequestAsync.");
            }
        };

        requestProcessor.processRequestAsync(request, requestContext, Listener);

    }

    /**
     * Tests that numeric types (Integer) are preserved in query template substitution
     * and not converted to quoted strings. This is critical for range queries on integer fields
     * which require unquoted numeric values after OpenSearch PR #20518 added stricter validation.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testExecute_numericTypePreservationInRangeQuery() throws Exception {
        String modelInputField = "inputs";
        String originalQueryField = "query.term.text.value";
        String newQueryField = "modelPrediction";
        String modelOutputField = "embedding.length()";
        String queryTemplate = "{\"query\":{\"range\":{\"embedding_size\":{\"lte\":${modelPrediction}}}}}";

        MLInferenceSearchRequestProcessor requestProcessor = getMlInferenceSearchRequestProcessor(
            queryTemplate,
            modelInputField,
            originalQueryField,
            newQueryField,
            modelOutputField,
            false,
            false
        );

        // Simulate embedding.length() returning an Integer (like 1536)
        // Create a list with 1536 elements to simulate the actual embedding array
        List<Float> embedding = new ArrayList<>();
        for (int i = 0; i < 1536; i++) {
            embedding.add(0.1f);
        }
        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("embedding", embedding)).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        QueryBuilder incomingQuery = new TermQueryBuilder("text", "foo");
        SearchSourceBuilder source = new SearchSourceBuilder().query(incomingQuery);
        SearchRequest request = new SearchRequest().source(source);

        ActionListener<SearchRequest> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchRequest newSearchRequest) {
                RangeQueryBuilder expectedQuery = new RangeQueryBuilder("embedding_size");
                expectedQuery.to(1536);
                expectedQuery.includeUpper(true);
                assertEquals(expectedQuery, newSearchRequest.source().query());
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException("Failed in executing processRequestAsync: " + e.getMessage(), e);
            }
        };

        requestProcessor.processRequestAsync(request, requestContext, listener);
    }

    /**
     * Tests the successful rewriting of a term query to a geometry query based on the model output
     * and the provided query template, where the model output is a list of coordinates.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testExecute_rewriteListFromTermQueryToGeometryQuerySuccess() throws Exception {
        String queryTemplate = "{\n"
            + "  \"query\": {\n"
            + "  \"geo_shape\" : {\n"
            + "    \"location\" : {\n"
            + "      \"shape\" : {\n"
            + "        \"type\" : \"Envelope\",\n"
            + "        \"coordinates\" : ${modelPredictionOutcome} \n"
            + "      },\n"
            + "      \"relation\" : \"intersects\"\n"
            + "    },\n"
            + "    \"ignore_unmapped\" : false,\n"
            + "    \"boost\" : 42.0\n"
            + "  }\n"
            + "  }\n"
            + "}";

        String expectedNewQueryString = "{\n"
            + "  \"query\": {\n"
            + "  \"geo_shape\" : {\n"
            + "    \"location\" : {\n"
            + "      \"shape\" : {\n"
            + "        \"type\" : \"Envelope\",\n"
            + "        \"coordinates\" : [ [ 0.0, 6.0], [ 4.0, 2.0] ]\n"
            + "      },\n"
            + "      \"relation\" : \"intersects\"\n"
            + "    },\n"
            + "    \"ignore_unmapped\" : false,\n"
            + "    \"boost\" : 42.0\n"
            + "  }\n"
            + "  }\n"
            + "}";

        String modelInputField = "inputs";
        String originalQueryField = "query.term.text.value";
        String newQueryField = "modelPredictionOutcome";
        String modelOutputField = "response";
        MLInferenceSearchRequestProcessor requestProcessor = getMlInferenceSearchRequestProcessor(
            queryTemplate,
            modelInputField,
            originalQueryField,
            newQueryField,
            modelOutputField,
            false,
            false
        );
        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(ImmutableMap.of("response", Arrays.asList(Arrays.asList(0.0, 6.0), Arrays.asList(4.0, 2.0))))
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        QueryBuilder incomingQuery = new TermQueryBuilder("text", "Seattle");
        SearchSourceBuilder source = new SearchSourceBuilder().query(incomingQuery);
        SearchRequest request = new SearchRequest().source(source);
        XContentParser parser = createParser(JsonXContent.jsonXContent, expectedNewQueryString);
        SearchSourceBuilder expectedSearchSourceBuilder = new SearchSourceBuilder();
        expectedSearchSourceBuilder.parseXContent(parser);
        SearchRequest expectedRequest = new SearchRequest().source(expectedSearchSourceBuilder);
        ActionListener<SearchRequest> Listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchRequest newSearchRequest) {
                assertEquals(expectedRequest.source().query(), newSearchRequest.source().query());
                assertEquals(expectedRequest.toString(), newSearchRequest.toString());
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException("Failed in executing processRequestAsync.");
            }
        };

        requestProcessor.processRequestAsync(request, requestContext, Listener);
    }

    /**
     * Tests the scenario where an exception occurs during the model inference process.
     * The test sets up a mock client that simulates a failure during the model execution,
     * and verifies that the appropriate exception is propagated to the listener.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testExecute_InferenceException() {
        String modelInputField = "inputs";
        String originalQueryField = "query.term.text.value";
        String newQueryField = "query.term.text.value";
        String modelOutputField = "response";
        MLInferenceSearchRequestProcessor requestProcessor = getMlInferenceSearchRequestProcessor(
            null,
            modelInputField,
            originalQueryField,
            newQueryField,
            modelOutputField,
            false,
            false
        );

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onFailure(new RuntimeException("Executing Model failed with exception."));
            return null;
        }).when(client).execute(any(), any(), any());

        QueryBuilder incomingQuery = new TermQueryBuilder("text", "foo");
        SearchSourceBuilder source = new SearchSourceBuilder().query(incomingQuery);
        SearchRequest request = new SearchRequest().source(source);

        ActionListener<SearchRequest> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchRequest newSearchRequest) {
                throw new RuntimeException("error handling not properly");
            }

            @Override
            public void onFailure(Exception ex) {
                try {
                    throw ex;
                } catch (Exception e) {
                    assertEquals("Executing Model failed with exception.", e.getMessage());
                }
            }
        };

        requestProcessor.processRequestAsync(request, requestContext, listener);
    }

    /**
     * Tests the scenario where an exception occurs during the model inference process,
     * but the `ignoreFailure` flag is set to true. In this case, the original search
     * request should be returned without any modifications.
     */
    public void testExecute_InferenceExceptionIgnoreFailure() {
        String modelInputField = "inputs";
        String originalQueryField = "query.term.text.value";
        String newQueryField = "query.term.text.value";
        String modelOutputField = "response";
        MLInferenceSearchRequestProcessor requestProcessor = getMlInferenceSearchRequestProcessor(
            null,
            modelInputField,
            originalQueryField,
            newQueryField,
            modelOutputField,
            true,
            false
        );

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onFailure(new RuntimeException("Executing Model failed with exception."));
            return null;

        }).when(client).execute(any(), any(), any());
        QueryBuilder incomingQuery = new TermQueryBuilder("text", "foo");
        SearchSourceBuilder source = new SearchSourceBuilder().query(incomingQuery);
        SearchRequest request = new SearchRequest().source(source);

        ActionListener<SearchRequest> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchRequest newSearchRequest) {
                assertEquals(incomingQuery, newSearchRequest.source().query());
            }

            @Override
            public void onFailure(Exception ex) {
                throw new RuntimeException("error handling not properly");
            }
        };

        requestProcessor.processRequestAsync(request, requestContext, listener);
        assertEquals(requestProcessor.isIgnoreFailure(), true);
    }

    /**
     * Tests the case where the query string is null, and an exception is expected.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testNullQueryStringException() throws Exception {
        String modelInputField = "inputs";
        String originalQueryField = "query.term.text.value";
        String newQueryField = "modelPredictionScore";
        String modelOutputField = "response";
        String queryTemplate = "";

        MLInferenceSearchRequestProcessor requestProcessor = getMlInferenceSearchRequestProcessor(
            queryTemplate,
            modelInputField,
            originalQueryField,
            newQueryField,
            modelOutputField,
            false,
            false
        );
        SearchRequest request = new SearchRequest();

        ActionListener<SearchRequest> Listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchRequest newSearchRequest) {
                throw new RuntimeException("error handling not properly");
            }

            @Override
            public void onFailure(Exception ex) {
                try {
                    throw ex;
                } catch (Exception e) {
                    assertEquals("query body is empty, cannot processor inference on empty query request.", e.getMessage());
                }
            }
        };

        requestProcessor.processRequestAsync(request, requestContext, Listener);
        assertEquals(requestProcessor.isIgnoreFailure(), false);
    }

    /**
     * Tests the case where the query string is null, but the `ignoreFailure` flag is set to true.
     * The original search request should be returned without any modifications.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testNullQueryStringIgnoreFailure() throws Exception {
        String modelInputField = "inputs";
        String originalQueryField = "query.term.text.value";
        String newQueryField = "modelPredictionScore";
        String modelOutputField = "response";
        String queryTemplate = "";

        MLInferenceSearchRequestProcessor requestProcessor = getMlInferenceSearchRequestProcessor(
            queryTemplate,
            modelInputField,
            originalQueryField,
            newQueryField,
            modelOutputField,
            true,
            false
        );
        SearchRequest request = new SearchRequest();
        ActionListener<SearchRequest> Listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchRequest newSearchRequest) {
                assertNull(newSearchRequest.source());
            }

            @Override
            public void onFailure(Exception ex) {
                throw new RuntimeException("Failed in executing processRequestAsync.");
            }
        };

        requestProcessor.processRequestAsync(request, requestContext, Listener);
        assertEquals(requestProcessor.isIgnoreFailure(), true);
    }

    /**
     * Tests the case where the query template contains an invalid query format, and an exception is expected.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testExecute_invalidQueryFormatInQueryTemplateException() throws Exception {
        /**
         * example term query: {"query":{"term":{"text":{"value":"foo","boost":1.0}}}}
         */
        String modelInputField = "inputs";
        String originalQueryField = "query.term.text.value";
        String newQueryField = "modelPredictionScore";
        String modelOutputField = "response";
        // typo in query
        String queryTemplate = "{\"query\":{\"range1\":{\"text\":{\"gte\":${modelPredictionScore}}}}}";

        MLInferenceSearchRequestProcessor requestProcessor = getMlInferenceSearchRequestProcessor(
            queryTemplate,
            modelInputField,
            originalQueryField,
            newQueryField,
            modelOutputField,
            false,
            false
        );

        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", "0.123")).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        QueryBuilder incomingQuery = new TermQueryBuilder("text", "foo");
        SearchSourceBuilder source = new SearchSourceBuilder().query(incomingQuery);
        SearchRequest request = new SearchRequest().source(source);

        /**
         * example input term query: {"query":{"term":{"text":{"value":foo,"boost":1.0}}}}
         */
        /**
         * example output range query: {"query":{"range":{"text":{"gte":"2"}}}}
         */

        ActionListener<SearchRequest> Listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchRequest newSearchRequest) {
                throw new RuntimeException("error handling not properly");
            }

            @Override
            public void onFailure(Exception e) {
                assertEquals("unknown query [range1] did you mean [range]?", e.getMessage());
            }
        };

        requestProcessor.processRequestAsync(request, requestContext, Listener);
        assertEquals(requestProcessor.isIgnoreFailure(), false);
    }

    /**
     * Tests the case where the query field specified in the input mapping is not found in the original query string,
     * and an exception is expected.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testExecute_queryFieldNotFoundInOriginalQueryException() throws Exception {
        String modelInputField = "inputs";
        // test typo in query field name
        String originalQueryField = "query.term.text.value1";
        String newQueryField = "modelPredictionScore";
        String modelOutputField = "response";
        String queryTemplate = "";

        MLInferenceSearchRequestProcessor requestProcessor = getMlInferenceSearchRequestProcessor(
            queryTemplate,
            modelInputField,
            originalQueryField,
            newQueryField,
            modelOutputField,
            false,
            false
        );

        QueryBuilder incomingQuery = new TermQueryBuilder("text", "foo");
        SearchSourceBuilder source = new SearchSourceBuilder().query(incomingQuery);
        SearchRequest request = new SearchRequest().source(source);

        /**
         * example input term query: {"query":{"term":{"text":{"value":"foo","boost":1.0}}}}
         */

        ActionListener<SearchRequest> Listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchRequest newSearchRequest) {
                throw new RuntimeException("error handling not properly");
            }

            @Override
            public void onFailure(Exception ex) {
                assertEquals(
                    "cannot find field: query.term.text.value1 in query string: {\"query\":{\"term\":{\"text\":{\"value\":\"foo\",\"boost\":1.0}}}}",
                    ex.getMessage()
                );
            }
        };
        requestProcessor.processRequestAsync(request, requestContext, Listener);
    }

    /**
     * Tests the case where the query field specified in the input mapping is not found in the original query string,
     * and an exception is expected.
     * when ignorMissing, this processor is ignored return original query
     * @throws Exception if an error occurs during the test
     */
    public void testExecute_queryFieldNotFoundInOriginalQueryExceptionIgnoreMissing() throws Exception {
        String modelInputField = "inputs";
        // test typo in query field name
        String originalQueryField = "query.term.text.value1";
        String newQueryField = "modelPredictionScore";
        String modelOutputField = "response";
        String queryTemplate = "";

        MLInferenceSearchRequestProcessor requestProcessor = getMlInferenceSearchRequestProcessor(
            queryTemplate,
            modelInputField,
            originalQueryField,
            newQueryField,
            modelOutputField,
            false,
            true
        );

        QueryBuilder incomingQuery = new TermQueryBuilder("text", "foo");
        SearchSourceBuilder source = new SearchSourceBuilder().query(incomingQuery);
        SearchRequest request = new SearchRequest().source(source);

        /**
         * example input term query: {"query":{"term":{"text":{"value":"foo","boost":1.0}}}}
         */

        ActionListener<SearchRequest> Listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchRequest newSearchRequest) {
                assertEquals(newSearchRequest.source().query(), incomingQuery);
            }

            @Override
            public void onFailure(Exception ex) {
                throw new RuntimeException("error handling not properly");
            }
        };
        requestProcessor.processRequestAsync(request, requestContext, Listener);
    }

    /**
     * Tests the case where the query field specified in the input mapping is not found in the query template,
     * and an exception is expected.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testExecute_queryFieldNotFoundInQueryTemplateException() throws Exception {
        String modelInputField = "inputs";
        // test typo in query field name
        String originalQueryField = "query.term.text.value1";
        String newQueryField = "modelPredictionScore";
        String modelOutputField = "response";
        String queryTemplate = "{\"query\":{\"range\":{\"text\":{\"gte\":\"${modelPredictionScore}\"}}}}";

        MLInferenceSearchRequestProcessor requestProcessor = getMlInferenceSearchRequestProcessor(
            queryTemplate,
            modelInputField,
            originalQueryField,
            newQueryField,
            modelOutputField,
            false,
            false
        );

        QueryBuilder incomingQuery = new TermQueryBuilder("text", "foo");
        SearchSourceBuilder source = new SearchSourceBuilder().query(incomingQuery);
        SearchRequest request = new SearchRequest().source(source);

        /**
         * example input term query: {"query":{"term":{"text":{"value":"foo","boost":1.0}}}}
         */
        /**
         * example output range query: {"query":{"range":{"text":{"gte":0.123}}}}
         */
        ActionListener<SearchRequest> Listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchRequest newSearchRequest) {
                throw new RuntimeException("error handling not properly");
            }

            @Override
            public void onFailure(Exception ex) {
                assertEquals(
                    "cannot find field: query.term.text.value1 in query string: {\"query\":{\"term\":{\"text\":{\"value\":\"foo\",\"boost\":1.0}}}}",
                    ex.getMessage()
                );
            }
        };
        requestProcessor.processRequestAsync(request, requestContext, Listener);

    }

    /**
     * Tests the case where the query field specified in the input mapping is not found in the query template,
     * but the `ignoreFailure` flag is set to true. The original search request should be returned without any modifications.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testExecute_queryFieldNotFoundInQueryTemplateIgnoreFailure() throws Exception {
        String modelInputField = "inputs";
        // test typo in query field name
        String originalQueryField = "query.term.text.value1";
        String newQueryField = "modelPredictionScore";
        String modelOutputField = "response";
        String queryTemplate = "{\"query\":{\"range\":{\"text\":{\"gte\":\"${modelPredictionScore}\"}}}}";

        MLInferenceSearchRequestProcessor requestProcessor = getMlInferenceSearchRequestProcessor(
            queryTemplate,
            modelInputField,
            originalQueryField,
            newQueryField,
            modelOutputField,
            true,
            false
        );

        QueryBuilder incomingQuery = new TermQueryBuilder("text", "foo");
        SearchSourceBuilder source = new SearchSourceBuilder().query(incomingQuery);
        SearchRequest request = new SearchRequest().source(source);

        /**
         * example input term query: {"query":{"term":{"text":{"value":"foo","boost":1.0}}}}
         */
        ActionListener<SearchRequest> Listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchRequest newSearchRequest) {
                assertEquals(newSearchRequest.source().query(), incomingQuery);
            }

            @Override
            public void onFailure(Exception ex) {
                throw new RuntimeException("error handling not properly");
            }
        };
        requestProcessor.processRequestAsync(request, requestContext, Listener);
        assertEquals(requestProcessor.isIgnoreFailure(), true);
    }

    /**
     * Tests the case where the query field specified in the input mapping is not found in the query template,
     * but the `ignoreMissing` flag is set to true. The original search request should be returned without any modifications.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testExecute_queryFieldNotFoundInQueryTemplateIgnoreMissing() throws Exception {
        String modelInputField = "inputs";
        // test typo in query field name
        String originalQueryField = "query.term.text.value1";
        String newQueryField = "modelPredictionScore";
        String modelOutputField = "response";
        String queryTemplate = "{\"query\":{\"range\":{\"text\":{\"gte\":\"${modelPredictionScore}\"}}}}";

        MLInferenceSearchRequestProcessor requestProcessor = getMlInferenceSearchRequestProcessor(
            queryTemplate,
            modelInputField,
            originalQueryField,
            newQueryField,
            modelOutputField,
            false,
            true
        );

        QueryBuilder incomingQuery = new TermQueryBuilder("text", "foo");
        SearchSourceBuilder source = new SearchSourceBuilder().query(incomingQuery);
        SearchRequest request = new SearchRequest().source(source);

        /**
         * example input term query: {"query":{"term":{"text":{"value":"foo","boost":1.0}}}}
         */
        ActionListener<SearchRequest> Listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchRequest newSearchRequest) {
                assertEquals(newSearchRequest.source().query(), incomingQuery);
            }

            @Override
            public void onFailure(Exception ex) {
                throw new RuntimeException("error handling not properly");
            }
        };
        requestProcessor.processRequestAsync(request, requestContext, Listener);

    }

    /**
     * Tests the successful rewriting of a single string in a term query based on the model output.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testExecute_rewriteTermQueryWriteToExtensionSuccess() throws Exception {

        /**
         * example term query: {"query":{"term":{"text":{"value":"foo","boost":1.0}}}}
         */
        String modelInputField = "inputs";
        String originalQueryField = "query.term.text.value";
        String newQueryField = "ext.ml_inference.llm_response";
        String modelOutputField = "response";
        MLInferenceSearchRequestProcessor requestProcessor = getMlInferenceSearchRequestProcessor(
            null,
            modelInputField,
            originalQueryField,
            newQueryField,
            modelOutputField,
            false,
            false
        );
        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", "eng")).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        QueryBuilder incomingQuery = new TermQueryBuilder("text", "foo");
        SearchSourceBuilder source = new SearchSourceBuilder().query(incomingQuery);
        SearchRequest request = new SearchRequest().source(source);

        Map<String, Object> llmResponse = new HashMap<>();
        llmResponse.put("llm_response", "eng");
        MLInferenceRequestParameters requestParameters = new MLInferenceRequestParameters(llmResponse);
        MLInferenceRequestParametersExtBuilder mlInferenceExtBuilder = new MLInferenceRequestParametersExtBuilder();
        mlInferenceExtBuilder.setRequestParameters(requestParameters);
        SearchSourceBuilder expectedSource = new SearchSourceBuilder().query(incomingQuery).ext(List.of(mlInferenceExtBuilder));
        SearchRequest expectRequest = new SearchRequest().source(expectedSource);

        ActionListener<SearchRequest> Listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchRequest newSearchRequest) {
                assertEquals(incomingQuery, newSearchRequest.source().query());
                assertEquals(expectRequest.source().toString(), newSearchRequest.source().toString());
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException("Failed in executing processRequestAsync." + e.getMessage());
            }
        };

        requestProcessor.processRequestAsync(request, requestContext, Listener);
        verify(requestContext).setAttribute("ext.ml_inference.llm_response", "eng");
    }

    /**
     * Tests the successful rewriting of a single string in a term query based on the model output.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testExecute_rewriteTermQueryReadAndWriteToExtensionSuccess() throws Exception {

        /**
         * example term query: {"query":{"term":{"text":{"value":"foo","boost":1.0}}}}
         */
        String modelInputField = "inputs";
        String originalQueryField = "ext.ml_inference.question";
        String newQueryField = "ext.ml_inference.llm_response";
        String modelOutputField = "response";
        MLInferenceSearchRequestProcessor requestProcessor = getMlInferenceSearchRequestProcessor(
            null,
            modelInputField,
            originalQueryField,
            newQueryField,
            modelOutputField,
            false,
            false
        );
        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", "eng")).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        QueryBuilder incomingQuery = new TermQueryBuilder("text", "foo");

        Map<String, Object> llmQuestion = new HashMap<>();
        llmQuestion.put("question", "what language is this text in?");
        MLInferenceRequestParameters requestParameters = new MLInferenceRequestParameters(llmQuestion);
        MLInferenceRequestParametersExtBuilder mlInferenceExtBuilder = new MLInferenceRequestParametersExtBuilder();
        mlInferenceExtBuilder.setRequestParameters(requestParameters);
        SearchSourceBuilder source = new SearchSourceBuilder().query(incomingQuery).ext(List.of(mlInferenceExtBuilder));

        SearchRequest request = new SearchRequest().source(source);

        // expecting new request with ml inference search extensions
        Map<String, Object> params = new HashMap<>();
        params.put("question", "what language is this text in?");
        params.put("llm_response", "eng");
        MLInferenceRequestParameters expectedRequestParameters = new MLInferenceRequestParameters(params);
        MLInferenceRequestParametersExtBuilder expectedMlInferenceExtBuilder = new MLInferenceRequestParametersExtBuilder();
        expectedMlInferenceExtBuilder.setRequestParameters(expectedRequestParameters);
        SearchSourceBuilder expectedSource = new SearchSourceBuilder().query(incomingQuery).ext(List.of(expectedMlInferenceExtBuilder));
        SearchRequest expectRequest = new SearchRequest().source(expectedSource);

        ActionListener<SearchRequest> Listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchRequest newSearchRequest) {
                assertEquals(incomingQuery, newSearchRequest.source().query());
                assertEquals(expectRequest.toString(), newSearchRequest.toString());
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException("Failed in executing processRequestAsync." + e.getMessage());
            }
        };

        requestProcessor.processRequestAsync(request, requestContext, Listener);

    }

    /**
     * Tests the successful writing a new field in requestContext based on the model output.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testExecute_rewriteTermQueryReadWriteToRequestContextSuccess() throws Exception {

        /**
         * example term query: {"query":{"term":{"text":{"value":"foo","boost":1.0}}}}
         */
        String modelInputField = "inputs";
        String originalQueryField = "ext.ml_inference.question";
        String newQueryField = "llm_response";
        String modelOutputField = "response";
        MLInferenceSearchRequestProcessor requestProcessor = getMlInferenceSearchRequestProcessor(
            null,
            modelInputField,
            originalQueryField,
            newQueryField,
            modelOutputField,
            false,
            false
        );
        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", "eng")).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        QueryBuilder incomingQuery = new TermQueryBuilder("text", "foo");

        Map<String, Object> llmQuestion = new HashMap<>();
        llmQuestion.put("question", "what language is this text in?");
        MLInferenceRequestParameters requestParameters = new MLInferenceRequestParameters(llmQuestion);
        MLInferenceRequestParametersExtBuilder mlInferenceExtBuilder = new MLInferenceRequestParametersExtBuilder();
        mlInferenceExtBuilder.setRequestParameters(requestParameters);
        SearchSourceBuilder source = new SearchSourceBuilder().query(incomingQuery).ext(List.of(mlInferenceExtBuilder));

        SearchRequest request = new SearchRequest().source(source);

        // expecting new request with ml inference search extensions
        Map<String, Object> params = new HashMap<>();
        params.put("question", "what language is this text in?");
        MLInferenceRequestParameters expectedRequestParameters = new MLInferenceRequestParameters(params);
        MLInferenceRequestParametersExtBuilder expectedMlInferenceExtBuilder = new MLInferenceRequestParametersExtBuilder();
        expectedMlInferenceExtBuilder.setRequestParameters(expectedRequestParameters);
        SearchSourceBuilder expectedSource = new SearchSourceBuilder().query(incomingQuery).ext(List.of(expectedMlInferenceExtBuilder));
        SearchRequest expectRequest = new SearchRequest().source(expectedSource);

        ActionListener<SearchRequest> Listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchRequest newSearchRequest) {
                assertEquals(incomingQuery, newSearchRequest.source().query());
                assertEquals(expectRequest.toString(), newSearchRequest.toString());
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException("Failed in executing processRequestAsync." + e.getMessage());
            }
        };

        requestProcessor.processRequestAsync(request, requestContext, Listener);

        verify(requestContext).setAttribute("llm_response", "eng");
    }

    /**
     * Tests the successful rewriting of a complex nested array in query extension based on the model output.
     * verify the pipelineContext is set from the extension
     * @throws Exception if an error occurs during the test
     */
    public void testExecute_rewriteTermQueryReadAndWriteComplexNestedArrayToExtensionSuccess() throws Exception {
        String modelInputField = "inputs";
        String originalQueryField = "ext.ml_inference.question";
        String newQueryField = "ext.ml_inference.llm_response";
        String modelOutputField = "response";
        MLInferenceSearchRequestProcessor requestProcessor = getMlInferenceSearchRequestProcessor(
            null,
            modelInputField,
            originalQueryField,
            newQueryField,
            modelOutputField,
            false,
            false
        );

        // Test model return a complex nested array
        Map<String, Object> nestedResponse = new HashMap<>();
        List<Map<String, String>> languageList = new ArrayList<>();
        languageList.add(Collections.singletonMap("eng", "0.95"));
        languageList.add(Collections.singletonMap("es", "0.67"));
        nestedResponse.put("language", languageList);
        nestedResponse.put("type", "bert");

        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", nestedResponse)).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        QueryBuilder incomingQuery = new TermQueryBuilder("text", "foo");

        Map<String, Object> llmQuestion = new HashMap<>();
        llmQuestion.put("question", "what language is this text in?");
        MLInferenceRequestParameters requestParameters = new MLInferenceRequestParameters(llmQuestion);
        MLInferenceRequestParametersExtBuilder mlInferenceExtBuilder = new MLInferenceRequestParametersExtBuilder();
        mlInferenceExtBuilder.setRequestParameters(requestParameters);
        SearchSourceBuilder source = new SearchSourceBuilder().query(incomingQuery).ext(List.of(mlInferenceExtBuilder));

        SearchRequest request = new SearchRequest().source(source);

        // Expecting new request with ml inference search extensions including the complex nested array
        Map<String, Object> params = new HashMap<>();
        params.put("question", "what language is this text in?");
        params.put("llm_response", nestedResponse);
        MLInferenceRequestParameters expectedRequestParameters = new MLInferenceRequestParameters(params);
        MLInferenceRequestParametersExtBuilder expectedMlInferenceExtBuilder = new MLInferenceRequestParametersExtBuilder();
        expectedMlInferenceExtBuilder.setRequestParameters(expectedRequestParameters);
        SearchSourceBuilder expectedSource = new SearchSourceBuilder().query(incomingQuery).ext(List.of(expectedMlInferenceExtBuilder));
        SearchRequest expectRequest = new SearchRequest().source(expectedSource);

        ActionListener<SearchRequest> Listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchRequest newSearchRequest) {
                assertEquals(incomingQuery, newSearchRequest.source().query());
                assertEquals(expectRequest.toString(), newSearchRequest.toString());

                // Additional checks for the complex nested array
                MLInferenceRequestParametersExtBuilder actualExtBuilder = (MLInferenceRequestParametersExtBuilder) newSearchRequest
                    .source()
                    .ext()
                    .get(0);
                MLInferenceRequestParameters actualParams = actualExtBuilder.getRequestParameters();
                Object actualResponse = actualParams.getParams().get("llm_response");

                assertTrue(actualResponse instanceof Map);
                Map<?, ?> actualNestedResponse = (Map<?, ?>) actualResponse;

                // Check the "language" field
                assertTrue(actualNestedResponse.get("language") instanceof List);
                List<?> actualLanguageList = (List<?>) actualNestedResponse.get("language");
                assertEquals(2, actualLanguageList.size());

                Map<?, ?> engMap = (Map<?, ?>) actualLanguageList.get(0);
                assertEquals("0.95", engMap.get("eng"));

                Map<?, ?> esMap = (Map<?, ?>) actualLanguageList.get(1);
                assertEquals("0.67", esMap.get("es"));

                // Check the "type" field
                assertEquals("bert", actualNestedResponse.get("type"));
                verify(requestContext).setAttribute("ext.ml_inference.question", "what language is this text in?");
                verify(requestContext).setAttribute("ext.ml_inference.llm_response", nestedResponse);

            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException("Failed in executing processRequestAsync." + e.getMessage());
            }
        };

        requestProcessor.processRequestAsync(request, requestContext, Listener);
    }

    /**
     * Tests when there are two optional input fields
     * but only the first optional input is present in the query
     * the successful rewriting of a single string in a term query based on the model output.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testExecute_rewriteTermQueryWithFirstOptionalMappingSuccess() throws Exception {

        /**
         * example term query: {"query":{"term":{"text":{"value":"foo","boost":1.0}}}}
         */

        String modelInputField = "inputs";
        String originalQueryField = "query.term.text.value";
        String newQueryField = "query.term.text.value";

        String originalQueryField2 = "query.term.title.value";
        String newQueryField2 = "query.term.title.value";

        String modelOutputField = "response";

        List<Map<String, String>> optionalInputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put(modelInputField, originalQueryField);
        input.put(originalQueryField2, newQueryField2);
        optionalInputMap.add(input);

        List<Map<String, String>> optionalOutputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newQueryField, modelOutputField);
        output.put(newQueryField2, modelOutputField);
        optionalOutputMap.add(output);

        MLInferenceSearchRequestProcessor requestProcessor = new MLInferenceSearchRequestProcessor(
            "model1",
            null,
            null,
            null,
            optionalInputMap,
            optionalOutputMap,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY
        );

        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", "eng")).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        QueryBuilder incomingQuery = new TermQueryBuilder("text", "foo");
        SearchSourceBuilder source = new SearchSourceBuilder().query(incomingQuery);
        SearchRequest request = new SearchRequest().source(source);
        QueryBuilder expectedQuery = new TermQueryBuilder("text", "eng");

        ActionListener<SearchRequest> Listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchRequest newSearchRequest) {
                assertEquals(expectedQuery, newSearchRequest.source().query());
                assertEquals(request.toString(), newSearchRequest.toString());
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException("Failed in executing processRequestAsync." + e.getMessage());
            }
        };

        requestProcessor.processRequestAsync(request, requestContext, Listener);

    }

    /**
     * Tests ML Processor can return a sparse vector correctly when performing a rewrite query.
     *
     * This simulates a real world scenario where user has a neural sparse model and attempts to parse
     * it by asserting FullResponsePath to true.
     * @throws Exception when an error occurs on the test
     */
    public void testExecute_rewriteTermQueryWithSparseVectorSuccess() throws Exception {
        String modelInputField = "inputs";
        String originalQueryField = "query.term.text.value";
        String newQueryField = "vector";
        String modelInferenceJsonPathInput = "$.inference_results[0].output[0].dataAsMap.response[0]";

        String queryTemplate = "{\n"
            + "  \"query\": {\n"
            + "    \"script_score\": {\n"
            + "      \"query\": {\n"
            + "        \"match_all\": {}\n"
            + "      },\n"
            + "      \"script\": {\n"
            + "        \"source\": \"return 1;\",\n"
            + "        \"params\": {\n"
            + "          \"query_tokens\": ${vector}\n"
            + "        }\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}";

        Map<String, Double> sparseVector = Map.of("this", 1.3123, "which", 0.2447, "here", 0.6674);

        List<Map<String, String>> optionalInputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put(modelInputField, originalQueryField);
        optionalInputMap.add(input);

        List<Map<String, String>> optionalOutputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newQueryField, modelInferenceJsonPathInput);
        optionalOutputMap.add(output);

        MLInferenceSearchRequestProcessor requestProcessor = new MLInferenceSearchRequestProcessor(
            "model1",
            queryTemplate,
            null,
            null,
            optionalInputMap,
            optionalOutputMap,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            true,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY
        );

        /**
         * {
         *   "inference_results" : [ {
         *     "output" : [ {
         *       "name" : "response",
         *       "dataAsMap" : {
         *         "response" : [ {
         *           "this" : 1.3123,
         *           "which" : 0.2447,
         *           "here" : 0.6674
         *         } ]
         *       }
         *     } ]
         *   } ]
         * }
         */
        ModelTensor modelTensor = ModelTensor.builder().name("response").dataAsMap(Map.of("response", List.of(sparseVector))).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        QueryBuilder incomingQuery = new TermQueryBuilder("text", "foo");
        SearchSourceBuilder source = new SearchSourceBuilder().query(incomingQuery);
        SearchRequest request = new SearchRequest().source(source);

        ActionListener<SearchRequest> Listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchRequest newSearchRequest) {
                Script script = new Script(ScriptType.INLINE, "painless", "return 1;", Map.of("query_tokens", sparseVector));

                ScriptScoreQueryBuilder expectedQuery = new ScriptScoreQueryBuilder(QueryBuilders.matchAllQuery(), script);
                assertEquals(expectedQuery, newSearchRequest.source().query());
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException("Failed in executing processRequestAsync.", e);
            }
        };

        requestProcessor.processRequestAsync(request, requestContext, Listener);
    }

    /**
     * Tests ML Processor can return a OpenSearch Query correctly when performing a rewrite query.
     *
     * This simulates a real world scenario where user has a llm return a OpenSearch Query to help them generate a new
     * query based on the context given in the prompt.
     *
     * @throws Exception when an error occurs on the test
     */
    public void testExecute_rewriteTermQueryWithNewQuerySuccess() throws Exception {
        String modelInputField = "inputs";
        String originalQueryField = "query.term.text.value";
        String newQueryField = "llm_query";
        String modelInferenceJsonPathInput = "$.inference_results[0].output[0].dataAsMap.content[0].text";

        String queryTemplate = "${llm_query}";

        String llmQuery = "{\n" + "  \"query\": {\n" + "    \"match_all\": {}\n" + "  }\n" + "}";
        Map content = Map.of("content", List.of(Map.of("text", llmQuery)));

        List<Map<String, String>> optionalInputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put(modelInputField, originalQueryField);
        optionalInputMap.add(input);

        List<Map<String, String>> optionalOutputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newQueryField, modelInferenceJsonPathInput);
        optionalOutputMap.add(output);

        MLInferenceSearchRequestProcessor requestProcessor = new MLInferenceSearchRequestProcessor(
            "model1",
            queryTemplate,
            null,
            null,
            optionalInputMap,
            optionalOutputMap,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            true,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY
        );

        /*
         * {
         *   "inference_results" : [ {
         *     "output" : [ {
         *       "name" : "response",
         *       "dataAsMap" : {
         *        "content": [
         *           "text": "{\"query\": \"match_all\" : {}}"
         *       }
         *     } ]
         *   } ]
         * }
         */
        ModelTensor modelTensor = ModelTensor.builder().name("response").dataAsMap(content).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        QueryBuilder incomingQuery = new TermQueryBuilder("text", "foo");
        SearchSourceBuilder source = new SearchSourceBuilder().query(incomingQuery);
        SearchRequest sampleRequest = new SearchRequest().source(source);

        ActionListener<SearchRequest> Listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchRequest newSearchRequest) {
                MatchAllQueryBuilder expectedQuery = new MatchAllQueryBuilder();
                assertEquals(expectedQuery, newSearchRequest.source().query());
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException("Failed in executing processRequestAsync.", e);
            }
        };

        requestProcessor.processRequestAsync(sampleRequest, requestContext, Listener);
    }

    /**
     * Tests when there are two optional input fields
     * but only the second optional input is present in the query
     * the successful rewriting of a single string in a term query based on the model output.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testExecute_rewriteTermQueryWithSecondOptionalMappingSuccess() throws Exception {

        /**
         * example term query: {"query":{"term":{"text":{"value":"foo","boost":1.0}}}}
         */

        String modelInputField = "inputs";
        String modelInputField2 = "optional_inputs";
        String originalQueryField = "query.term.text.value";
        String newQueryField = "query.term.text.value";

        String originalQueryField2 = "query.term.title.value";
        String newQueryField2 = "query.term.title.value";

        String modelOutputField = "response";

        List<Map<String, String>> optionalInputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put(modelInputField, originalQueryField2);
        input.put(modelInputField2, originalQueryField);
        optionalInputMap.add(input);

        List<Map<String, String>> optionalOutputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newQueryField, modelOutputField);
        output.put(newQueryField2, modelOutputField);
        optionalOutputMap.add(output);

        MLInferenceSearchRequestProcessor requestProcessor = new MLInferenceSearchRequestProcessor(
            "model1",
            null,
            null,
            null,
            optionalInputMap,
            optionalOutputMap,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY
        );

        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", "eng")).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        QueryBuilder incomingQuery = new TermQueryBuilder("title", "foo");
        SearchSourceBuilder source = new SearchSourceBuilder().query(incomingQuery);
        SearchRequest request = new SearchRequest().source(source);
        QueryBuilder expectedQuery = new TermQueryBuilder("title", "eng");

        ActionListener<SearchRequest> Listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchRequest newSearchRequest) {
                assertEquals(expectedQuery, newSearchRequest.source().query());
                assertEquals(request.toString(), newSearchRequest.toString());
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException("Failed in executing processRequestAsync." + e.getMessage());
            }
        };
        ArgumentCaptor<MLPredictionTaskRequest> argCaptor = ArgumentCaptor.forClass(MLPredictionTaskRequest.class);

        requestProcessor.processRequestAsync(request, requestContext, Listener);

        verify(client, times(1)).execute(eq(MLPredictionTaskAction.INSTANCE), argCaptor.capture(), any());
        MLPredictionTaskRequest req = argCaptor.getValue();
        MLInput mlInput = req.getMlInput();
        RemoteInferenceInputDataSet inputDataSet = (RemoteInferenceInputDataSet) mlInput.getInputDataset();
        assertEquals(toJson(inputDataSet.getParameters()), "{\"inputs\":\"foo\"}");

    }

    /**
     * Tests the successful rewriting of a term query to a range query based on the model output
     * and the provided query template.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testExecute_rewriteOptionMappingFromTermQueryToRangeQuerySuccess() throws Exception {
        /**
         * example term query: {"query":{"term":{"text":{"value":"foo","boost":1.0}}}}
         */
        String modelInputField = "inputs";
        String originalQueryField = "query.term.text.value";
        String originalQueryField2 = "query.term.title.value";
        String newQueryField = "modelPredictionScore";
        String newQueryField2 = "modelPredictionScore";
        String modelOutputField = "response";
        String queryTemplate = "{\"query\":{\"range\":{\"text\":{\"gte\":${modelPredictionScore}}}}}";

        List<Map<String, String>> optionalInputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put(modelInputField, originalQueryField);
        input.put(originalQueryField2, newQueryField2);
        optionalInputMap.add(input);

        List<Map<String, String>> optionalOutputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newQueryField, modelOutputField);
        output.put(newQueryField2, modelOutputField);
        optionalOutputMap.add(output);

        MLInferenceSearchRequestProcessor requestProcessor = new MLInferenceSearchRequestProcessor(
            "model1",
            queryTemplate,
            null,
            null,
            optionalInputMap,
            optionalOutputMap,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY
        );

        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", "0.123")).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        QueryBuilder incomingQuery = new TermQueryBuilder("text", "foo");
        SearchSourceBuilder source = new SearchSourceBuilder().query(incomingQuery);
        SearchRequest request = new SearchRequest().source(source);

        /**
         * example input term query: {"query":{"term":{"text":{"value":foo,"boost":1.0}}}}
         */
        /**
         * example output range query: {"query":{"range":{"text":{"gte":"2"}}}}
         */

        ActionListener<SearchRequest> Listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchRequest newSearchRequest) {
                RangeQueryBuilder expectedQuery = new RangeQueryBuilder("text");
                expectedQuery.from(0.123);
                expectedQuery.includeLower(true);
                assertEquals(expectedQuery, newSearchRequest.source().query());
                assertEquals(request.toString(), newSearchRequest.toString());
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException("Failed in executing processRequestAsync.");
            }
        };

        ArgumentCaptor<MLPredictionTaskRequest> argCaptor = ArgumentCaptor.forClass(MLPredictionTaskRequest.class);

        requestProcessor.processRequestAsync(request, requestContext, Listener);

        verify(client, times(1)).execute(eq(MLPredictionTaskAction.INSTANCE), argCaptor.capture(), any());
        MLPredictionTaskRequest req = argCaptor.getValue();
        MLInput mlInput = req.getMlInput();
        RemoteInferenceInputDataSet inputDataSet = (RemoteInferenceInputDataSet) mlInput.getInputDataset();
        assertEquals(toJson(inputDataSet.getParameters()), "{\"inputs\":\"foo\"}");

    }

    /**
     * Tests when there are two optional output fields
     * but the model output has a typo on the field name, ignoreMissing is false.
     * expect exception to be thrown.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testExecute_optionalMappingWithWrongModelOutputMapping() throws Exception {

        /**
         * example term query: {"query":{"term":{"text":{"value":"foo","boost":1.0}}}}
         */

        String modelInputField = "inputs";
        String originalQueryField = "query.term.text.value";
        String newQueryField = "query.term.text.value";

        String originalQueryField2 = "query.term.title.value";
        String newQueryField2 = "query.term.title.value";

        String modelOutputField = "response1";

        List<Map<String, String>> optionalInputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put(modelInputField, originalQueryField);
        input.put(originalQueryField2, newQueryField2);
        optionalInputMap.add(input);

        List<Map<String, String>> optionalOutputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newQueryField, modelOutputField);
        output.put(newQueryField2, modelOutputField);
        optionalOutputMap.add(output);

        MLInferenceSearchRequestProcessor requestProcessor = new MLInferenceSearchRequestProcessor(
            "model1",
            null,
            null,
            null,
            optionalInputMap,
            optionalOutputMap,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY
        );

        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", "eng")).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        QueryBuilder incomingQuery = new TermQueryBuilder("text", "foo");
        SearchSourceBuilder source = new SearchSourceBuilder().query(incomingQuery);
        SearchRequest request = new SearchRequest().source(source);

        ActionListener<SearchRequest> Listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchRequest newSearchRequest) {
                throw new RuntimeException("error handling not properly.");
            }

            @Override
            public void onFailure(Exception e) {
                String expectedErrorMessage = "model inference output cannot find field name: response1";
                assertTrue(e.getMessage().contains(expectedErrorMessage));
            }
        };

        requestProcessor.processRequestAsync(request, requestContext, Listener);

    }

    /**
     * Tests when there are two optional output fields
     * but the model output has a typo on the field name, ignoreMissing is true.
     * instead of returning the mapped model output, it will try to rewrite with the entire model output.
     * expect the exception throwing to advise query cannot accept the entire model output.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testExecute_optionalMappingWithWrongModelOutputMappingIgnoreMissing() throws Exception {

        /**
         * example term query: {"query":{"term":{"text":{"value":"foo","boost":1.0}}}}
         */

        String modelInputField = "inputs";
        String originalQueryField = "query.term.text.value";
        String newQueryField = "query.term.text.value";

        String originalQueryField2 = "query.term.title.value";
        String newQueryField2 = "query.term.title.value";

        String modelOutputField = "response1";

        List<Map<String, String>> optionalInputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put(modelInputField, originalQueryField);
        input.put(originalQueryField2, newQueryField2);
        optionalInputMap.add(input);

        List<Map<String, String>> optionalOutputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newQueryField, modelOutputField);
        output.put(newQueryField2, modelOutputField);
        optionalOutputMap.add(output);

        MLInferenceSearchRequestProcessor requestProcessor = new MLInferenceSearchRequestProcessor(
            "model1",
            null,
            null,
            null,
            optionalInputMap,
            optionalOutputMap,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            true,
            "remote",
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY
        );

        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", "eng")).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        QueryBuilder incomingQuery = new TermQueryBuilder("text", "foo");
        SearchSourceBuilder source = new SearchSourceBuilder().query(incomingQuery);
        SearchRequest request = new SearchRequest().source(source);
        QueryBuilder expectedQuery = new TermQueryBuilder("text", "eng");

        ActionListener<SearchRequest> Listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchRequest newSearchRequest) {
                throw new RuntimeException("error handling not properly.");
            }

            @Override
            public void onFailure(Exception e) {
                assertTrue(e.getMessage().contains("[term] query does not support [response]"));
            }
        };

        requestProcessor.processRequestAsync(request, requestContext, Listener);

    }

    /**
     * Tests when there are two optional output fields
     * but the model output has a typo on the field name, ignoreMissing is true and ignoreFailure is true.
     * instead of returning the mapped model output, it will try to rewrite with the entire model output.
     * expect the exception throwing to advise query cannot accept the entire model output.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testExecute_optionalMappingWithWrongModelOutputMappingIgnoreMissingIgnoreFailure() throws Exception {

        /**
         * example term query: {"query":{"term":{"text":{"value":"foo","boost":1.0}}}}
         */

        String modelInputField = "inputs";
        String originalQueryField = "query.term.text.value";
        String newQueryField = "query.term.text.value";

        String originalQueryField2 = "query.term.title.value";
        String newQueryField2 = "query.term.title.value";

        String modelOutputField = "response1";

        List<Map<String, String>> optionalInputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put(modelInputField, originalQueryField);
        input.put(originalQueryField2, newQueryField2);
        optionalInputMap.add(input);

        List<Map<String, String>> optionalOutputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newQueryField, modelOutputField);
        output.put(newQueryField2, modelOutputField);
        optionalOutputMap.add(output);

        MLInferenceSearchRequestProcessor requestProcessor = new MLInferenceSearchRequestProcessor(
            "model1",
            null,
            null,
            null,
            optionalInputMap,
            optionalOutputMap,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            true,
            "remote",
            false,
            true,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY
        );

        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", "eng")).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        QueryBuilder incomingQuery = new TermQueryBuilder("text", "foo");
        SearchSourceBuilder source = new SearchSourceBuilder().query(incomingQuery);
        SearchRequest request = new SearchRequest().source(source);

        ActionListener<SearchRequest> Listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchRequest newSearchRequest) {
                assertEquals(incomingQuery, newSearchRequest.source().query());
                assertEquals(request.toString(), newSearchRequest.toString());
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException("error handling not properly.");
            }
        };

        requestProcessor.processRequestAsync(request, requestContext, Listener);
    }

    /**
     * Helper method to create an instance of the MLInferenceSearchRequestProcessor with the specified parameters.
     *
     * @param queryTemplate     the query template
     * @param modelInputField   the model input field name
     * @param originalQueryField the original query field name
     * @param newQueryField     the new query field name
     * @param modelOutputField  the model output field name
     * @param ignoreFailure     the flag indicating whether to ignore failures or not
     * @param ignoreMissing     the flag indicating whether to ignore missing fields or not
     * @return an instance of the MLInferenceSearchRequestProcessor
     */
    private MLInferenceSearchRequestProcessor getMlInferenceSearchRequestProcessor(
        String queryTemplate,
        String modelInputField,
        String originalQueryField,
        String newQueryField,
        String modelOutputField,
        boolean ignoreFailure,
        boolean ignoreMissing
    ) {
        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put(modelInputField, originalQueryField);
        inputMap.add(input);

        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newQueryField, modelOutputField);
        outputMap.add(output);

        MLInferenceSearchRequestProcessor requestProcessor = new MLInferenceSearchRequestProcessor(
            "model1",
            queryTemplate,
            inputMap,
            outputMap,
            null,
            null,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            ignoreMissing,
            "remote",
            false,
            ignoreFailure,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY
        );
        return requestProcessor;
    }

    /**
     * Tests the creation of the MLInferenceSearchRequestProcessor with required fields.
     *
     * @throws Exception if an error occurs during the test
     */
    private MLInferenceSearchRequestProcessor.Factory factory;

    @Mock
    private NamedXContentRegistry xContentRegistry;

    @Before
    public void init() {
        factory = new MLInferenceSearchRequestProcessor.Factory(client, xContentRegistry);
    }

    /**
     * Tests the creation of the MLInferenceSearchRequestProcessor with required fields.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testCreateRequiredFields() throws Exception {
        Map<String, Object> config = new HashMap<>();
        config.put(MODEL_ID, "model1");
        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put("text_docs", "text");
        inputMap.add(input);
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put("text_embedding", "$.inference_results[0].output[0].data");
        outputMap.add(output);
        config.put(INPUT_MAP, inputMap);
        config.put(OUTPUT_MAP, outputMap);
        String processorTag = randomAlphaOfLength(10);
        MLInferenceSearchRequestProcessor MLInferenceSearchRequestProcessor = factory
            .create(Collections.emptyMap(), processorTag, null, false, config, null);
        assertNotNull(MLInferenceSearchRequestProcessor);
        assertEquals(MLInferenceSearchRequestProcessor.getTag(), processorTag);
        assertEquals(MLInferenceSearchRequestProcessor.getType(), MLInferenceSearchRequestProcessor.TYPE);
        assertEquals(MLInferenceSearchRequestProcessor.isIgnoreFailure(), false);
    }

    /**
     * Tests the creation of the MLInferenceSearchRequestProcessor for a local model.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testCreateLocalModelProcessor() throws Exception {
        Map<String, Processor.Factory> registry = new HashMap<>();
        Map<String, Object> config = new HashMap<>();
        config.put(MODEL_ID, "model1");
        config.put(FUNCTION_NAME, "text_embedding");
        config.put(FULL_RESPONSE_PATH, true);
        config.put(MODEL_INPUT, "{ \"text_docs\": ${ml_inference.text_docs} }");
        Map<String, Object> model_config = new HashMap<>();
        model_config.put("return_number", true);
        config.put(MODEL_CONFIG, model_config);
        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put("text_docs", "text");
        inputMap.add(input);
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put("text_embedding", "$.inference_results[0].output[0].data");
        outputMap.add(output);
        config.put(INPUT_MAP, inputMap);
        config.put(OUTPUT_MAP, outputMap);
        config.put(MAX_PREDICTION_TASKS, 5);
        String processorTag = randomAlphaOfLength(10);
        MLInferenceSearchRequestProcessor MLInferenceSearchRequestProcessor = factory
            .create(Collections.emptyMap(), processorTag, null, false, config, null);
        assertNotNull(MLInferenceSearchRequestProcessor);
        assertEquals(MLInferenceSearchRequestProcessor.getTag(), processorTag);
        assertEquals(MLInferenceSearchRequestProcessor.getType(), MLInferenceSearchRequestProcessor.TYPE);
        assertEquals(MLInferenceSearchRequestProcessor.isIgnoreFailure(), false);
    }

    /**
     * The model input field is required for using a local model
     * when missing the model input field, expected to throw Exceptions
     *
     * @throws Exception if an error occurs during the test
     */
    public void testCreateLocalModelProcessorMissingModelInputField() throws Exception {
        Map<String, Processor.Factory> registry = new HashMap<>();
        Map<String, Object> config = new HashMap<>();
        config.put(MODEL_ID, "model1");
        config.put(FUNCTION_NAME, "text_embedding");
        config.put(FULL_RESPONSE_PATH, true);
        Map<String, Object> model_config = new HashMap<>();
        model_config.put("return_number", true);
        config.put(MODEL_CONFIG, model_config);
        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put("text_docs", "text");
        inputMap.add(input);
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put("text_embedding", "$.inference_results[0].output[0].data");
        outputMap.add(output);
        config.put(INPUT_MAP, inputMap);
        config.put(OUTPUT_MAP, outputMap);
        config.put(MAX_PREDICTION_TASKS, 5);
        String processorTag = randomAlphaOfLength(10);
        try {
            MLInferenceSearchRequestProcessor MLInferenceSearchRequestProcessor = factory
                .create(Collections.emptyMap(), processorTag, null, false, config, null);
            assertNotNull(MLInferenceSearchRequestProcessor);
        } catch (Exception e) {
            assertEquals(e.getMessage(), "Please provide model input when using a local model in ML Inference Processor");
        }
    }

    /**
     * Tests the case where the `input_map` field is missing in the configuration, and an exception is expected.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testCreateNoFieldPresent() throws Exception {
        Map<String, Object> config = new HashMap<>();
        try {
            factory.create(Collections.emptyMap(), "no field", null, false, config, null);
            fail("factory create should have failed");
        } catch (OpenSearchParseException e) {
            assertEquals(e.getMessage(), ("[model_id] required property is missing"));
        }
    }

    /**
     * Tests the case where the `input_map` field is missing in the configuration, and an exception is expected.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testMissingInputMapFields() throws Exception {
        Map<String, Object> config = new HashMap<>();
        config.put(MODEL_ID, "model1");
        String processorTag = randomAlphaOfLength(10);
        try {
            MLInferenceSearchRequestProcessor MLInferenceSearchRequestProcessor = factory
                .create(Collections.emptyMap(), processorTag, null, false, config, null);
            fail("factory create should have failed");
        } catch (IllegalArgumentException e) {
            assertEquals(
                e.getMessage(),
                ("Please provide at least one non-empty input_map or optional_input_map for ML Inference Search Request Processor")
            );
        }
    }

    /**
     * Tests the case where the `output_map` field is missing in the configuration, and an exception is expected.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testMissingOutputMapFields() throws Exception {
        Map<String, Object> config = new HashMap<>();
        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put("text_docs", "text");
        inputMap.add(input);
        config.put(MODEL_ID, "model1");
        String processorTag = randomAlphaOfLength(10);
        config.put(INPUT_MAP, inputMap);
        try {
            MLInferenceSearchRequestProcessor MLInferenceSearchRequestProcessor = factory
                .create(Collections.emptyMap(), processorTag, null, false, config, null);
            fail("factory create should have failed");
        } catch (IllegalArgumentException e) {
            assertEquals(
                e.getMessage(),
                ("Please provide at least one non-empty output_map or optional_output_map for ML Inference Search Request Processor")
            );
        }
    }

    /**
     * Tests the case where the number of prediction tasks exceeds the maximum allowed value, and an exception is expected.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testExceedMaxPredictionTasks() throws Exception {
        Map<String, Object> config = new HashMap<>();
        config.put(MODEL_ID, "model2");
        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input0 = new HashMap<>();
        input0.put("inputs", "text");
        inputMap.add(input0);
        Map<String, String> input1 = new HashMap<>();
        input1.put("inputs", "hashtag");
        inputMap.add(input1);
        Map<String, String> input2 = new HashMap<>();
        input2.put("inputs", "timestamp");
        inputMap.add(input2);
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put("text_embedding", "$.inference_results[0].output[0].data");
        outputMap.add(output);
        config.put(INPUT_MAP, inputMap);
        config.put(OUTPUT_MAP, outputMap);
        config.put(MAX_PREDICTION_TASKS, 2);
        String processorTag = randomAlphaOfLength(10);

        try {
            factory.create(Collections.emptyMap(), processorTag, null, false, config, null);
        } catch (IllegalArgumentException e) {
            assertEquals(
                e.getMessage(),
                ("The number of prediction task setting in this process is 3. It exceeds the max_prediction_tasks of 2. Please reduce the size of input_map or optional_input_map or increase max_prediction_tasks.")
            );
        }
    }

    /**
     * Tests the case where the length of the `output_map` list is greater than the length of the `input_map` list,
     * and an exception is expected.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testOutputMapsExceedInputMaps() throws Exception {
        Map<String, Processor.Factory> registry = new HashMap<>();
        Map<String, Object> config = new HashMap<>();
        config.put(MODEL_ID, "model2");
        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input0 = new HashMap<>();
        input0.put("inputs", "text");
        inputMap.add(input0);
        Map<String, String> input1 = new HashMap<>();
        input1.put("inputs", "hashtag");
        inputMap.add(input1);
        config.put(INPUT_MAP, inputMap);
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output1 = new HashMap<>();
        output1.put("text_embedding", "response");
        outputMap.add(output1);
        Map<String, String> output2 = new HashMap<>();
        output2.put("hashtag_embedding", "response");
        outputMap.add(output2);
        Map<String, String> output3 = new HashMap<>();
        output2.put("hashtvg_embedding", "response");
        outputMap.add(output3);
        config.put(OUTPUT_MAP, outputMap);
        config.put(MAX_PREDICTION_TASKS, 2);
        String processorTag = randomAlphaOfLength(10);

        try {
            factory.create(Collections.emptyMap(), processorTag, null, false, config, null);
        } catch (IllegalArgumentException e) {
            assertEquals(
                e.getMessage(),
                ("when output_maps/optional_output_maps and input_maps/optional_input_maps are provided, their length needs to match. The input is in length of 2, while output_maps is in the length of 3. Please adjust mappings.")
            );
        }
    }

    /**
     * Tests the creation of the MLInferenceSearchRequestProcessor with optional fields.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testCreateOptionalFields() throws Exception {
        Map<String, Object> config = new HashMap<>();
        config.put(MODEL_ID, "model2");
        Map<String, Object> model_config = new HashMap<>();
        model_config.put("hidden_size", 768);
        model_config.put("gradient_checkpointing", false);
        model_config.put("position_embedding_type", "absolute");
        config.put(MODEL_CONFIG, model_config);
        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put("inputs", "text");
        inputMap.add(input);
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put("text_embedding", "response");
        outputMap.add(output);
        List<Map<String, String>> optionalInputMap = new ArrayList<>();
        Map<String, String> optionalInput = new HashMap<>();
        optionalInput.put("optionalInputs", "text_size");
        optionalInputMap.add(optionalInput);
        List<Map<String, String>> optionalOutputMap = new ArrayList<>();
        Map<String, String> optionalOutput = new HashMap<>();
        optionalOutput.put("metadata", "response_details");
        optionalOutputMap.add(optionalOutput);
        config.put(INPUT_MAP, inputMap);
        config.put(OUTPUT_MAP, outputMap);
        config.put(MAX_PREDICTION_TASKS, 5);
        config.put(OPTIONAL_INPUT_MAP, optionalInputMap);
        config.put(OPTIONAL_OUTPUT_MAP, optionalOutputMap);
        config.put(INPUT_MAP, inputMap);
        config.put(OUTPUT_MAP, outputMap);
        config.put(MAX_PREDICTION_TASKS, 5);
        String processorTag = randomAlphaOfLength(10);

        MLInferenceSearchRequestProcessor mLInferenceSearchRequestProcessor = factory
            .create(Collections.emptyMap(), processorTag, "test", true, config, null);
        assertNotNull(mLInferenceSearchRequestProcessor);
        assertEquals(mLInferenceSearchRequestProcessor.getTag(), processorTag);
        assertEquals(mLInferenceSearchRequestProcessor.getType(), MLInferenceSearchRequestProcessor.TYPE);
        assertEquals(mLInferenceSearchRequestProcessor.isIgnoreFailure(), true);
        assertEquals(mLInferenceSearchRequestProcessor.getDescription(), "test");
        assertEquals(mLInferenceSearchRequestProcessor.getOptionalInputMaps(), optionalInputMap);
        assertEquals(mLInferenceSearchRequestProcessor.getOptionalOutputMaps(), optionalOutputMap);
        assertEquals(mLInferenceSearchRequestProcessor.getInferenceProcessorAttributes().getModelId(), "model2");
        assertEquals(mLInferenceSearchRequestProcessor.getInferenceProcessorAttributes().getInputMaps(), inputMap);
        assertEquals(mLInferenceSearchRequestProcessor.getInferenceProcessorAttributes().getOutputMaps(), outputMap);
    }

    /**
     * Tests the creation of the MLInferenceSearchRequestProcessor with empty input_map fields.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testCreateNoInputMapFields() throws Exception {
        Map<String, Object> config = new HashMap<>();
        config.put(MODEL_ID, "model1");
        List<Map<String, String>> inputMap = new ArrayList<>();
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put("text_embedding", "$.inference_results[0].output[0].data");
        outputMap.add(output);
        config.put(INPUT_MAP, inputMap);
        config.put(OUTPUT_MAP, outputMap);
        String processorTag = randomAlphaOfLength(10);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            factory.create(Collections.emptyMap(), processorTag, null, false, config, null);
        });

        assertEquals(
            "Please provide at least one non-empty input_map or optional_input_map for ML Inference Search Request Processor",
            exception.getMessage()
        );
    }

    /**
     * Tests the creation of the MLInferenceSearchRequestProcessor with empty output_map fields.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testCreateNoOutputMapFields() throws Exception {
        Map<String, Object> config = new HashMap<>();
        config.put(MODEL_ID, "model1");
        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put("text_docs", "text");
        inputMap.add(input);
        List<Map<String, String>> outputMap = new ArrayList<>();

        config.put(INPUT_MAP, inputMap);
        config.put(OUTPUT_MAP, outputMap);
        String processorTag = randomAlphaOfLength(10);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            factory.create(Collections.emptyMap(), processorTag, null, false, config, null);
        });

        assertEquals(
            "Please provide at least one non-empty output_map or optional_output_map for ML Inference Search Request Processor",
            exception.getMessage()
        );
    }

    /**
     * Tests mean pooling transformation in search request processor
     * @throws Exception if an error occurs during the test
     */
    public void testExecute_MeanPoolingTransformation() throws Exception {
        String modelInputField = "inputs";
        String originalQueryField = "query.term.text.value";
        String multiVectorField = "ext.ml_inference.multi_vectors";
        String knnVectorField = "ext.ml_inference.knn_vector";

        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put(modelInputField, originalQueryField);
        inputMap.add(input);

        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(multiVectorField, "image_embeddings[0]");
        output.put(knnVectorField, "image_embeddings[0].meanPooling()");
        outputMap.add(output);

        MLInferenceSearchRequestProcessor processor = new MLInferenceSearchRequestProcessor(
            "model1",
            null,
            inputMap,
            outputMap,
            null,
            null,
            null,
            1,
            "tag",
            "description",
            false,
            "REMOTE",
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY
        );

        String inputQuery = "{\"query\":{\"term\":{\"text\":{\"value\":\"foo\",\"boost\":1.0}}}}";
        QueryBuilder incomingQuery = new TermQueryBuilder("text", "foo");
        SearchSourceBuilder source = new SearchSourceBuilder().query(incomingQuery);
        SearchRequest request = new SearchRequest().source(source);

        // Mock nested array structure for image embeddings
        List<List<Double>> imageEmbeddings = Arrays
            .asList(Arrays.asList(1.0, 2.0, 3.0), Arrays.asList(4.0, 5.0, 6.0), Arrays.asList(7.0, 8.0, 9.0));

        Map<String, Object> dataAsMap = new HashMap<>();
        dataAsMap.put("image_embeddings", Arrays.asList(imageEmbeddings));

        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(dataAsMap).build();

        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();

        ModelTensorOutput mlOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        MLTaskResponse response = MLTaskResponse.builder().output(mlOutput).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(response);
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(), any());

        PipelineProcessingContext requestContext = new PipelineProcessingContext();
        ActionListener<SearchRequest> listener = mock(ActionListener.class);

        processor.processRequestAsync(request, requestContext, listener);

        ArgumentCaptor<SearchRequest> argumentCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(listener).onResponse(argumentCaptor.capture());

        SearchRequest capturedRequest = argumentCaptor.getValue();

        // Verify multi_vectors contains the original nested array
        assertEquals(imageEmbeddings, requestContext.getAttribute(multiVectorField));

        // Verify knn_vector contains the mean pooled result
        List<Float> meanPooled = (List<Float>) requestContext.getAttribute(knnVectorField);
        assertEquals(3, meanPooled.size());
        assertEquals(4.0, meanPooled.get(0), 0.001); // (1+4+7)/3
        assertEquals(5.0, meanPooled.get(1), 0.001); // (2+5+8)/3
        assertEquals(6.0, meanPooled.get(2), 0.001); // (3+6+9)/3
    }

}
