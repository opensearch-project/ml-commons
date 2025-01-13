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
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.utils.StringUtils.toJson;
import static org.opensearch.ml.processor.InferenceProcessorAttributes.INPUT_MAP;
import static org.opensearch.ml.processor.InferenceProcessorAttributes.MAX_PREDICTION_TASKS;
import static org.opensearch.ml.processor.InferenceProcessorAttributes.MODEL_CONFIG;
import static org.opensearch.ml.processor.InferenceProcessorAttributes.MODEL_ID;
import static org.opensearch.ml.processor.InferenceProcessorAttributes.OUTPUT_MAP;
import static org.opensearch.ml.processor.MLInferenceSearchResponseProcessor.DEFAULT_MAX_PREDICTION_TASKS;
import static org.opensearch.ml.processor.MLInferenceSearchResponseProcessor.DEFAULT_OUTPUT_FIELD_NAME;
import static org.opensearch.ml.processor.MLInferenceSearchResponseProcessor.FULL_RESPONSE_PATH;
import static org.opensearch.ml.processor.MLInferenceSearchResponseProcessor.FUNCTION_NAME;
import static org.opensearch.ml.processor.MLInferenceSearchResponseProcessor.MODEL_INPUT;
import static org.opensearch.ml.processor.MLInferenceSearchResponseProcessor.ONE_TO_ONE;
import static org.opensearch.ml.processor.MLInferenceSearchResponseProcessor.TYPE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchParseException;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchResponseSections;
import org.opensearch.client.Client;
import org.opensearch.common.document.DocumentField;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.ml.common.dataset.TextSimilarityInputDataSet;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.repackage.com.google.common.collect.ImmutableMap;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.SearchModule;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.internal.InternalSearchResponse;
import org.opensearch.search.pipeline.PipelineProcessingContext;
import org.opensearch.test.AbstractBuilderTestCase;

public class MLInferenceSearchResponseProcessorTests extends AbstractBuilderTestCase {
    @Mock
    private Client client;
    @Mock
    private PipelineProcessingContext responseContext;
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
     * Tests that an exception is thrown when the `processResponse` method is called, as this processor
     * makes asynchronous calls.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseException() throws Exception {

        MLInferenceSearchResponseProcessor responseProcessor = getMlInferenceSearchResponseProcessorSinglePairMapping(
            null,
            null,
            null,
            null,
            false,
            false,
            false
        );
        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(5, true, fieldName);

        try {
            responseProcessor.processResponse(request, response);

        } catch (Exception e) {
            assertEquals("ML inference search response processor make asynchronous calls and does not call processRequest", e.getMessage());
        }
    }

    /**
     * Tests the successful processing of a response with a single pair of input and output mappings.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseSuccess() throws Exception {
        String modelInputField = "inputs";
        String originalDocumentField = "text";
        String newDocumentField = "text_embedding";
        String modelOutputField = "response";
        MLInferenceSearchResponseProcessor responseProcessor = getMlInferenceSearchResponseProcessorSinglePairMapping(
            modelOutputField,
            modelInputField,
            originalDocumentField,
            newDocumentField,
            false,
            false,
            false
        );

        assertEquals(responseProcessor.getType(), TYPE);
        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(5, true, fieldName);

        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(ImmutableMap.of("response", Arrays.asList(0.0, 1.0, 2.0, 3.0, 4.0)))
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                assertEquals(newSearchResponse.getHits().getHits().length, 5);
                assertEquals(newSearchResponse.getHits().getHits()[0].getSourceAsMap().get("text_embedding"), 0.0);
                assertEquals(newSearchResponse.getHits().getHits()[1].getSourceAsMap().get("text_embedding"), 1.0);
                assertEquals(newSearchResponse.getHits().getHits()[2].getSourceAsMap().get("text_embedding"), 2.0);
                assertEquals(newSearchResponse.getHits().getHits()[3].getSourceAsMap().get("text_embedding"), 3.0);
                assertEquals(newSearchResponse.getHits().getHits()[4].getSourceAsMap().get("text_embedding"), 4.0);
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException(e);
            }
        };

        responseProcessor.processResponseAsync(request, response, responseContext, listener);
    }

    /**
     * Tests the successful processing of a response with a single pair of input and output mappings.
     * read the query text from input_mapping
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseSuccessReadQueryTextFromInputMap() throws Exception {
        String modelInputField = "text_docs";
        String originalDocumentField = "text";
        String newDocumentField = "similarity_score";
        String modelOutputField = "response";
        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put("query_text", "$._request.query.term.text.value");
        input.put(modelInputField, originalDocumentField);
        inputMap.add(input);
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newDocumentField, modelOutputField);
        outputMap.add(output);
        Map<String, String> modelConfig = new HashMap<>();

        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            inputMap,
            outputMap,
            modelConfig,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "text_similarity",
            false,
            false,
            false,
            "{ \"query_text\": \"${input_map.query_text}\", \"text_docs\":${input_map.text_docs}}",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            false
        );
        assertEquals(responseProcessor.getType(), TYPE);
        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(5, true, fieldName);

        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(ImmutableMap.of("response", Arrays.asList(0.0, 1.0, 2.0, 3.0, 4.0)))
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                assertEquals(newSearchResponse.getHits().getHits().length, 5);
                assertEquals(newSearchResponse.getHits().getHits()[0].getSourceAsMap().get(newDocumentField), 0.0);
                assertEquals(newSearchResponse.getHits().getHits()[1].getSourceAsMap().get(newDocumentField), 1.0);
                assertEquals(newSearchResponse.getHits().getHits()[2].getSourceAsMap().get(newDocumentField), 2.0);
                assertEquals(newSearchResponse.getHits().getHits()[3].getSourceAsMap().get(newDocumentField), 3.0);
                assertEquals(newSearchResponse.getHits().getHits()[4].getSourceAsMap().get(newDocumentField), 4.0);
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException(e);
            }
        };

        ArgumentCaptor<MLPredictionTaskRequest> argCaptor = ArgumentCaptor.forClass(MLPredictionTaskRequest.class);
        responseProcessor.processResponseAsync(request, response, responseContext, listener);
        // match model input
        verify(client, times(1)).execute(eq(MLPredictionTaskAction.INSTANCE), argCaptor.capture(), any());
        MLPredictionTaskRequest req = argCaptor.getValue();
        MLInput mlInput = req.getMlInput();
        TextSimilarityInputDataSet inputDataSet = (TextSimilarityInputDataSet) mlInput.getInputDataset();
        assertEquals(toJson(inputDataSet.getQueryText()), "foo");
        assertEquals(toJson(inputDataSet.getTextDocs()), "[\"value 0\",\"value 1\",\"value 2\",\"value 3\",\"value 4\"]");
    }

    /**
     * Tests read the query size and sort field from request
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseSuccessReadRequestMetaFieldFromInputMap() throws Exception {
        String modelInputField = "text_docs";
        String originalDocumentField = "text";
        String newDocumentField = "similarity_score";
        String modelOutputField = "response";
        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put("query_text", "$._request.query.term.text.value");
        input.put("sort", "$._request.sort");
        input.put("size", "$._request.size");
        input.put(modelInputField, originalDocumentField);
        inputMap.add(input);
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newDocumentField, modelOutputField);
        outputMap.add(output);
        Map<String, String> modelConfig = new HashMap<>();

        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            inputMap,
            outputMap,
            modelConfig,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            false,
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            false
        );
        assertEquals(responseProcessor.getType(), TYPE);
        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(5, true, fieldName);

        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(ImmutableMap.of("response", Arrays.asList(0.0, 1.0, 2.0, 3.0, 4.0)))
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                assertEquals(newSearchResponse.getHits().getHits().length, 5);
                assertEquals(newSearchResponse.getHits().getHits()[0].getSourceAsMap().get(newDocumentField), 0.0);
                assertEquals(newSearchResponse.getHits().getHits()[1].getSourceAsMap().get(newDocumentField), 1.0);
                assertEquals(newSearchResponse.getHits().getHits()[2].getSourceAsMap().get(newDocumentField), 2.0);
                assertEquals(newSearchResponse.getHits().getHits()[3].getSourceAsMap().get(newDocumentField), 3.0);
                assertEquals(newSearchResponse.getHits().getHits()[4].getSourceAsMap().get(newDocumentField), 4.0);
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException(e);
            }
        };

        ArgumentCaptor<MLPredictionTaskRequest> argCaptor = ArgumentCaptor.forClass(MLPredictionTaskRequest.class);
        responseProcessor.processResponseAsync(request, response, responseContext, listener);
        // match model input
        verify(client, times(1)).execute(eq(MLPredictionTaskAction.INSTANCE), argCaptor.capture(), any());
        MLPredictionTaskRequest req = argCaptor.getValue();
        MLInput mlInput = req.getMlInput();
        RemoteInferenceInputDataSet inputDataSet = (RemoteInferenceInputDataSet) mlInput.getInputDataset();
        assertEquals(
            toJson(inputDataSet.getParameters()),
            "{\"size\":\"5\",\"sort\":\"[{\\\"text\\\":{\\\"order\\\":\\\"asc\\\"}}]\",\"text_docs\":\"[\\\"value 0\\\",\\\"value 1\\\",\\\"value 2\\\",\\\"value 3\\\",\\\"value 4\\\"]\",\"query_text\":\"foo\"}"
        );
    }

    /**
     * Tests read the query text based on input_mapping
     * when the query mapping is not found, expect to
     * @throws Exception
     */
    @Test
    public void testProcessResponseSuccessReadQueryTextException() throws Exception {
        String modelInputField = "text_docs";
        String originalDocumentField = "text";
        String newDocumentField = "similarity_score";
        String modelOutputField = "response";
        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put("query_text", "$._request.query.term.text.value1");
        input.put(modelInputField, originalDocumentField);
        inputMap.add(input);
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newDocumentField, modelOutputField);
        outputMap.add(output);
        Map<String, String> modelConfig = new HashMap<>();

        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            inputMap,
            outputMap,
            modelConfig,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            false,
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            false
        );
        assertEquals(responseProcessor.getType(), TYPE);
        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(5, true, fieldName);

        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(ImmutableMap.of("response", Arrays.asList(0.0, 1.0, 2.0, 3.0, 4.0)))
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                throw new RuntimeException("error handling not properly");
            }

            @Override
            public void onFailure(Exception e) {
                assertEquals(
                    e.getMessage(),
                    "Missing required input field in query body. input_map: [text, $._request.query.term.text.value1], query body:{\"size\":5,\"query\":{\"term\":{\"text\":{\"value\":\"foo\",\"boost\":1.0}}},\"sort\":[{\"text\":{\"order\":\"asc\"}}]}"
                );
            }
        };

        responseProcessor.processResponseAsync(request, response, responseContext, listener);
        verify(client, times(0)).execute(any(), any(), any());
    }

    /**
     * Tests read the query text based on input_mapping, but query text not found
     * isRequestInputMissing is true, isDocumentFieldMissing is false
     * when the query mapping is not found, ignoreMissing then expect to read the document input
     * @throws Exception
     */
    @Test
    public void testProcessResponseSuccessReadQueryTextExceptionIgnoreMissing() throws Exception {
        String modelInputField = "text_docs";
        String originalDocumentField = "text";
        String newDocumentField = "similarity_score";
        String modelOutputField = "response";
        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put("query_text", "_request.query.term.text.value1");
        input.put(modelInputField, originalDocumentField);
        inputMap.add(input);
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newDocumentField, modelOutputField);
        outputMap.add(output);
        Map<String, String> modelConfig = new HashMap<>();

        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            inputMap,
            outputMap,
            modelConfig,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            true,
            "remote",
            false,
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            false
        );
        assertEquals(responseProcessor.getType(), TYPE);
        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(5, true, fieldName);

        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(ImmutableMap.of("response", Arrays.asList(0.0, 1.0, 2.0, 3.0, 4.0)))
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                assertEquals(newSearchResponse.getHits().getHits().length, 5);
                assertEquals(newSearchResponse.getHits().getHits()[0].getSourceAsMap().get(newDocumentField), 0.0);
                assertEquals(newSearchResponse.getHits().getHits()[1].getSourceAsMap().get(newDocumentField), 1.0);
                assertEquals(newSearchResponse.getHits().getHits()[2].getSourceAsMap().get(newDocumentField), 2.0);
                assertEquals(newSearchResponse.getHits().getHits()[3].getSourceAsMap().get(newDocumentField), 3.0);
                assertEquals(newSearchResponse.getHits().getHits()[4].getSourceAsMap().get(newDocumentField), 4.0);
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException("error handling not properly");
            }
        };

        ArgumentCaptor<MLPredictionTaskRequest> argCaptor = ArgumentCaptor.forClass(MLPredictionTaskRequest.class);
        responseProcessor.processResponseAsync(request, response, responseContext, listener);
        // match model input
        verify(client, times(1)).execute(eq(MLPredictionTaskAction.INSTANCE), argCaptor.capture(), any());
        MLPredictionTaskRequest req = argCaptor.getValue();
        MLInput mlInput = req.getMlInput();
        RemoteInferenceInputDataSet inputDataSet = (RemoteInferenceInputDataSet) mlInput.getInputDataset();
        assertEquals(
            toJson(inputDataSet.getParameters()),
            "{\"text_docs\":\"[\\\"value 0\\\",\\\"value 1\\\",\\\"value 2\\\",\\\"value 3\\\",\\\"value 4\\\"]\"}"
        );

    }

    /**
     * Tests create processor with one_to_one is true
     * with custom prompt
     * with one to one prediction, 5 documents in hits are calling 5 prediction tasks
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseOneToOneWithCustomPrompt() throws Exception {

        String documentField = "text";
        String modelInputField = "context";
        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put(modelInputField, documentField);
        inputMap.add(input);

        String newDocumentField = "llm_response";
        String modelOutputField = "response";
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newDocumentField, modelOutputField);
        outputMap.add(output);
        Map<String, String> modelConfig = new HashMap<>();
        modelConfig
            .put(
                "prompt",
                "\\n\\nHuman: You are a professional data analyst. You will always answer question based on the given context first. If the answer is not directly shown in the context, you will analyze the data and find the answer. If you don't know the answer, just say I don't know. Context: ${parameters.context}. \\n\\n Human: please summarize the documents \\n\\n Assistant:"
            );
        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            inputMap,
            outputMap,
            modelConfig,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            false,
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            true
        );

        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(5, true, fieldName);

        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", "there is 1 value")).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                assertEquals(newSearchResponse.getHits().getHits().length, 5);
                assertEquals(
                    newSearchResponse.getHits().getHits()[0].getSourceAsMap().get(newDocumentField).toString(),
                    "there is 1 value"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[1].getSourceAsMap().get(newDocumentField).toString(),
                    "there is 1 value"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[2].getSourceAsMap().get(newDocumentField).toString(),
                    "there is 1 value"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[3].getSourceAsMap().get(newDocumentField).toString(),
                    "there is 1 value"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[4].getSourceAsMap().get(newDocumentField).toString(),
                    "there is 1 value"
                );
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException(e);
            }

        };
        responseProcessor.processResponseAsync(request, response, responseContext, listener);
        verify(client, times(5)).execute(any(), any(), any());
    }

    /**
     * Tests create processor with one_to_one is false
     * with custom prompt
     * with many to one prediction, 5 documents in hits are calling 1 prediction tasks
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseManyToOneWithCustomPrompt() throws Exception {

        String documentField = "text";
        String modelInputField = "context";
        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put(modelInputField, documentField);
        inputMap.add(input);

        String newDocumentField = "llm_response";
        String modelOutputField = "response";
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newDocumentField, modelOutputField);
        outputMap.add(output);
        Map<String, String> modelConfig = new HashMap<>();
        modelConfig
            .put(
                "prompt",
                "\\n\\nHuman: You are a professional data analyst. You will always answer question based on the given context first. If the answer is not directly shown in the context, you will analyze the data and find the answer. If you don't know the answer, just say I don't know. Context: ${parameters.context}. \\n\\n Human: please summarize the documents \\n\\n Assistant:"
            );
        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            inputMap,
            outputMap,
            modelConfig,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            false,
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            false
        );

        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(5, true, fieldName);

        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", "there are 5 values")).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                assertEquals(newSearchResponse.getHits().getHits().length, 5);
                assertEquals(
                    newSearchResponse.getHits().getHits()[0].getSourceAsMap().get(newDocumentField).toString(),
                    "there are 5 values"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[1].getSourceAsMap().get(newDocumentField).toString(),
                    "there are 5 values"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[2].getSourceAsMap().get(newDocumentField).toString(),
                    "there are 5 values"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[3].getSourceAsMap().get(newDocumentField).toString(),
                    "there are 5 values"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[4].getSourceAsMap().get(newDocumentField).toString(),
                    "there are 5 values"
                );
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException(e);
            }

        };
        responseProcessor.processResponseAsync(request, response, responseContext, listener);
        verify(client, times(1)).execute(any(), any(), any());
    }

    /**
     * Tests create processor with one_to_one is false
     * with custom prompt
     * with many to one prediction, 5 documents in hits are calling 1 prediction tasks
     * with full response path false and no output mapping is provided
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseManyToOneWithCustomPromptFullResponsePathFalse() throws Exception {

        String documentField = "text";
        String modelInputField = "context";
        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put(modelInputField, documentField);
        inputMap.add(input);

        Map<String, String> modelConfig = new HashMap<>();
        modelConfig
            .put(
                "prompt",
                "\\n\\nHuman: You are a professional data analyst. You will always answer question based on the given context first. If the answer is not directly shown in the context, you will analyze the data and find the answer. If you don't know the answer, just say I don't know. Context: ${parameters.context}. \\n\\n Human: please summarize the documents \\n\\n Assistant:"
            );
        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            inputMap,
            null,
            modelConfig,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            false,
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            false
        );

        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(5, true, fieldName);
        Map<String, Object> predictionResult = ImmutableMap.of("response", "here is a summary of the documents");

        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(predictionResult).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                assertEquals(newSearchResponse.getHits().getHits().length, 5);
                assertEquals(newSearchResponse.getHits().getHits()[0].getSourceAsMap().get("inference_results"), predictionResult);
                assertEquals(newSearchResponse.getHits().getHits()[1].getSourceAsMap().get("inference_results"), predictionResult);
                assertEquals(newSearchResponse.getHits().getHits()[2].getSourceAsMap().get("inference_results"), predictionResult);
                assertEquals(newSearchResponse.getHits().getHits()[3].getSourceAsMap().get("inference_results"), predictionResult);
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException(e);
            }

        };
        responseProcessor.processResponseAsync(request, response, responseContext, listener);
        verify(client, times(1)).execute(any(), any(), any());
    }

    /**
     * Tests create processor with one_to_one is false
     * with custom prompt
     * with many to one prediction, 5 documents in hits are calling 1 prediction tasks
     * with full response path true and no output mapping is provided
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseManyToOneWithCustomPromptFullResponsePathTrue() throws Exception {

        String documentField = "text";
        String modelInputField = "context";
        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put(modelInputField, documentField);
        inputMap.add(input);

        Map<String, String> modelConfig = new HashMap<>();
        modelConfig
            .put(
                "prompt",
                "\\n\\nHuman: You are a professional data analyst. You will always answer question based on the given context first. If the answer is not directly shown in the context, you will analyze the data and find the answer. If you don't know the answer, just say I don't know. Context: ${parameters.context}. \\n\\n Human: please summarize the documents \\n\\n Assistant:"
            );
        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            inputMap,
            null,
            modelConfig,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            true,
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            false
        );

        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(5, true, fieldName);
        Map<String, Object> predictionResult = ImmutableMap.of("response", "here is a summary of the documents");
        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(predictionResult).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();
        Map<String, Object> fullPredictionResult = generateInferenceResult("here is a summary of the documents");

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                assertEquals(newSearchResponse.getHits().getHits().length, 5);
                assertEquals(newSearchResponse.getHits().getHits()[0].getSourceAsMap().get("inference_results"), fullPredictionResult);
                assertEquals(newSearchResponse.getHits().getHits()[1].getSourceAsMap().get("inference_results"), fullPredictionResult);
                assertEquals(newSearchResponse.getHits().getHits()[2].getSourceAsMap().get("inference_results"), fullPredictionResult);
                assertEquals(newSearchResponse.getHits().getHits()[3].getSourceAsMap().get("inference_results"), fullPredictionResult);
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException(e);
            }

        };
        responseProcessor.processResponseAsync(request, response, responseContext, listener);
        verify(client, times(1)).execute(any(), any(), any());
    }

    /**
     * Tests the successful processing of a response with a single pair of input and output mappings.
     * read the query text into model config
     * with query extensions
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseSuccessWriteToExt() throws Exception {
        String documentField = "text";
        String modelInputField = "context";
        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put(modelInputField, documentField);
        inputMap.add(input);

        String newDocumentField = "ext.ml_inference.llm_response";
        String modelOutputField = "response";
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newDocumentField, modelOutputField);
        outputMap.add(output);
        Map<String, String> modelConfig = new HashMap<>();
        modelConfig
            .put(
                "prompt",
                "\\n\\nHuman: You are a professional data analyst. You will always answer question based on the given context first. If the answer is not directly shown in the context, you will analyze the data and find the answer. If you don't know the answer, just say I don't know. Context: ${parameters.context}. \\n\\n Human: please summarize the documents \\n\\n Assistant:"
            );
        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            inputMap,
            outputMap,
            modelConfig,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            false,
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            false
        );

        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(5, true, fieldName);

        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", "there is 1 value")).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                assertEquals(newSearchResponse.getHits().getHits().length, 5);
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException(e);
            }

        };
        responseProcessor.processResponseAsync(request, response, responseContext, listener);
        verify(client, times(1)).execute(any(), any(), any());
    }

    /**
     * Tests create processor with one_to_one is true
     * with no mapping provided
     * with one to one prediction, 5 documents in hits are calling 5 prediction tasks
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseOneToOneWithNoMappings() throws Exception {

        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            null,
            null,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            true,
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            true
        );

        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(5, true, fieldName);

        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(ImmutableMap.of("response", Arrays.asList(0.0, 1.0, 2.0, 3.0, 4.0)))
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                assertEquals(newSearchResponse.getHits().getHits().length, 5);
                assertEquals(
                    newSearchResponse.getHits().getHits()[0].getSourceAsMap().get(DEFAULT_OUTPUT_FIELD_NAME).toString(),
                    "{inference_results=[{output=[{dataAsMap={response=[0.0, 1.0, 2.0, 3.0, 4.0]}}]}]}"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[1].getSourceAsMap().get(DEFAULT_OUTPUT_FIELD_NAME).toString(),
                    "{inference_results=[{output=[{dataAsMap={response=[0.0, 1.0, 2.0, 3.0, 4.0]}}]}]}"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[2].getSourceAsMap().get(DEFAULT_OUTPUT_FIELD_NAME).toString(),
                    "{inference_results=[{output=[{dataAsMap={response=[0.0, 1.0, 2.0, 3.0, 4.0]}}]}]}"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[3].getSourceAsMap().get(DEFAULT_OUTPUT_FIELD_NAME).toString(),
                    "{inference_results=[{output=[{dataAsMap={response=[0.0, 1.0, 2.0, 3.0, 4.0]}}]}]}"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[4].getSourceAsMap().get(DEFAULT_OUTPUT_FIELD_NAME).toString(),
                    "{inference_results=[{output=[{dataAsMap={response=[0.0, 1.0, 2.0, 3.0, 4.0]}}]}]}"
                );
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException(e);
            }

        };
        responseProcessor.processResponseAsync(request, response, responseContext, listener);
        verify(client, times(5)).execute(any(), any(), any());
    }

    /**
     * Tests create processor with one_to_one is true
     * with empty mapping provided
     * with one to one prediction, 5 documents in hits are calling 5 prediction tasks
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseOneToOneWithEmptyMappings() throws Exception {
        List<Map<String, String>> outputMap = new ArrayList<>();
        List<Map<String, String>> inputMap = new ArrayList<>();
        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            inputMap,
            outputMap,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            true,
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            true
        );

        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(5, true, fieldName);

        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(ImmutableMap.of("response", Arrays.asList(0.0, 1.0, 2.0, 3.0, 4.0)))
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                assertEquals(newSearchResponse.getHits().getHits().length, 5);
                assertEquals(
                    newSearchResponse.getHits().getHits()[0].getSourceAsMap().get(DEFAULT_OUTPUT_FIELD_NAME).toString(),
                    "{inference_results=[{output=[{dataAsMap={response=[0.0, 1.0, 2.0, 3.0, 4.0]}}]}]}"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[1].getSourceAsMap().get(DEFAULT_OUTPUT_FIELD_NAME).toString(),
                    "{inference_results=[{output=[{dataAsMap={response=[0.0, 1.0, 2.0, 3.0, 4.0]}}]}]}"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[2].getSourceAsMap().get(DEFAULT_OUTPUT_FIELD_NAME).toString(),
                    "{inference_results=[{output=[{dataAsMap={response=[0.0, 1.0, 2.0, 3.0, 4.0]}}]}]}"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[3].getSourceAsMap().get(DEFAULT_OUTPUT_FIELD_NAME).toString(),
                    "{inference_results=[{output=[{dataAsMap={response=[0.0, 1.0, 2.0, 3.0, 4.0]}}]}]}"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[4].getSourceAsMap().get(DEFAULT_OUTPUT_FIELD_NAME).toString(),
                    "{inference_results=[{output=[{dataAsMap={response=[0.0, 1.0, 2.0, 3.0, 4.0]}}]}]}"
                );
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException(e);
            }

        };
        responseProcessor.processResponseAsync(request, response, responseContext, listener);
        verify(client, times(5)).execute(any(), any(), any());
    }

    /**
     * Tests create processor with one_to_one is true
     * with output_maps
     * with one to one prediction, 5 documents in hits are calling 5 prediction tasks
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseOneToOneWithOutputMappings() throws Exception {

        String newDocumentField = "text_embedding";
        String modelOutputField = "response";
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newDocumentField, modelOutputField);
        outputMap.add(output);

        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            null,
            outputMap,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            false,
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            true
        );

        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(5, true, fieldName);

        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(ImmutableMap.of("response", Arrays.asList(0.0, 1.0, 2.0, 3.0, 4.0)))
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                assertEquals(newSearchResponse.getHits().getHits().length, 5);
                assertEquals(
                    newSearchResponse.getHits().getHits()[0].getSourceAsMap().get(newDocumentField).toString(),
                    "[0.0, 1.0, 2.0, 3.0, 4.0]"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[1].getSourceAsMap().get(newDocumentField).toString(),
                    "[0.0, 1.0, 2.0, 3.0, 4.0]"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[2].getSourceAsMap().get(newDocumentField).toString(),
                    "[0.0, 1.0, 2.0, 3.0, 4.0]"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[3].getSourceAsMap().get(newDocumentField).toString(),
                    "[0.0, 1.0, 2.0, 3.0, 4.0]"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[4].getSourceAsMap().get(newDocumentField).toString(),
                    "[0.0, 1.0, 2.0, 3.0, 4.0]"
                );
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException(e);
            }

        };
        responseProcessor.processResponseAsync(request, response, responseContext, listener);
        verify(client, times(5)).execute(any(), any(), any());
    }

    /**
     * Tests create processor with one_to_one is true
     * with output_maps
     * with one to one prediction, the only one prediction task onFailure
     * expect to run one prediction task
     * when there is one document, the combinedResponseListener calls onFailure
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseOneToOneWithOutputMappingsCombineResponseListenerFail() throws Exception {

        String newDocumentField = "text_embedding";
        String modelOutputField = "response";
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newDocumentField, modelOutputField);
        outputMap.add(output);

        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            null,
            outputMap,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            false,
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            true
        );

        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(1, true, fieldName);
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onFailure(new RuntimeException("Prediction Failed"));
            return null;
        }).when(client).execute(any(), any(), any());

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                throw new RuntimeException("error handling not properly");
            }

            @Override
            public void onFailure(Exception e) {
                assertEquals("Prediction Failed", e.getMessage());
            }

        };
        responseProcessor.processResponseAsync(request, response, responseContext, listener);
        verify(client, times(1)).execute(any(), any(), any());
    }

    /**
     * Tests create processor with one_to_one is true
     * with output_maps
     * with one to one prediction, the only one prediction task throw Exceptions
     * expect to run one prediction task
     * when there is one document, the combinedResponseListener calls onFailure
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseOneToOneWithOutputMappingsCombineResponseListenerException() throws Exception {

        String newDocumentField = "text_embedding";
        String modelOutputField = "response";
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newDocumentField, modelOutputField);
        outputMap.add(output);

        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            null,
            outputMap,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            false,
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            true
        );

        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(1, true, fieldName);
        when(client.execute(any(), any())).thenThrow(new RuntimeException("Prediction Failed"));
        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                throw new RuntimeException("error handling not properly");
            }

            @Override
            public void onFailure(Exception e) {
                assertEquals("Failed to process response: Prediction Failed", e.getMessage());
            }

        };
        responseProcessor.processResponseAsync(request, response, responseContext, listener);
        verify(client, times(1)).execute(any(), any(), any());
    }

    /**
     * Tests create processor with one_to_one is true
     * with output_maps
     * with one to one prediction, the only one prediction task throw Exceptions
     * expect to run one prediction task
     * when there is one document and ignoreFailure, should return the original response
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseOneToOneWithOutputMappingsCombineResponseListenerExceptionIgnoreFailure() throws Exception {

        String newDocumentField = "text_embedding";
        String modelOutputField = "response";
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newDocumentField, modelOutputField);
        outputMap.add(output);

        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            null,
            outputMap,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            false,
            true,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            true
        );

        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(1, true, fieldName);
        when(client.execute(any(), any())).thenThrow(new RuntimeException("Prediction Failed"));
        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                assertEquals(newSearchResponse.getHits().getHits(), response.getHits().getHits());
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException("error handling not properly");
            }

        };
        responseProcessor.processResponseAsync(request, response, responseContext, listener);
        verify(client, times(1)).execute(any(), any(), any());
    }

    /**
     * Tests create processor with one_to_one is true
     * with output_maps
     * createRewriteResponseListener throw Exceptions
     * expect to run one prediction task
     * when there is one document and ignoreFailure, should return the original response
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseCreateRewriteResponseListenerExceptionIgnoreFailure() throws Exception {

        String newDocumentField = "text_embedding";
        String modelOutputField = "response";
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newDocumentField, modelOutputField);
        outputMap.add(output);

        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            null,
            outputMap,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            false,
            true,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            false
        );

        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(1, true, fieldName);
        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(ImmutableMap.of("response", Arrays.asList(0.0, 1.0, 2.0, 3.0, 4.0)))
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        SearchResponse mockResponse = mock(SearchResponse.class);
        SearchHits searchHits = response.getHits();

        InternalSearchResponse internalSearchResponse = new InternalSearchResponse(searchHits, null, null, null, false, null, 1);
        when(mockResponse.getInternalResponse()).thenReturn(internalSearchResponse);

        RuntimeException mockException = new RuntimeException("Mock exception");
        AtomicInteger callCount = new AtomicInteger(0);

        when(mockResponse.getHits()).thenAnswer(invocation -> {

            int count = callCount.getAndIncrement();

            if (count == 6) {
                // throw exception when it reaches createRewriteResponseListener
                throw mockException;
            } else {
                return searchHits;
            }
        });

        when(mockResponse.getTook()).thenReturn(TimeValue.timeValueNanos(10));
        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                assertNotNull(newSearchResponse);
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException("error handling not properly");
            }

        };
        responseProcessor.processResponseAsync(request, mockResponse, responseContext, listener);
        verify(client, times(1)).execute(any(), any(), any());
    }

    /**
     * Tests create processor with one_to_one is false
     * with output_maps
     * createRewriteResponseListener throw Exceptions
     * expect to run one prediction task
     * createRewriteResponseListener should reach on Failure
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseCreateRewriteResponseListenerException() throws Exception {

        String newDocumentField = "text_embedding";
        String modelOutputField = "response";
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newDocumentField, modelOutputField);
        outputMap.add(output);

        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            null,
            outputMap,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            false,
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            false
        );

        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(1, true, fieldName);
        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(ImmutableMap.of("response", Arrays.asList(0.0, 1.0, 2.0, 3.0, 4.0)))
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        SearchResponse mockResponse = mock(SearchResponse.class);
        SearchHits searchHits = response.getHits();
        RuntimeException mockException = new RuntimeException("Mock exception");
        AtomicInteger callCount = new AtomicInteger(0);

        InternalSearchResponse internalSearchResponse = new InternalSearchResponse(searchHits, null, null, null, false, null, 1);
        when(mockResponse.getInternalResponse()).thenReturn(internalSearchResponse);

        when(mockResponse.getHits()).thenAnswer(invocation -> {

            int count = callCount.getAndIncrement();

            if (count == 2) {
                // throw exception when it reaches createRewriteResponseListener
                throw mockException;
            } else {
                return searchHits;
            }
        });

        when(mockResponse.getTook()).thenReturn(TimeValue.timeValueNanos(10));
        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                throw new RuntimeException("error handling not properly");
            }

            @Override
            public void onFailure(Exception e) {
                assertNotNull(e.getMessage());
            }
        };
        responseProcessor.processResponseAsync(request, mockResponse, responseContext, listener);
        verify(client, times(1)).execute(any(), any(), any());
    }

    /**
     * Tests create processor with one_to_one is true
     * with output_maps
     * test throwing OpenSearchStatusException
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseOpenSearchStatusException() throws Exception {

        String newDocumentField = "text_embedding";
        String modelOutputField = "response";
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newDocumentField, modelOutputField);
        outputMap.add(output);

        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            null,
            outputMap,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            false,
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            false
        );

        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(1, true, fieldName);
        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(ImmutableMap.of("response", Arrays.asList(0.0, 1.0, 2.0, 3.0, 4.0)))
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        SearchResponse mockResponse = mock(SearchResponse.class);
        SearchHits searchHits = response.getHits();
        RuntimeException mockException = new OpenSearchStatusException("Mock exception", RestStatus.BAD_REQUEST);
        AtomicInteger callCount = new AtomicInteger(0);
        ;
        when(mockResponse.getHits()).thenAnswer(invocation -> {

            int count = callCount.getAndIncrement();

            if (count == 0) {
                // throw exception when it reaches processResponseAsync
                throw mockException;
            } else {
                return searchHits;
            }
        });

        when(mockResponse.getTook()).thenReturn(TimeValue.timeValueNanos(10));
        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                throw new RuntimeException("error handling not properly");
            }

            @Override
            public void onFailure(Exception e) {
                assertNotNull(e.getMessage());
            }
        };
        responseProcessor.processResponseAsync(request, mockResponse, responseContext, listener);
        verify(client, times(0)).execute(any(), any(), any());
    }

    /**
     * Tests create processor with one_to_one is true
     * with output_maps
     * test throwing MLResourceNotFoundException
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseMLResourceNotFoundException() throws Exception {

        String newDocumentField = "text_embedding";
        String modelOutputField = "response";
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newDocumentField, modelOutputField);
        outputMap.add(output);

        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            null,
            outputMap,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            false,
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            false
        );

        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(1, true, fieldName);
        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(ImmutableMap.of("response", Arrays.asList(0.0, 1.0, 2.0, 3.0, 4.0)))
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        SearchResponse mockResponse = mock(SearchResponse.class);
        SearchHits searchHits = response.getHits();
        RuntimeException mockException = new MLResourceNotFoundException("Mock exception");
        AtomicInteger callCount = new AtomicInteger(0);
        ;
        when(mockResponse.getHits()).thenAnswer(invocation -> {

            int count = callCount.getAndIncrement();

            if (count == 0) {
                // throw exception when it reaches processResponseAsync
                throw mockException;
            } else {
                return searchHits;
            }
        });

        when(mockResponse.getTook()).thenReturn(TimeValue.timeValueNanos(10));
        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                throw new RuntimeException("error handling not properly");
            }

            @Override
            public void onFailure(Exception e) {
                assertNotNull(e.getMessage());
            }
        };
        responseProcessor.processResponseAsync(request, mockResponse, responseContext, listener);
        verify(client, times(0)).execute(any(), any(), any());
    }

    /**
     * Tests create processor with one_to_one is true
     * with output_maps
     * with one to one prediction, the only one prediction task throw exception
     * expect to run one prediction task
     * when there is one document, the combinedResponseListener calls onFailure
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseOneToOneWithOutputMappingsIgnoreFailure() throws Exception {

        String newDocumentField = "text_embedding";
        String modelOutputField = "response";
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newDocumentField, modelOutputField);
        outputMap.add(output);

        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            null,
            outputMap,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            false,
            true,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            true
        );

        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(1, true, fieldName);
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onFailure(new RuntimeException("Prediction Failed"));
            return null;
        }).when(client).execute(any(), any(), any());

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                assertEquals(newSearchResponse.getHits().getHits(), response.getHits().getHits());
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException("error handling not properly");
            }

        };
        responseProcessor.processResponseAsync(request, response, responseContext, listener);
        verify(client, times(1)).execute(any(), any(), any());
    }

    /**
     * Tests create processor with one_to_one is true
     * with output_maps
     * with one to one prediction, the only one prediction task throw exception
     * expect to run one prediction task
     * when there is one document, the combinedResponseListener calls onFailure
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseOneToOneWithOutputMappingsMLTaskResponseExceptionIgnoreFailure() throws Exception {

        String newDocumentField = "text_embedding";
        String modelOutputField = "response";
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newDocumentField, modelOutputField);
        outputMap.add(output);

        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            null,
            outputMap,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            false,
            true,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            true
        );

        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        MLTaskResponse mockMLTaskResponse = mock(MLTaskResponse.class);
        when(mockMLTaskResponse.getOutput()).thenThrow(new RuntimeException("get mlTaskResponse failed."));
        SearchResponse response = getSearchResponse(1, true, fieldName);
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(mockMLTaskResponse);
            return null;
        }).when(client).execute(any(), any(), any());

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                assertEquals(newSearchResponse.getHits().getHits(), response.getHits().getHits());
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException("error handling not properly");
            }

        };
        responseProcessor.processResponseAsync(request, response, responseContext, listener);
        verify(client, times(1)).execute(any(), any(), any());
    }

    /**
     * Tests create processor with one_to_one is true
     * with output_maps
     * with one to one prediction, when one of 5 prediction tasks failed,
     * expect to run one prediction task and the rest 4 predictions tasks are not created
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseOneToOneWithOutputMappingsPredictException() throws Exception {

        String newDocumentField = "text_embedding";
        String modelOutputField = "response";
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newDocumentField, modelOutputField);
        outputMap.add(output);

        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            null,
            outputMap,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            false,
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            true
        );

        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(5, true, fieldName);
        when(client.execute(any(), any())).thenThrow(new RuntimeException("Prediction Failed"));
        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                throw new RuntimeException("error handling not properly");
            }

            @Override
            public void onFailure(Exception e) {

                assertEquals("Failed to process response: Prediction Failed", e.getMessage());
            }

        };
        responseProcessor.processResponseAsync(request, response, responseContext, listener);
        verify(client, times(5)).execute(any(), any(), any());
    }

    /**
     * Tests create processor with one_to_one is true
     * with output_maps
     * with one to one prediction, when one of 5 prediction tasks failed,
     * expect to run one prediction task and the rest 4 predictions tasks are not created
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseOneToOneWithOutputMappingsPredictFail() throws Exception {

        String newDocumentField = "text_embedding";
        String modelOutputField = "response";
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newDocumentField, modelOutputField);
        outputMap.add(output);

        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            null,
            outputMap,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            false,
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            true
        );

        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(5, true, fieldName);
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onFailure(new RuntimeException("Prediction Failed"));
            return null;
        }).when(client).execute(any(), any(), any());
        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                throw new RuntimeException("error handling not properly");
            }

            @Override
            public void onFailure(Exception e) {

                assertEquals("Failed to process response: Prediction Failed", e.getMessage());
            }

        };
        responseProcessor.processResponseAsync(request, response, responseContext, listener);
        verify(client, times(1)).execute(any(), any(), any());
    }

    /**
     * Tests create processor with one_to_one is true
     * with output_maps
     * with one to one prediction, prediction tasks throw exception
     * ignore Failure is true
     * when ignoreFailure, will run all 5 prediction tasks
     * then return original response
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseOneToOneWithOutputMappingsPredictFailIgnoreFailure() throws Exception {

        String newDocumentField = "text_embedding";
        String modelOutputField = "response";
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newDocumentField, modelOutputField);
        outputMap.add(output);

        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            null,
            outputMap,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            false,
            true,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            true
        );

        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(5, true, fieldName);
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onFailure(new RuntimeException("Prediction Failed"));
            return null;
        }).when(client).execute(any(), any(), any());

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                assertEquals(newSearchResponse.getHits().getHits(), response.getHits().getHits());
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException("error handling not properly");
            }

        };
        responseProcessor.processResponseAsync(request, response, responseContext, listener);
        verify(client, times(5)).execute(any(), any(), any());
    }

    /**
     * Tests create processor with one_to_one is true
     * with two rounds predictions for every document
     * with one to one prediction, 5 documents in hits are calling 10 prediction tasks
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseOneToOneTwoRoundsPredictions() throws Exception {

        String modelInputField = "inputs";
        String modelOutputField = "response";

        // document fields for first round of prediction
        String originalDocumentField = "text";
        String newDocumentField = "text_embedding";

        // document fields for second round of prediction
        String originalDocumentField1 = "image";
        String newDocumentField1 = "image_embedding";

        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put(modelInputField, originalDocumentField);
        inputMap.add(input);

        Map<String, String> input1 = new HashMap<>();
        input1.put(modelInputField, originalDocumentField1);
        inputMap.add(input1);

        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newDocumentField, modelOutputField);
        outputMap.add(output);

        Map<String, String> output2 = new HashMap<>();
        output2.put(newDocumentField1, modelOutputField);
        outputMap.add(output2);

        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            inputMap,
            outputMap,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            false,
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            true
        );

        SearchRequest request = getSearchRequest();

        SearchResponse response = getSearchResponseTwoFields(5, true, originalDocumentField, originalDocumentField1);

        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(ImmutableMap.of("response", Arrays.asList(0.0, 1.0, 2.0, 3.0, 4.0)))
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                assertEquals(newSearchResponse.getHits().getHits().length, 5);
                assertEquals(
                    newSearchResponse.getHits().getHits()[0].getSourceAsMap().get(newDocumentField).toString(),
                    "[0.0, 1.0, 2.0, 3.0, 4.0]"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[1].getSourceAsMap().get(newDocumentField).toString(),
                    "[0.0, 1.0, 2.0, 3.0, 4.0]"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[2].getSourceAsMap().get(newDocumentField).toString(),
                    "[0.0, 1.0, 2.0, 3.0, 4.0]"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[3].getSourceAsMap().get(newDocumentField).toString(),
                    "[0.0, 1.0, 2.0, 3.0, 4.0]"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[4].getSourceAsMap().get(newDocumentField).toString(),
                    "[0.0, 1.0, 2.0, 3.0, 4.0]"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[0].getSourceAsMap().get(newDocumentField1).toString(),
                    "[0.0, 1.0, 2.0, 3.0, 4.0]"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[1].getSourceAsMap().get(newDocumentField1).toString(),
                    "[0.0, 1.0, 2.0, 3.0, 4.0]"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[2].getSourceAsMap().get(newDocumentField1).toString(),
                    "[0.0, 1.0, 2.0, 3.0, 4.0]"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[3].getSourceAsMap().get(newDocumentField1).toString(),
                    "[0.0, 1.0, 2.0, 3.0, 4.0]"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[4].getSourceAsMap().get(newDocumentField1).toString(),
                    "[0.0, 1.0, 2.0, 3.0, 4.0]"
                );
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException(e);
            }

        };
        responseProcessor.processResponseAsync(request, response, responseContext, listener);
        verify(client, times(10)).execute(any(), any(), any());
    }

    /**
     * Tests create processor with one_to_one is true
     * with two rounds predictions for every document
     * failed in first round prediction when ignoreFailure is false
     * expect to throw exception without further processing
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseOneToOneTwoRoundsPredictionsOneException() throws Exception {

        String modelInputField = "inputs";
        String modelOutputField = "response";

        // document fields for first round of prediction
        String originalDocumentField = "text";
        String newDocumentField = "text_embedding";

        // document fields for second round of prediction
        String originalDocumentField1 = "image";
        String newDocumentField1 = "image_embedding";

        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put(modelInputField, originalDocumentField);
        inputMap.add(input);

        Map<String, String> input1 = new HashMap<>();
        input1.put(modelInputField, originalDocumentField1);
        inputMap.add(input1);

        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newDocumentField, modelOutputField);
        outputMap.add(output);

        Map<String, String> output2 = new HashMap<>();
        output2.put(newDocumentField1, modelOutputField);
        outputMap.add(output2);

        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            inputMap,
            outputMap,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            false,
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            true
        );

        SearchRequest request = getSearchRequest();
        // create a search response with a typo in the document field
        SearchResponse response = getSearchResponseTwoFields(5, true, originalDocumentField + "typo", originalDocumentField1);

        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(ImmutableMap.of("response", Arrays.asList(0.0, 1.0, 2.0, 3.0, 4.0)))
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                throw new RuntimeException("error handling not properly");
            }

            @Override
            public void onFailure(Exception e) {
                assertEquals(
                    "cannot find all required input fields: [text] in hit:{\n"
                        + "  \"_id\" : \"doc 0\",\n"
                        + "  \"_score\" : 0.0,\n"
                        + "  \"_source\" : {\n"
                        + "    \"texttypo\" : \"value 0\",\n"
                        + "    \"image\" : \"value 0\"\n"
                        + "  }\n"
                        + "} and query body:{\"size\":5,\"query\":{\"term\":{\"text\":{\"value\":\"foo\",\"boost\":1.0}}},\"sort\":[{\"text\":{\"order\":\"asc\"}}]}",
                    e.getMessage()
                );
            }

        };
        responseProcessor.processResponseAsync(request, response, responseContext, listener);
        verify(client, times(0)).execute(any(), any(), any());
    }

    /**
     * Tests create processor with one_to_one is true
     * with two rounds predictions for every document
     * failed in first round prediction when ignoreMissing is true
     * expect to return document with second round prediction results
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseOneToOneTwoRoundsPredictionsOneExceptionIgnoreMissing() throws Exception {

        String modelInputField = "inputs";
        String modelOutputField = "response";

        // document fields for first round of prediction
        String originalDocumentField = "text";
        String newDocumentField = "text_embedding";

        // document fields for second round of prediction
        String originalDocumentField1 = "image";
        String newDocumentField1 = "image_embedding";

        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put(modelInputField, originalDocumentField);
        inputMap.add(input);

        Map<String, String> input1 = new HashMap<>();
        input1.put(modelInputField, originalDocumentField1);
        inputMap.add(input1);

        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newDocumentField, modelOutputField);
        outputMap.add(output);

        Map<String, String> output2 = new HashMap<>();
        output2.put(newDocumentField1, modelOutputField);
        outputMap.add(output2);

        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            inputMap,
            outputMap,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            true,
            "remote",
            false,
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            true
        );

        SearchRequest request = getSearchRequest();
        // create a search response with a typo in the document field
        SearchResponse response = getSearchResponseTwoFields(5, true, originalDocumentField + "typo", originalDocumentField1);

        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(ImmutableMap.of("response", Arrays.asList(0.0, 1.0, 2.0, 3.0, 4.0)))
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                assertEquals(newSearchResponse.getHits().getHits().length, 5);
                assertEquals(
                    newSearchResponse.getHits().getHits()[0].getSourceAsMap().get(newDocumentField1).toString(),
                    "[0.0, 1.0, 2.0, 3.0, 4.0]"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[1].getSourceAsMap().get(newDocumentField1).toString(),
                    "[0.0, 1.0, 2.0, 3.0, 4.0]"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[2].getSourceAsMap().get(newDocumentField1).toString(),
                    "[0.0, 1.0, 2.0, 3.0, 4.0]"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[3].getSourceAsMap().get(newDocumentField1).toString(),
                    "[0.0, 1.0, 2.0, 3.0, 4.0]"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[4].getSourceAsMap().get(newDocumentField1).toString(),
                    "[0.0, 1.0, 2.0, 3.0, 4.0]"
                );
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException(e);
            }

        };
        responseProcessor.processResponseAsync(request, response, responseContext, listener);
        verify(client, times(10)).execute(any(), any(), any());
    }

    /**
     * Tests create processor with one_to_one is true
     * with two rounds predictions for every document
     * failed in first round prediction when ignoreFailure is true
     * expect to return document with second round prediction results
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseOneToOneTwoRoundsPredictionsOneExceptionIgnoreFailure() throws Exception {

        String modelInputField = "inputs";
        String modelOutputField = "response";

        // document fields for first round of prediction
        String originalDocumentField = "text";
        String newDocumentField = "text_embedding";

        // document fields for second round of prediction
        String originalDocumentField1 = "image";
        String newDocumentField1 = "image_embedding";

        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put(modelInputField, originalDocumentField);
        inputMap.add(input);

        Map<String, String> input1 = new HashMap<>();
        input1.put(modelInputField, originalDocumentField1);
        inputMap.add(input1);

        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newDocumentField, modelOutputField);
        outputMap.add(output);

        Map<String, String> output2 = new HashMap<>();
        output2.put(newDocumentField1, modelOutputField);
        outputMap.add(output2);

        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            inputMap,
            outputMap,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            false,
            true,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            true
        );

        SearchRequest request = getSearchRequest();
        // create a search response with a typo in the document field
        SearchResponse response = getSearchResponseTwoFields(5, true, originalDocumentField + "typo", originalDocumentField1);

        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(ImmutableMap.of("response", Arrays.asList(0.0, 1.0, 2.0, 3.0, 4.0)))
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                assertEquals(newSearchResponse.getHits().getHits().length, 5);
                assertNull(newSearchResponse.getHits().getHits()[0].getSourceAsMap().get(newDocumentField1));
                assertNull(newSearchResponse.getHits().getHits()[1].getSourceAsMap().get(newDocumentField1));
                assertNull(newSearchResponse.getHits().getHits()[2].getSourceAsMap().get(newDocumentField1));
                assertNull(newSearchResponse.getHits().getHits()[3].getSourceAsMap().get(newDocumentField1));
                assertNull(newSearchResponse.getHits().getHits()[4].getSourceAsMap().get(newDocumentField1));
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException(e);
            }

        };
        responseProcessor.processResponseAsync(request, response, responseContext, listener);
        verify(client, times(0)).execute(any(), any(), any());
    }

    /**
     * Tests the successful processing of a response without any input-output mappings.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseNoMappingSuccess() throws Exception {
        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            null,
            null,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            true,
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            false
        );

        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(5, true, fieldName);

        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(ImmutableMap.of("response", Arrays.asList(0.0, 1.0, 2.0, 3.0, 4.0)))
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                assertEquals(newSearchResponse.getHits().getHits().length, 5);
                assertEquals(
                    newSearchResponse.getHits().getHits()[0].getSourceAsMap().get(DEFAULT_OUTPUT_FIELD_NAME).toString(),
                    "{inference_results=[{output=[{dataAsMap={response=[0.0, 1.0, 2.0, 3.0, 4.0]}}]}]}"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[1].getSourceAsMap().get(DEFAULT_OUTPUT_FIELD_NAME).toString(),
                    "{inference_results=[{output=[{dataAsMap={response=[0.0, 1.0, 2.0, 3.0, 4.0]}}]}]}"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[2].getSourceAsMap().get(DEFAULT_OUTPUT_FIELD_NAME).toString(),
                    "{inference_results=[{output=[{dataAsMap={response=[0.0, 1.0, 2.0, 3.0, 4.0]}}]}]}"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[3].getSourceAsMap().get(DEFAULT_OUTPUT_FIELD_NAME).toString(),
                    "{inference_results=[{output=[{dataAsMap={response=[0.0, 1.0, 2.0, 3.0, 4.0]}}]}]}"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[4].getSourceAsMap().get(DEFAULT_OUTPUT_FIELD_NAME).toString(),
                    "{inference_results=[{output=[{dataAsMap={response=[0.0, 1.0, 2.0, 3.0, 4.0]}}]}]}"
                );
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException(e);
            }
        };

        responseProcessor.processResponseAsync(request, response, responseContext, listener);
    }

    /**
     * Tests the successful processing of a response without any input-output mappings.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseEmptyMappingSuccess() throws Exception {
        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        inputMap.add(input);
        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            inputMap,
            null,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            true,
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            false
        );

        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(5, true, fieldName);

        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(ImmutableMap.of("response", Arrays.asList(0.0, 1.0, 2.0, 3.0, 4.0)))
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                assertEquals(newSearchResponse.getHits().getHits().length, 5);
                assertEquals(
                    newSearchResponse.getHits().getHits()[0].getSourceAsMap().get(DEFAULT_OUTPUT_FIELD_NAME).toString(),
                    "{inference_results=[{output=[{dataAsMap={response=[0.0, 1.0, 2.0, 3.0, 4.0]}}]}]}"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[1].getSourceAsMap().get(DEFAULT_OUTPUT_FIELD_NAME).toString(),
                    "{inference_results=[{output=[{dataAsMap={response=[0.0, 1.0, 2.0, 3.0, 4.0]}}]}]}"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[2].getSourceAsMap().get(DEFAULT_OUTPUT_FIELD_NAME).toString(),
                    "{inference_results=[{output=[{dataAsMap={response=[0.0, 1.0, 2.0, 3.0, 4.0]}}]}]}"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[3].getSourceAsMap().get(DEFAULT_OUTPUT_FIELD_NAME).toString(),
                    "{inference_results=[{output=[{dataAsMap={response=[0.0, 1.0, 2.0, 3.0, 4.0]}}]}]}"
                );
                assertEquals(
                    newSearchResponse.getHits().getHits()[4].getSourceAsMap().get(DEFAULT_OUTPUT_FIELD_NAME).toString(),
                    "{inference_results=[{output=[{dataAsMap={response=[0.0, 1.0, 2.0, 3.0, 4.0]}}]}]}"
                );
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException(e);
            }
        };

        responseProcessor.processResponseAsync(request, response, responseContext, listener);
    }

    /**
     * Tests the successful processing of a response with a list of embeddings as the output.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseListOfEmbeddingsSuccess() throws Exception {
        /**
         * sample response before inference
         * {
         * { "text" : "value 0" },
         * { "text" : "value 1" },
         * { "text" : "value 2" },
         * { "text" : "value 3" },
         * { "text" : "value 4" }
         * }
         *
         * sample response after inference
         *  { "text" : "value 0", "text_embedding":[0.1, 0.2]},
         *  { "text" : "value 1", "text_embedding":[0.2, 0.2]},
         *  { "text" : "value 2", "text_embedding":[0.3, 0.2]},
         *  { "text" : "value 3","text_embedding":[0.4, 0.2]},
         *  { "text" : "value 4","text_embedding":[0.5, 0.2]}
         */

        String modelInputField = "inputs";
        String originalDocumentField = "text";
        String newDocumentField = "text_embedding";
        String modelOutputField = "response";
        MLInferenceSearchResponseProcessor responseProcessor = getMlInferenceSearchResponseProcessorSinglePairMapping(
            modelOutputField,
            modelInputField,
            originalDocumentField,
            newDocumentField,
            false,
            false,
            false
        );
        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(5, true, fieldName);

        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(
                ImmutableMap
                    .of(
                        "response",
                        Arrays
                            .asList(
                                Arrays.asList(0.1, 0.2),
                                Arrays.asList(0.2, 0.2),
                                Arrays.asList(0.3, 0.2),
                                Arrays.asList(0.4, 0.2),
                                Arrays.asList(0.5, 0.2)
                            )
                    )
            )
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                assertEquals(newSearchResponse.getHits().getHits().length, 5);
                assertEquals(newSearchResponse.getHits().getHits()[0].getSourceAsMap().get("text_embedding"), Arrays.asList(0.1, 0.2));
                assertEquals(newSearchResponse.getHits().getHits()[1].getSourceAsMap().get("text_embedding"), Arrays.asList(0.2, 0.2));
                assertEquals(newSearchResponse.getHits().getHits()[2].getSourceAsMap().get("text_embedding"), Arrays.asList(0.3, 0.2));
                assertEquals(newSearchResponse.getHits().getHits()[3].getSourceAsMap().get("text_embedding"), Arrays.asList(0.4, 0.2));
                assertEquals(newSearchResponse.getHits().getHits()[4].getSourceAsMap().get("text_embedding"), Arrays.asList(0.5, 0.2));
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException(e);
            }
        };

        responseProcessor.processResponseAsync(request, response, responseContext, listener);
    }

    /**
     * Tests the successful processing of a response where the existing document field is overridden.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseOverrideSameField() throws Exception {
        /**
         * sample response before inference
         * {
         * { "text" : "value 0" },
         * { "text" : "value 1" },
         * { "text" : "value 2" },
         * { "text" : "value 3" },
         * { "text" : "value 4" }
         * }
         *
         * sample response after inference
         *  { "text":[0.1, 0.2]},
         *  { "text":[0.2, 0.2]},
         *  { "text":[0.3, 0.2]},
         *  { "text":[0.4, 0.2]},
         *  { "text":[0.5, 0.2]}
         */

        String modelInputField = "inputs";
        String originalDocumentField = "text";
        String newDocumentField = "text";
        String modelOutputField = "response";
        MLInferenceSearchResponseProcessor responseProcessor = getMlInferenceSearchResponseProcessorSinglePairMapping(
            modelOutputField,
            modelInputField,
            originalDocumentField,
            newDocumentField,
            true,
            false,
            false
        );
        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(5, true, fieldName);

        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(
                ImmutableMap
                    .of(
                        "response",
                        Arrays
                            .asList(
                                Arrays.asList(0.1, 0.2),
                                Arrays.asList(0.2, 0.2),
                                Arrays.asList(0.3, 0.2),
                                Arrays.asList(0.4, 0.2),
                                Arrays.asList(0.5, 0.2)
                            )
                    )
            )
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                assertEquals(newSearchResponse.getHits().getHits().length, 5);
                assertEquals(newSearchResponse.getHits().getHits()[0].getSourceAsMap().get("text"), Arrays.asList(0.1, 0.2));
                assertEquals(newSearchResponse.getHits().getHits()[1].getSourceAsMap().get("text"), Arrays.asList(0.2, 0.2));
                assertEquals(newSearchResponse.getHits().getHits()[2].getSourceAsMap().get("text"), Arrays.asList(0.3, 0.2));
                assertEquals(newSearchResponse.getHits().getHits()[3].getSourceAsMap().get("text"), Arrays.asList(0.4, 0.2));
                assertEquals(newSearchResponse.getHits().getHits()[4].getSourceAsMap().get("text"), Arrays.asList(0.5, 0.2));
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException(e);
            }
        };
        responseProcessor.processResponseAsync(request, response, responseContext, listener);
    }

    /**
     * Tests the successful processing of a response where the existing document field is skipped.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseOverrideSameFieldFalse() throws Exception {
        /**
         * sample response before inference
         * {
         * { "text" : "value 0" },
         * { "text" : "value 1" },
         * { "text" : "value 2" },
         * { "text" : "value 3" },
         * { "text" : "value 4" }
         * }
         *
         * sample response after inference
         *  { "text":[0.1, 0.2]},
         *  { "text":[0.2, 0.2]},
         *  { "text":[0.3, 0.2]},
         *  { "text":[0.4, 0.2]},
         *  { "text":[0.5, 0.2]}
         */

        String modelInputField = "inputs";
        String originalDocumentField = "text";
        String newDocumentField = "text";
        String modelOutputField = "response";
        MLInferenceSearchResponseProcessor responseProcessor = getMlInferenceSearchResponseProcessorSinglePairMapping(
            modelOutputField,
            modelInputField,
            originalDocumentField,
            newDocumentField,
            false,
            false,
            false
        );
        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(5, true, fieldName);

        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(
                ImmutableMap
                    .of(
                        "response",
                        Arrays
                            .asList(
                                Arrays.asList(0.1, 0.2),
                                Arrays.asList(0.2, 0.2),
                                Arrays.asList(0.3, 0.2),
                                Arrays.asList(0.4, 0.2),
                                Arrays.asList(0.5, 0.2)
                            )
                    )
            )
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                assertEquals(newSearchResponse.getHits().getHits().length, 5);
                assertEquals(newSearchResponse.getHits().getHits()[0].getSourceAsMap().get("text"), "value 0");
                assertEquals(newSearchResponse.getHits().getHits()[1].getSourceAsMap().get("text"), "value 1");
                assertEquals(newSearchResponse.getHits().getHits()[2].getSourceAsMap().get("text"), "value 2");
                assertEquals(newSearchResponse.getHits().getHits()[3].getSourceAsMap().get("text"), "value 3");
                assertEquals(newSearchResponse.getHits().getHits()[4].getSourceAsMap().get("text"), "value 4");
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException(e);
            }
        };
        responseProcessor.processResponseAsync(request, response, responseContext, listener);

    }

    /**
     * Tests the successful processing of a response where one input field is missing,
     * and the `ignoreMissing` flag is set to true.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseListOfEmbeddingsMissingOneInputIgnoreMissingSuccess() throws Exception {
        /**
         * sample response before inference
         * {
         * { "text" : "value 0" },
         * { "text" : "value 1" },
         * { "textMissing" : "value 2" },
         * { "text" : "value 3" },
         * { "text" : "value 4" }
         * }
         *
         * sample response after inference
         *  { "text" : "value 0", "text_embedding":[0.1, 0.2]},
         *  { "text" : "value 1", "text_embedding":[0.2, 0.2]},
         *  { "textMissing" : "value 2"},
         *  { "text" : "value 3"，"text_embedding":[0.4, 0.2]},
         *  { "text" : "value 4"，"text_embedding":[0.5, 0.2]}
         */

        String modelInputField = "inputs";
        String originalDocumentField = "text";
        String newDocumentField = "text_embedding";
        String modelOutputField = "response";
        MLInferenceSearchResponseProcessor responseProcessor = getMlInferenceSearchResponseProcessorSinglePairMapping(
            modelOutputField,
            modelInputField,
            originalDocumentField,
            newDocumentField,
            false,
            false,
            true
        );
        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponseMissingField(5, true, fieldName);

        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(
                ImmutableMap
                    .of(
                        "response",
                        Arrays.asList(Arrays.asList(0.1, 0.2), Arrays.asList(0.2, 0.2), Arrays.asList(0.4, 0.2), Arrays.asList(0.5, 0.2))
                    )
            )
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                assertEquals(newSearchResponse.getHits().getHits().length, 5);
                assertEquals(newSearchResponse.getHits().getHits()[0].getSourceAsMap().get("text_embedding"), Arrays.asList(0.1, 0.2));
                assertEquals(newSearchResponse.getHits().getHits()[1].getSourceAsMap().get("text_embedding"), Arrays.asList(0.2, 0.2));

                assertEquals(newSearchResponse.getHits().getHits()[3].getSourceAsMap().get("text_embedding"), Arrays.asList(0.4, 0.2));
                assertEquals(newSearchResponse.getHits().getHits()[4].getSourceAsMap().get("text_embedding"), Arrays.asList(0.5, 0.2));

            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException(e);
            }
        };

        responseProcessor.processResponseAsync(request, response, responseContext, listener);
    }

    /**
     * Tests the case where one input field is missing, and an IllegalArgumentException is expected
     * when the `ignoreMissing` flag is set to false.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseListOfEmbeddingsMissingOneInputException() throws Exception {
        /**
         * sample response before inference
         * {
         * { "text" : "value 0" },
         * { "text" : "value 1" },
         * { "textMissing" : "value 2" },
         * { "text" : "value 3" },
         * { "text" : "value 4" }
         * }
         *
         * sample response after inference
         *  { "text" : "value 0", "text_embedding":[0.1, 0.2]},
         *  { "text" : "value 1", "text_embedding":[0.2, 0.2]},
         *  { "textMissing" : "value 2"},
         *  { "text" : "value 3"，"text_embedding":[0.4, 0.2]},
         *  { "text" : "value 4"，"text_embedding":[0.5, 0.2]}
         */

        String modelInputField = "inputs";
        String originalDocumentField = "text";
        String newDocumentField = "text_embedding";
        String modelOutputField = "response";
        MLInferenceSearchResponseProcessor responseProcessor = getMlInferenceSearchResponseProcessorSinglePairMapping(
            modelOutputField,
            modelInputField,
            originalDocumentField,
            newDocumentField,
            false,
            false,
            false
        );
        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponseMissingField(5, true, fieldName);

        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(
                ImmutableMap
                    .of(
                        "response",
                        Arrays.asList(Arrays.asList(0.1, 0.2), Arrays.asList(0.2, 0.2), Arrays.asList(0.4, 0.2), Arrays.asList(0.5, 0.2))
                    )
            )
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                throw new RuntimeException("error handling not properly");
            }

            @Override
            public void onFailure(Exception e) {
                assertEquals(
                    "cannot find all required input fields: [text] in hit:{\n"
                        + "  \"_id\" : \"doc 2\",\n"
                        + "  \"_score\" : 2.0,\n"
                        + "  \"_source\" : {\n"
                        + "    \"textMissing\" : \"value 2\"\n"
                        + "  }\n"
                        + "} and query body:{\"size\":5,\"query\":{\"term\":{\"text\":{\"value\":\"foo\",\"boost\":1.0}}},\"sort\":[{\"text\":{\"order\":\"asc\"}}]}",
                    e.getMessage()
                );
            }
        };

        responseProcessor.processResponseAsync(request, response, responseContext, listener);
    }

    /**
     * Tests the successful processing of a response with two rounds of prediction.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseTwoRoundsOfPredictionSuccess() throws Exception {
        String modelInputField = "inputs";
        String modelOutputField = "response";

        // document fields for first round of prediction
        String originalDocumentField = "text";
        String newDocumentField = "text_embedding";

        // document fields for second round of prediction
        String originalDocumentField1 = "image";
        String newDocumentField1 = "image_embedding";

        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put(modelInputField, originalDocumentField);
        inputMap.add(input);

        Map<String, String> input1 = new HashMap<>();
        input1.put(modelInputField, originalDocumentField1);
        inputMap.add(input1);

        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newDocumentField, modelOutputField);
        outputMap.add(output);

        Map<String, String> output2 = new HashMap<>();
        output2.put(newDocumentField1, modelOutputField);
        outputMap.add(output2);

        Map<String, String> modelConfig = new HashMap<>();
        modelConfig.put("model_task_type", "TEXT_EMBEDDING");
        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            inputMap,
            outputMap,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            false,
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            false
        );
        SearchRequest request = getSearchRequest();
        SearchResponse response = getSearchResponseTwoFields(5, true, originalDocumentField, originalDocumentField1);

        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(ImmutableMap.of("response", Arrays.asList(0.0, 1.0, 2.0, 3.0, 4.0)))
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                assertEquals(newSearchResponse.getHits().getHits().length, 5);
                assertEquals(newSearchResponse.getHits().getHits()[0].getSourceAsMap().get(newDocumentField), 0.0);
                assertEquals(newSearchResponse.getHits().getHits()[1].getSourceAsMap().get(newDocumentField), 1.0);
                assertEquals(newSearchResponse.getHits().getHits()[2].getSourceAsMap().get(newDocumentField), 2.0);
                assertEquals(newSearchResponse.getHits().getHits()[3].getSourceAsMap().get(newDocumentField), 3.0);
                assertEquals(newSearchResponse.getHits().getHits()[4].getSourceAsMap().get(newDocumentField), 4.0);

                assertEquals(newSearchResponse.getHits().getHits()[0].getSourceAsMap().get(newDocumentField1), 0.0);
                assertEquals(newSearchResponse.getHits().getHits()[1].getSourceAsMap().get(newDocumentField1), 1.0);
                assertEquals(newSearchResponse.getHits().getHits()[2].getSourceAsMap().get(newDocumentField1), 2.0);
                assertEquals(newSearchResponse.getHits().getHits()[3].getSourceAsMap().get(newDocumentField1), 3.0);
                assertEquals(newSearchResponse.getHits().getHits()[4].getSourceAsMap().get(newDocumentField1), 4.0);
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException(e);
            }
        };

        responseProcessor.processResponseAsync(request, response, responseContext, listener);
    }

    /**
     * Tests the successful processing of a response with one model input and multiple model outputs.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseOneModelInputMultipleModelOutputs() throws Exception {
        // one model input
        String modelInputField = "inputs";
        String originalDocumentField = "text";

        // two model outputs
        String modelOutputField = "response";
        String newDocumentField = "text_embedding";
        String modelOutputField1 = "response_type";
        String newDocumentField1 = "embedding_type";

        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put(modelInputField, originalDocumentField);
        inputMap.add(input);

        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newDocumentField, modelOutputField);
        output.put(newDocumentField1, modelOutputField1);
        outputMap.add(output);
        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            inputMap,
            outputMap,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            false,
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            false
        );
        SearchRequest request = getSearchRequest();
        SearchResponse response = getSearchResponse(5, true, originalDocumentField);

        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(ImmutableMap.of(modelOutputField, Arrays.asList(0.0, 1.0, 2.0, 3.0, 4.0), "response_type", "embedding_float"))
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                assertEquals(newSearchResponse.getHits().getHits().length, 5);
                assertEquals(newSearchResponse.getHits().getHits()[0].getSourceAsMap().get(newDocumentField), 0.0);
                assertEquals(newSearchResponse.getHits().getHits()[1].getSourceAsMap().get(newDocumentField), 1.0);
                assertEquals(newSearchResponse.getHits().getHits()[2].getSourceAsMap().get(newDocumentField), 2.0);
                assertEquals(newSearchResponse.getHits().getHits()[3].getSourceAsMap().get(newDocumentField), 3.0);
                assertEquals(newSearchResponse.getHits().getHits()[4].getSourceAsMap().get(newDocumentField), 4.0);

                assertEquals(newSearchResponse.getHits().getHits()[0].getSourceAsMap().get(newDocumentField1), "embedding_float");
                assertEquals(newSearchResponse.getHits().getHits()[1].getSourceAsMap().get(newDocumentField1), "embedding_float");
                assertEquals(newSearchResponse.getHits().getHits()[2].getSourceAsMap().get(newDocumentField1), "embedding_float");
                assertEquals(newSearchResponse.getHits().getHits()[3].getSourceAsMap().get(newDocumentField1), "embedding_float");
                assertEquals(newSearchResponse.getHits().getHits()[4].getSourceAsMap().get(newDocumentField1), "embedding_float");
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException(e);
            }
        };

        responseProcessor.processResponseAsync(request, response, responseContext, listener);
    }

    /**
     * Tests the case where an exception occurs during prediction, and the `ignoreFailure` flag is set to false.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponsePredictionException() throws Exception {
        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            null,
            null,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            true,
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            false
        );

        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(5, true, fieldName);

        when(client.execute(any(), any())).thenThrow(new RuntimeException("Prediction Failed"));
        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                throw new RuntimeException("error handling not properly.");
            }

            @Override
            public void onFailure(Exception e) {
                assertEquals("Prediction Failed", e.getMessage());
            }
        };

        responseProcessor.processResponseAsync(request, response, responseContext, listener);
    }

    /**
     * Tests the case where an onFailure occurs during prediction, and the `ignoreFailure` flag is set to false.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponsePredictionFailed() throws Exception {
        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            null,
            null,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            true,
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            false
        );

        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(5, true, fieldName);

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onFailure(new RuntimeException("Prediction Failed"));
            return null;
        }).when(client).execute(any(), any(), any());

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                throw new RuntimeException("error handling not properly.");
            }

            @Override
            public void onFailure(Exception e) {
                assertEquals("Prediction Failed", e.getMessage());
            }
        };

        responseProcessor.processResponseAsync(request, response, responseContext, listener);
    }

    /**
     * Tests the case where an exception occurs during prediction, but the `ignoreFailure` flag is set to true.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponsePredictionExceptionIgnoreFailure() throws Exception {
        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            null,
            null,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            true,
            true,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            false
        );

        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(5, true, fieldName);

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onFailure(new RuntimeException("Prediction Failed"));
            return null;
        }).when(client).execute(any(), any(), any());

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                assertEquals(response, newSearchResponse);
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException("error handling not properly.");
            }
        };

        responseProcessor.processResponseAsync(request, response, responseContext, listener);
    }

    /**
     * Tests the case where there are no hits in the search response.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseEmptyHit() throws Exception {
        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            null,
            null,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            true,
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            false
        );

        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(0, true, fieldName);

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                assertEquals(response, newSearchResponse);
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException(e);
            }
        };

        responseProcessor.processResponseAsync(request, response, responseContext, listener);
    }

    /**
     * Tests the case where there are no hits in the search response.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseHitWithNoSource() throws Exception {
        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            null,
            null,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            true,
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            false
        );

        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponseNoSource(0, true, fieldName);

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                assertEquals(response, newSearchResponse);
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException(e);
            }
        };

        responseProcessor.processResponseAsync(request, response, responseContext, listener);
    }

    /**
     * Tests create processor with one_to_one is true
     * with output_maps in one to one prediction
     * Exceptions happen when replaceHits to be one Hit Response
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseOneToOneMadeOneHitResponseExceptions() throws Exception {

        String newDocumentField = "text_embedding";
        String modelOutputField = "response";
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newDocumentField, modelOutputField);
        outputMap.add(output);

        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            null,
            outputMap,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            false,
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            true
        );

        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(5, true, fieldName);

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                throw new RuntimeException("error handling not properly.");
            }

            @Override
            public void onFailure(Exception e) {
                assertEquals("Mock exception", e.getMessage());
            }
        };

        SearchResponse mockResponse = mock(SearchResponse.class);
        SearchHits searchHits = response.getHits();
        when(mockResponse.getHits()).thenReturn(searchHits);
        RuntimeException mockException = new RuntimeException("Mock exception");
        AtomicInteger callCount = new AtomicInteger(0);
        when(mockResponse.getAggregations()).thenAnswer(invocation -> {
            int count = callCount.getAndIncrement();
            if (count < 6) {
                return null;
            } else {
                throw mockException;
            }
        });
        when(mockResponse.getTook()).thenReturn(TimeValue.timeValueNanos(10));
        responseProcessor.processResponseAsync(request, mockResponse, responseContext, listener);
    }

    /**
     * Tests create processor with one_to_one is true
     * with output_maps in one to one prediction
     * Exceptions happen when replaceHits  and ignoreFailure return original response
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseOneToOneMadeOneHitResponseExceptionsIgnoreFailure() throws Exception {

        String newDocumentField = "text_embedding";
        String modelOutputField = "response";
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newDocumentField, modelOutputField);
        outputMap.add(output);

        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            null,
            outputMap,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            false,
            true,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            true
        );

        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(5, true, fieldName);

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                assertEquals(newSearchResponse.getHits().getHits().length, 5);
                assertEquals(newSearchResponse.getHits().getHits(), response.getHits().getHits());
                assertNull(newSearchResponse.getHits().getHits()[0].getSourceAsMap().get(newDocumentField));
                assertNull(newSearchResponse.getHits().getHits()[1].getSourceAsMap().get(newDocumentField));
                assertNull(newSearchResponse.getHits().getHits()[2].getSourceAsMap().get(newDocumentField));
                assertNull(newSearchResponse.getHits().getHits()[3].getSourceAsMap().get(newDocumentField));
                assertNull(newSearchResponse.getHits().getHits()[4].getSourceAsMap().get(newDocumentField));
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException("error handling not properly.");
            }

        };

        SearchResponse mockResponse = mock(SearchResponse.class);
        SearchHits searchHits = response.getHits();
        when(mockResponse.getHits()).thenReturn(searchHits);
        RuntimeException mockException = new RuntimeException("Mock exception");
        when(mockResponse.getAggregations()).thenThrow(mockException);

        responseProcessor.processResponseAsync(request, mockResponse, responseContext, listener);
    }

    /**
     * Tests create processor with one_to_one is true
     * with output_maps in one to one prediction
     * Exceptions happen when replaceHits
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testProcessResponseOneToOneCombinedHitsExceptions() throws Exception {

        String newDocumentField = "text_embedding";
        String modelOutputField = "response";
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newDocumentField, modelOutputField);
        outputMap.add(output);

        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            null,
            outputMap,
            null,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            false,
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            true
        );

        SearchRequest request = getSearchRequest();
        String fieldName = "text";
        SearchResponse response = getSearchResponse(5, true, fieldName);

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                throw new RuntimeException("error handling not properly.");
            }

            @Override
            public void onFailure(Exception e) {
                assertEquals("Failed to process response: Mock exception", e.getMessage());
            }
        };

        SearchResponse mockResponse = mock(SearchResponse.class);
        SearchHits searchHits = response.getHits();
        when(mockResponse.getHits()).thenReturn(searchHits);
        RuntimeException mockException = new RuntimeException("Mock exception");
        AtomicInteger callCount = new AtomicInteger(0);
        when(mockResponse.getAggregations()).thenAnswer(invocation -> {
            int count = callCount.getAndIncrement();
            // every time getting one Hit Response will call get Aggregations two times ,
            // the 12th time is used for combine hits
            if (count < 12) {
                return null;
            } else {
                throw mockException;
            }
        });
        when(mockResponse.getTook()).thenReturn(TimeValue.timeValueNanos(10));
        responseProcessor.processResponseAsync(request, mockResponse, responseContext, listener);
    }

    /**
     * Tests the processResponseAsync method when the input is a regular SearchResponse.
     *
     * This test verifies that when a regular SearchResponse is passed to the method,
     * it attempts to create a new MLInferenceSearchResponse object.
     */
    @Test
    public void testProcessResponseAsync_WithRegularSearchResponse() {
        String modelInputField = "inputs";
        String originalDocumentField = "text";
        String newDocumentField = "text_embedding";
        String modelOutputField = "response";

        SearchResponse response = getSearchResponse(5, true, originalDocumentField);
        Map<String, Object> params = new HashMap<>();
        params.put("llm_response", "answer");
        MLInferenceSearchResponse mLInferenceSearchResponse = new MLInferenceSearchResponse(
            params,
            response.getInternalResponse(),
            response.getScrollId(),
            response.getTotalShards(),
            response.getSuccessfulShards(),
            response.getSkippedShards(),
            response.getSuccessfulShards(),
            response.getShardFailures(),
            response.getClusters()
        );

        MLInferenceSearchResponseProcessor responseProcessor = getMlInferenceSearchResponseProcessorSinglePairMapping(
            modelOutputField,
            modelInputField,
            originalDocumentField,
            newDocumentField,
            false,
            false,
            false
        );
        SearchRequest request = getSearchRequest();
        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(ImmutableMap.of("response", Arrays.asList(0.0, 1.0, 2.0, 3.0, 4.0)))
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                MLInferenceSearchResponse responseAfterProcessor = (MLInferenceSearchResponse) newSearchResponse;
                assertEquals(responseAfterProcessor.getHits().getHits().length, 5);
                assertEquals(responseAfterProcessor.getHits().getHits()[0].getSourceAsMap().get("text_embedding"), 0.0);
                assertEquals(responseAfterProcessor.getHits().getHits()[1].getSourceAsMap().get("text_embedding"), 1.0);
                assertEquals(responseAfterProcessor.getHits().getHits()[2].getSourceAsMap().get("text_embedding"), 2.0);
                assertEquals(responseAfterProcessor.getHits().getHits()[3].getSourceAsMap().get("text_embedding"), 3.0);
                assertEquals(responseAfterProcessor.getHits().getHits()[4].getSourceAsMap().get("text_embedding"), 4.0);
                assertEquals(responseAfterProcessor.getParams(), params);
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException(e);
            }
        };

        responseProcessor.processResponseAsync(request, mLInferenceSearchResponse, responseContext, listener);

    }

    /**
     * Tests the processResponseAsync method when the input is already an MLInferenceSearchResponse.
     *
     * This test verifies that when an MLInferenceSearchResponse is passed to the method,
     * and the params is being passed over
     */
    @Test
    public void testProcessResponseAsync_WithMLInferenceSearchResponse() {
        String modelInputField = "inputs";
        String originalDocumentField = "text";
        String newDocumentField = "text_embedding";
        String modelOutputField = "response";

        SearchResponse response = getSearchResponse(5, true, originalDocumentField);
        Map<String, Object> params = new HashMap<>();
        params.put("llm_response", "answer");
        MLInferenceSearchResponse mLInferenceSearchResponse = new MLInferenceSearchResponse(
            params,
            response.getInternalResponse(),
            response.getScrollId(),
            response.getTotalShards(),
            response.getSuccessfulShards(),
            response.getSkippedShards(),
            response.getSuccessfulShards(),
            response.getShardFailures(),
            response.getClusters()
        );

        MLInferenceSearchResponseProcessor responseProcessor = getMlInferenceSearchResponseProcessorSinglePairMapping(
            modelOutputField,
            modelInputField,
            originalDocumentField,
            newDocumentField,
            false,
            false,
            false
        );
        SearchRequest request = getSearchRequest();
        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(ImmutableMap.of("response", Arrays.asList(0.0, 1.0, 2.0, 3.0, 4.0)))
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                MLInferenceSearchResponse responseAfterProcessor = (MLInferenceSearchResponse) newSearchResponse;
                assertEquals(responseAfterProcessor.getHits().getHits().length, 5);
                assertEquals(responseAfterProcessor.getHits().getHits()[0].getSourceAsMap().get("text_embedding"), 0.0);
                assertEquals(responseAfterProcessor.getHits().getHits()[1].getSourceAsMap().get("text_embedding"), 1.0);
                assertEquals(responseAfterProcessor.getHits().getHits()[2].getSourceAsMap().get("text_embedding"), 2.0);
                assertEquals(responseAfterProcessor.getHits().getHits()[3].getSourceAsMap().get("text_embedding"), 3.0);
                assertEquals(responseAfterProcessor.getHits().getHits()[4].getSourceAsMap().get("text_embedding"), 4.0);
                assertEquals(responseAfterProcessor.getParams(), params);
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException(e);
            }
        };

        responseProcessor.processResponseAsync(request, mLInferenceSearchResponse, responseContext, listener);

    }

    /**
     * Tests the processResponseAsync method when the input is already an MLInferenceSearchResponse.
     *
     * This test verifies that when an MLInferenceSearchResponse is passed to the method,
     * and the params is being passed over and new params is added
     */
    @Test
    public void testProcessResponseAsync_WriteExtensionToMLInferenceSearchResponse() {
        String documentField = "text";
        String modelInputField = "context";
        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put(modelInputField, documentField);
        inputMap.add(input);

        String newDocumentField = "ext.ml_inference.summary";
        String modelOutputField = "response";
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newDocumentField, modelOutputField);
        outputMap.add(output);
        Map<String, String> modelConfig = new HashMap<>();
        modelConfig
            .put(
                "prompt",
                "\\n\\nHuman: You are a professional data analyst. You will always answer question based on the given context first. If the answer is not directly shown in the context, you will analyze the data and find the answer. If you don't know the answer, just say I don't know. Context: ${parameters.context}. \\n\\n Human: please summarize the documents \\n\\n Assistant:"
            );
        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            inputMap,
            outputMap,
            modelConfig,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            false,
            "remote",
            false,
            false,
            false,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            false
        );
        SearchResponse response = getSearchResponse(5, true, documentField);
        Map<String, Object> params = new HashMap<>();
        params.put("llm_response", "answer");
        MLInferenceSearchResponse mLInferenceSearchResponse = new MLInferenceSearchResponse(
            params,
            response.getInternalResponse(),
            response.getScrollId(),
            response.getTotalShards(),
            response.getSuccessfulShards(),
            response.getSkippedShards(),
            response.getSuccessfulShards(),
            response.getShardFailures(),
            response.getClusters()
        );

        SearchRequest request = getSearchRequest();
        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", "there is 1 value")).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        ActionListener<SearchResponse> listener = new ActionListener<>() {
            @Override
            public void onResponse(SearchResponse newSearchResponse) {
                MLInferenceSearchResponse responseAfterProcessor = (MLInferenceSearchResponse) newSearchResponse;
                assertEquals(responseAfterProcessor.getHits().getHits().length, 5);
                Map<String, Object> newParams = new HashMap<>();
                newParams.put("llm_response", "answer");
                newParams.put("summary", "there is 1 value");
                assertEquals(responseAfterProcessor.getParams(), newParams);
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException(e);
            }
        };

        responseProcessor.processResponseAsync(request, mLInferenceSearchResponse, responseContext, listener);

    }

    private static SearchRequest getSearchRequest() {
        QueryBuilder incomingQuery = new TermQueryBuilder("text", "foo");
        SearchSourceBuilder source = new SearchSourceBuilder().query(incomingQuery).size(5).sort("text");
        SearchRequest request = new SearchRequest().source(source);
        return request;
    }

    private static Map<String, Object> generateInferenceResult(String response) {
        Map<String, Object> inferenceResult = new HashMap<>();
        List<Map<String, Object>> inferenceResults = new ArrayList<>();

        Map<String, Object> outputMap = new HashMap<>();
        List<Map<String, Object>> outputs = new ArrayList<>();

        Map<String, Object> responseOutput = new HashMap<>();
        Map<String, String> dataAsMap = new HashMap<>();
        dataAsMap.put("response", response);
        responseOutput.put("dataAsMap", dataAsMap);

        outputs.add(responseOutput);
        outputMap.put("output", outputs);

        inferenceResults.add(outputMap);
        inferenceResult.put("inference_results", inferenceResults);

        return inferenceResult;
    }

    /**
     * Helper method to create an instance of the MLInferenceSearchResponseProcessor with the specified parameters in
     * single pair of input and output mapping.
     *
     * @param modelInputField       the model input field name
     * @param originalDocumentField the original query field name
     * @param newDocumentField      the new document field name
     * @param override              the flag indicating whether to override existing document field
     * @param ignoreFailure         the flag indicating whether to ignore failures or not
     * @param ignoreMissing         the flag indicating whether to ignore missing fields or not
     * @return an instance of the MLInferenceSearchResponseProcessor
     */
    private MLInferenceSearchResponseProcessor getMlInferenceSearchResponseProcessorSinglePairMapping(
        String modelOutputField,
        String modelInputField,
        String originalDocumentField,
        String newDocumentField,
        boolean override,
        boolean ignoreFailure,
        boolean ignoreMissing
    ) {
        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put(modelInputField, originalDocumentField);
        inputMap.add(input);

        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put(newDocumentField, modelOutputField);
        outputMap.add(output);
        Map<String, String> model_config = new HashMap<>();
        model_config.put("truncate_result", "false");
        MLInferenceSearchResponseProcessor responseProcessor = new MLInferenceSearchResponseProcessor(
            "model1",
            inputMap,
            outputMap,
            model_config,
            DEFAULT_MAX_PREDICTION_TASKS,
            PROCESSOR_TAG,
            DESCRIPTION,
            ignoreMissing,
            "remote",
            false,
            ignoreFailure,
            override,
            "{ \"parameters\": ${ml_inference.parameters} }",
            client,
            TEST_XCONTENT_REGISTRY_FOR_QUERY,
            false
        );
        return responseProcessor;
    }

    private SearchResponse getSearchResponse(int size, boolean includeMapping, String fieldName) {
        SearchHit[] hits = new SearchHit[size];
        for (int i = 0; i < size; i++) {
            Map<String, DocumentField> searchHitFields = new HashMap<>();
            if (includeMapping) {
                searchHitFields.put(fieldName, new DocumentField("value " + i, Collections.emptyList()));
            }
            searchHitFields.put(fieldName, new DocumentField("value " + i, Collections.emptyList()));
            hits[i] = new SearchHit(i, "doc " + i, searchHitFields, Collections.emptyMap());
            hits[i].sourceRef(new BytesArray("{ \"" + fieldName + "\" : \"value " + i + "\" }"));
            hits[i].score(i);
        }
        SearchHits searchHits = new SearchHits(hits, new TotalHits(size * 2L, TotalHits.Relation.EQUAL_TO), size);
        SearchResponseSections searchResponseSections = new SearchResponseSections(searchHits, null, null, false, false, null, 0);
        return new SearchResponse(searchResponseSections, null, 1, 1, 0, 10, null, null);
    }

    private SearchResponse getSearchResponseNoSource(int size, boolean includeMapping, String fieldName) {
        SearchHit[] hits = new SearchHit[size];
        for (int i = 0; i < size; i++) {
            Map<String, DocumentField> searchHitFields = new HashMap<>();
            if (includeMapping) {
                searchHitFields.put(fieldName, new DocumentField("value " + i, Collections.emptyList()));
            }
            searchHitFields.put(fieldName, new DocumentField("value " + i, Collections.emptyList()));
            hits[i] = new SearchHit(i, "doc " + i, null, Collections.emptyMap());
            hits[i].sourceRef(null);
            hits[i].score(i);
        }
        SearchHits searchHits = new SearchHits(hits, new TotalHits(size * 2L, TotalHits.Relation.EQUAL_TO), size);
        SearchResponseSections searchResponseSections = new SearchResponseSections(searchHits, null, null, false, false, null, 0);
        return new SearchResponse(searchResponseSections, null, 1, 1, 0, 10, null, null);
    }

    private SearchResponse getSearchResponseMissingField(int size, boolean includeMapping, String fieldName) {
        SearchHit[] hits = new SearchHit[size];
        for (int i = 0; i < size; i++) {

            if (i == (size % 3)) {
                Map<String, DocumentField> searchHitFields = new HashMap<>();
                if (includeMapping) {
                    searchHitFields.put(fieldName + "Missing", new DocumentField("value " + i, Collections.emptyList()));
                }
                searchHitFields.put(fieldName + "Missing", new DocumentField("value " + i, Collections.emptyList()));
                hits[i] = new SearchHit(i, "doc " + i, searchHitFields, Collections.emptyMap());
                hits[i].sourceRef(new BytesArray("{ \"" + fieldName + "Missing" + "\" : \"value " + i + "\" }"));
                hits[i].score(i);
            } else {
                Map<String, DocumentField> searchHitFields = new HashMap<>();
                if (includeMapping) {
                    searchHitFields.put(fieldName, new DocumentField("value " + i, Collections.emptyList()));
                }
                searchHitFields.put(fieldName, new DocumentField("value " + i, Collections.emptyList()));
                hits[i] = new SearchHit(i, "doc " + i, searchHitFields, Collections.emptyMap());
                hits[i].sourceRef(new BytesArray("{ \"" + fieldName + "\" : \"value " + i + "\" }"));
                hits[i].score(i);
            }
        }
        SearchHits searchHits = new SearchHits(hits, new TotalHits(size * 2L, TotalHits.Relation.EQUAL_TO), size);
        SearchResponseSections searchResponseSections = new SearchResponseSections(searchHits, null, null, false, false, null, 0);
        return new SearchResponse(searchResponseSections, null, 1, 1, 0, 10, null, null);
    }

    private SearchResponse getSearchResponseTwoFields(int size, boolean includeMapping, String fieldName, String fieldName1) {
        SearchHit[] hits = new SearchHit[size];
        for (int i = 0; i < size; i++) {
            Map<String, DocumentField> searchHitFields = new HashMap<>();
            if (includeMapping) {
                searchHitFields.put(fieldName, new DocumentField("value " + i, Collections.emptyList()));
                searchHitFields.put(fieldName1, new DocumentField("value " + i, Collections.emptyList()));
            }
            searchHitFields.put(fieldName, new DocumentField("value " + i, Collections.emptyList()));
            searchHitFields.put(fieldName1, new DocumentField("value " + i, Collections.emptyList()));
            hits[i] = new SearchHit(i, "doc " + i, searchHitFields, Collections.emptyMap());
            hits[i]
                .sourceRef(
                    new BytesArray(
                        "{ \"" + fieldName + "\" : \"value " + i + "\", " + "\"" + fieldName1 + "\" : \"value " + i + "\" " + " }"
                    )
                );
            hits[i].score(i);
        }
        SearchHits searchHits = new SearchHits(hits, new TotalHits(size * 2L, TotalHits.Relation.EQUAL_TO), size);
        SearchResponseSections searchResponseSections = new SearchResponseSections(searchHits, null, null, false, false, null, 0);
        return new SearchResponse(searchResponseSections, null, 1, 1, 0, 10, null, null);
    }

    private MLInferenceSearchResponseProcessor.Factory factory;

    @Mock
    private NamedXContentRegistry xContentRegistry;

    @Before
    public void init() {
        factory = new MLInferenceSearchResponseProcessor.Factory(client, xContentRegistry);
    }

    /**
     * Tests the creation of the MLInferenceSearchResponseProcessor with required fields.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testCreateRequiredFields() throws Exception {
        Map<String, Object> config = new HashMap<>();
        config.put(MODEL_ID, "model1");
        String processorTag = randomAlphaOfLength(10);
        MLInferenceSearchResponseProcessor MLInferenceSearchResponseProcessor = factory
            .create(Collections.emptyMap(), processorTag, null, false, config, null);
        assertNotNull(MLInferenceSearchResponseProcessor);
        assertEquals(MLInferenceSearchResponseProcessor.getTag(), processorTag);
        assertEquals(MLInferenceSearchResponseProcessor.getType(), MLInferenceSearchResponseProcessor.TYPE);
    }

    /**
     * Tests the creation of the MLInferenceSearchResponseProcessor for a local model.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testCreateLocalModelProcessor() throws Exception {
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
        MLInferenceSearchResponseProcessor MLInferenceSearchResponseProcessor = factory
            .create(Collections.emptyMap(), processorTag, null, false, config, null);
        assertNotNull(MLInferenceSearchResponseProcessor);
        assertEquals(MLInferenceSearchResponseProcessor.getTag(), processorTag);
        assertEquals(MLInferenceSearchResponseProcessor.getType(), MLInferenceSearchResponseProcessor.TYPE);
    }

    /**
     * The model input field is required for using a local model
     * when missing the model input field, expected to throw Exceptions
     *
     * @throws Exception if an error occurs during the test
     */
    public void testCreateLocalModelProcessorMissingModelInputField() throws Exception {
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
            MLInferenceSearchResponseProcessor MLInferenceSearchResponseProcessor = factory
                .create(Collections.emptyMap(), processorTag, null, false, config, null);
            assertNotNull(MLInferenceSearchResponseProcessor);
        } catch (Exception e) {
            assertEquals(e.getMessage(), "Please provide model input when using a local model in ML Inference Processor");
        }
    }

    /**
     * Tests the case where the `model_id` field is missing in the configuration, and an exception is expected.
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
        output3.put("hashtvg_embedding", "response");
        outputMap.add(output3);
        config.put(OUTPUT_MAP, outputMap);
        config.put(MAX_PREDICTION_TASKS, 2);
        String processorTag = randomAlphaOfLength(10);

        try {
            factory.create(Collections.emptyMap(), processorTag, null, false, config, null);
        } catch (IllegalArgumentException e) {
            assertEquals(
                e.getMessage(),
                "when output_maps and input_maps are provided, their length needs to match. The input_maps is in length of 2, while output_maps is in the length of 3. Please adjust mappings."
            );

        }
    }

    /**
         * Tests the creation of the MLInferenceSearchResponseProcessor with optional fields.
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

        MLInferenceSearchResponseProcessor MLInferenceSearchResponseProcessor = factory
            .create(Collections.emptyMap(), processorTag, null, false, config, null);
        assertNotNull(MLInferenceSearchResponseProcessor);
        assertEquals(MLInferenceSearchResponseProcessor.getTag(), processorTag);
        assertEquals(MLInferenceSearchResponseProcessor.getType(), MLInferenceSearchResponseProcessor.TYPE);
    }

    /**
     * Tests the case where output map try to write to extension and one to one inference  is true
     * and an exception is expected.
     *
     * @throws Exception if an error occurs during the test
     */
    public void testWriteToExtensionAndOneToOne() throws Exception {
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
        output2.put("ext.inference.hashtag_embedding", "response");
        outputMap.add(output2);
        config.put(OUTPUT_MAP, outputMap);
        config.put(ONE_TO_ONE, true);
        String processorTag = randomAlphaOfLength(10);

        try {
            factory.create(Collections.emptyMap(), processorTag, null, false, config, null);
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "");

        }
    }
}
