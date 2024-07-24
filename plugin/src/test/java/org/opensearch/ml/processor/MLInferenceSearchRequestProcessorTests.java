/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.processor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.processor.InferenceProcessorAttributes.*;
import static org.opensearch.ml.processor.InferenceProcessorAttributes.MAX_PREDICTION_TASKS;
import static org.opensearch.ml.processor.MLInferenceSearchRequestProcessor.*;
import static org.opensearch.ml.processor.MLInferenceSearchRequestProcessor.MODEL_INPUT;

import java.util.*;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchParseException;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.RangeQueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.ingest.Processor;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.repackage.com.google.common.collect.ImmutableMap;
import org.opensearch.search.SearchModule;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.pipeline.PipelineProcessingContext;
import org.opensearch.test.AbstractBuilderTestCase;

public class MLInferenceSearchRequestProcessorTests extends AbstractBuilderTestCase {

    @Mock
    private Client client;

    @Mock
    private PipelineProcessingContext requestContext;

    static public final NamedXContentRegistry TEST_XCONTENT_REGISTRY_FOR_QUERY = new NamedXContentRegistry(
        new SearchModule(Settings.EMPTY, List.of()).getNamedXContents()
    );
    private static final String PROCESSOR_TAG = "inference";
    private static final String DESCRIPTION = "inference_test";

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
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

        MLInferenceSearchRequestProcessor requestProcessor = new MLInferenceSearchRequestProcessor(
            "model1",
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
                throw new RuntimeException("Failed in executing processRequestAsync.");
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
        } catch (OpenSearchParseException e) {
            assertEquals(e.getMessage(), ("[input_map] required property is missing"));
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
        } catch (OpenSearchParseException e) {
            assertEquals(e.getMessage(), ("[output_map] required property is missing"));
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
                ("The number of prediction task setting in this process is 3. It exceeds the max_prediction_tasks of 2. Please reduce the size of input_map or increase max_prediction_tasks.")
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
            assertEquals(e.getMessage(), ("The length of output_map and the length of input_map do no match."));
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
        config.put(INPUT_MAP, inputMap);
        config.put(OUTPUT_MAP, outputMap);
        config.put(MAX_PREDICTION_TASKS, 5);
        String processorTag = randomAlphaOfLength(10);

        MLInferenceSearchRequestProcessor MLInferenceSearchRequestProcessor = factory
            .create(Collections.emptyMap(), processorTag, null, false, config, null);
        assertNotNull(MLInferenceSearchRequestProcessor);
        assertEquals(MLInferenceSearchRequestProcessor.getTag(), processorTag);
        assertEquals(MLInferenceSearchRequestProcessor.getType(), MLInferenceSearchRequestProcessor.TYPE);
    }
}
