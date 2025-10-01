/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.processor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.common.utils.StringUtils.toJson;
import static org.opensearch.ml.processor.MLInferenceIngestProcessor.DEFAULT_OUTPUT_FIELD_NAME;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import org.junit.Assert;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ingest.IngestDocument;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.MLResultDataType;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.repackage.com.google.common.collect.ImmutableMap;
import org.opensearch.script.ScriptService;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.client.Client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;

public class MLInferenceIngestProcessorTests extends OpenSearchTestCase {

    @Mock
    private Client client;
    @Mock
    private ScriptService scriptService;
    @Mock
    private BiConsumer<IngestDocument, Exception> handler;

    @Mock
    NamedXContentRegistry xContentRegistry;
    private static final String PROCESSOR_TAG = "inference";
    private static final String DESCRIPTION = "inference_test";
    private IngestDocument ingestDocument;
    private IngestDocument nestedObjectIngestDocument;
    private ModelExecutor modelExecutor;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        Map<String, Object> sourceAndMetadata = new HashMap<>();
        sourceAndMetadata.put("key1", "value1");
        sourceAndMetadata.put("key2", "value2");
        ingestDocument = new IngestDocument(sourceAndMetadata, new HashMap<>());

        Map<String, Object> nestedObjectSourceAndMetadata = getNestedObjectWithAnotherNestedObjectSource();
        nestedObjectIngestDocument = new IngestDocument(nestedObjectSourceAndMetadata, new HashMap<>());
        modelExecutor = new ModelExecutor() {
        };

    }

    private MLInferenceIngestProcessor createMLInferenceProcessor(
        String modelId,
        List<Map<String, String>> inputMaps,
        List<Map<String, String>> outputMaps,
        Map<String, String> modelConfigMaps,
        boolean ignoreMissing,
        String functionName,
        boolean fullResponsePath,
        boolean ignoreFailure,
        boolean override,
        String modelInput
    ) {
        functionName = functionName != null ? functionName : "remote";
        modelInput = modelInput != null ? modelInput : "{ \"parameters\": ${ml_inference.parameters} }";

        return new MLInferenceIngestProcessor(
            modelId,
            inputMaps,
            outputMaps,
            modelConfigMaps,
            RANDOM_MULTIPLIER,
            PROCESSOR_TAG,
            DESCRIPTION,
            ignoreMissing,
            functionName,
            fullResponsePath,
            ignoreFailure,
            override,
            modelInput,
            scriptService,
            client,
            xContentRegistry
        );
    }

    public void testExecute_Exception() throws Exception {
        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "model1",
            null,
            null,
            null,
            false,
            "remote",
            false,
            false,
            false,
            null
        );
        try {
            IngestDocument document = processor.execute(ingestDocument);
        } catch (UnsupportedOperationException e) {
            assertEquals("this method should not get executed.", e.getMessage());
        }

    }

    /**
     * Models that use the parameters field need to have a valid NamedXContentRegistry object to create valid MLInputs. For example
     * <pre>
     * PUT   /_plugins/_ml/_predict/text_embedding/model_id
     *  {
     *     "parameters": {
     *         "content_type" : "query"
     *     },
     *     "text_docs" : ["what day is it today?"],
     *     "target_response" : ["sentence_embedding"]
     *   }
     * </pre>
     * These types of models like Local Asymmetric embedding models use the parameters field.
     * And as such we need to test that having the contentRegistry throws an exception as it can not
     * properly create a valid MLInput to perform prediction
     *
     * @implNote If you check the stack trace of the test you will see it tells you that it's a direct consequence of xContentRegistry being null
     */
    public void testExecute_xContentRegistryNullWithLocalModel_throwsException() throws Exception {
        // Set the registry to null and reset after exiting the test
        xContentRegistry = null;

        String localModelInput =
            "{ \"text_docs\": [\"What day is it today?\"],\"target_response\": [\"sentence_embedding\"], \"parameters\": { \"contentType\" : \"query\"} }";

        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "local_model_id",
            null,
            null,
            null,
            false,
            FunctionName.TEXT_EMBEDDING.toString(),
            false,
            false,
            false,
            localModelInput
        );
        try {
            String npeMessage =
                "Cannot invoke \"org.opensearch.ml.common.input.MLInput.setAlgorithm(org.opensearch.ml.common.FunctionName)\" because \"mlInput\" is null";

            processor.execute(ingestDocument, handler);
            verify(handler)
                .accept(
                    isNull(),
                    argThat(exception -> exception instanceof NullPointerException && exception.getMessage().equals(npeMessage))
                );
        } catch (Exception e) {
            assertEquals("this catch block should not get executed.", e.getMessage());
        }
        // reset to mocked object
        xContentRegistry = mock(NamedXContentRegistry.class);
    }

    /**
     * test nested object document with array of Map<String,String>
     */
    public void testExecute_nestedObjectStringDocumentSuccess() {

        List<Map<String, String>> inputMap = getInputMapsForNestedObjectChunks("chunks.*.chunk");

        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "model1",
            inputMap,
            null,
            null,
            true,
            "remote",
            false,
            false,
            false,
            null
        );
        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", Arrays.asList(1, 2, 3))).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        ArgumentCaptor<MLPredictionTaskRequest> argCaptor = ArgumentCaptor.forClass(MLPredictionTaskRequest.class);
        processor.execute(nestedObjectIngestDocument, handler);

        // match output documents
        Map<String, Object> sourceAndMetadata = getNestedObjectWithAnotherNestedObjectSource();
        sourceAndMetadata.put(DEFAULT_OUTPUT_FIELD_NAME, ImmutableMap.of("response", Arrays.asList(1, 2, 3)));
        IngestDocument ingestDocument1 = new IngestDocument(sourceAndMetadata, new HashMap<>());
        verify(handler).accept(eq(ingestDocument1), isNull());
        assertEquals(nestedObjectIngestDocument, ingestDocument1);

        // match model input
        verify(client, times(1)).execute(eq(MLPredictionTaskAction.INSTANCE), argCaptor.capture(), any());
        MLPredictionTaskRequest req = argCaptor.getValue();
        MLInput mlInput = req.getMlInput();
        RemoteInferenceInputDataSet inputDataSet = (RemoteInferenceInputDataSet) mlInput.getInputDataset();
        assertEquals(
            toJson(inputDataSet.getParameters()),
            "{\"inputs\":\"[{\\\"text\\\":[{\\\"chapter\\\":\\\"first chapter\\\",\\\"context\\\":\\\"this is first\\\"},{\\\"chapter\\\":\\\"first chapter\\\",\\\"context\\\":\\\"this is second\\\"}]},{\\\"text\\\":[{\\\"chapter\\\":\\\"second chapter\\\",\\\"context\\\":\\\"this is third\\\"},{\\\"chapter\\\":\\\"second chapter\\\",\\\"context\\\":\\\"this is fourth\\\"}]}]\"}"
        );

    }

    /**
     * test nested object document with array of Map<String,Object>,
     * the value Object is a Map<String,String>
     */
    public void testExecute_nestedObjectMapDocumentSuccess() throws IOException {
        List<Map<String, String>> inputMap = getInputMapsForNestedObjectChunks("chunks.*.chunk.text");

        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "model1",
            inputMap,
            null,
            null,
            true,
            "remote",
            false,
            false,
            false,
            null
        );
        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", Arrays.asList(1, 2, 3))).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        /**
         * Preview of sourceAndMetadata
         * {
         *   "chunks": [
         *     {
         *       "chunk": {
         *         "text": "this is first"
         *       }
         *     },
         *     {
         *       "chunk": {
         *         "text": "this is second"
         *       }
         *     }
         *   ]
         * }
         */
        ArrayList<Object> childDocuments = new ArrayList<>();
        Map<String, Object> childDocument1Text = new HashMap<>();
        childDocument1Text.put("text", "this is first");
        Map<String, Object> childDocument1 = new HashMap<>();
        childDocument1.put("chunk", childDocument1Text);

        Map<String, Object> childDocument2 = new HashMap<>();
        Map<String, Object> childDocument2Text = new HashMap<>();
        childDocument2Text.put("text", "this is second");
        childDocument2.put("chunk", childDocument2Text);

        childDocuments.add(childDocument1);
        childDocuments.add(childDocument2);

        Map<String, Object> sourceAndMetadata = new HashMap<>();
        sourceAndMetadata.put("chunks", childDocuments);

        ArgumentCaptor<MLPredictionTaskRequest> argCaptor = ArgumentCaptor.forClass(MLPredictionTaskRequest.class);

        IngestDocument nestedObjectIngestDocument = new IngestDocument(sourceAndMetadata, new HashMap<>());
        processor.execute(nestedObjectIngestDocument, handler);

        // match input dataset

        ArgumentCaptor<MLPredictionTaskRequest> argumentCaptor = ArgumentCaptor.forClass(MLPredictionTaskRequest.class);
        verify(client).execute(any(), argumentCaptor.capture(), any());

        Map<String, String> inputParameters = new HashMap<>();
        ArrayList<Object> embedding_text = new ArrayList<>();
        embedding_text.add("this is first");
        embedding_text.add("this is second");
        inputParameters.put("inputs", modelExecutor.toString(embedding_text));
        String modelInput = "{ \"parameters\": ${ml_inference.parameters} }";

        MLPredictionTaskRequest expectedRequest = (MLPredictionTaskRequest) modelExecutor
            .getMLModelInferenceRequest(xContentRegistry, inputParameters, null, inputParameters, "model1", "remote", modelInput);
        MLPredictionTaskRequest actualRequest = argumentCaptor.getValue();

        RemoteInferenceInputDataSet expectedRemoteInputDataset = (RemoteInferenceInputDataSet) expectedRequest
            .getMlInput()
            .getInputDataset();
        RemoteInferenceInputDataSet actualRemoteInputDataset = (RemoteInferenceInputDataSet) actualRequest.getMlInput().getInputDataset();

        assertEquals(expectedRemoteInputDataset.getParameters().get("inputs"), actualRemoteInputDataset.getParameters().get("inputs"));

        // match document
        sourceAndMetadata.put(DEFAULT_OUTPUT_FIELD_NAME, List.of(ImmutableMap.of("response", Arrays.asList(1, 2, 3))));
        IngestDocument ingestDocument1 = new IngestDocument(sourceAndMetadata, new HashMap<>());
        verify(handler).accept(eq(ingestDocument1), isNull());
        assertEquals(nestedObjectIngestDocument, ingestDocument1);

        // match model input
        verify(client, times(1)).execute(eq(MLPredictionTaskAction.INSTANCE), argCaptor.capture(), any());
        MLPredictionTaskRequest req = argCaptor.getValue();
        MLInput mlInput = req.getMlInput();
        RemoteInferenceInputDataSet inputDataSet = (RemoteInferenceInputDataSet) mlInput.getInputDataset();
        assertEquals(toJson(inputDataSet.getParameters()), "{\"inputs\":\"[\\\"this is first\\\",\\\"this is second\\\"]\"}");
    }

    public void testExecute_jsonPathWithMissingLeaves() {

        Map<String, Object> sourceObject = getNestedObjectWithAnotherNestedObjectSource();
        sourceObject.remove("chunks.1.chunk.text.0.context", "this is third");

        Configuration suppressExceptionConfiguration = Configuration
            .builder()
            .options(Option.DEFAULT_PATH_LEAF_TO_NULL, Option.SUPPRESS_EXCEPTIONS)
            .build();
        Object jsonObject = JsonPath.parse(sourceObject).json();
        JsonPath.parse(jsonObject).delete("$.chunks[1].chunk.text[0].context");
        ArrayList<Object> value = JsonPath.using(suppressExceptionConfiguration).parse(jsonObject).read("chunks.*.chunk.text.*.context");

        assertEquals(value.size(), 4);
        assertEquals(value.get(0), "this is first");
        assertEquals(value.get(1), "this is second");
        assertNull(value.get(2)); // confirm the missing leave is null
        assertEquals(value.get(3), "this is fourth");
    }

    /**
     * test nested object document with array of Map<String,Object>,
     * the value Object is also a nested object,
     */
    public void testExecute_nestedObjectAndNestedObjectDocumentOutputInOneFieldSuccess() throws IOException {
        List<Map<String, String>> inputMap = getInputMapsForNestedObjectChunks("chunks.*.chunk.text.*.context");

        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "model1",
            inputMap,
            null,
            null,
            true,
            "remote",
            false,
            false,
            false,
            null
        );
        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", Arrays.asList(1, 2, 3, 4))).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        Map<String, Object> sourceAndMetadata = getNestedObjectWithAnotherNestedObjectSource();

        IngestDocument nestedObjectIngestDocument = new IngestDocument(sourceAndMetadata, new HashMap<>());
        processor.execute(nestedObjectIngestDocument, handler);

        // match input dataset
        ArgumentCaptor<MLPredictionTaskRequest> argumentCaptor = ArgumentCaptor.forClass(MLPredictionTaskRequest.class);
        verify(client).execute(any(), argumentCaptor.capture(), any());

        Map<String, String> inputParameters = new HashMap<>();
        ArrayList<Object> embedding_text = new ArrayList<>();
        embedding_text.add("this is first");
        embedding_text.add("this is second");
        embedding_text.add("this is third");
        embedding_text.add("this is fourth");
        inputParameters.put("inputs", modelExecutor.toString(embedding_text));
        String modelInput = "{ \"parameters\": ${ml_inference.parameters} }";

        MLPredictionTaskRequest expectedRequest = (MLPredictionTaskRequest) modelExecutor
            .getMLModelInferenceRequest(xContentRegistry, inputParameters, null, inputParameters, "model1", "remote", modelInput);
        MLPredictionTaskRequest actualRequest = argumentCaptor.getValue();

        RemoteInferenceInputDataSet expectedRemoteInputDataset = (RemoteInferenceInputDataSet) expectedRequest
            .getMlInput()
            .getInputDataset();
        RemoteInferenceInputDataSet actualRemoteInputDataset = (RemoteInferenceInputDataSet) actualRequest.getMlInput().getInputDataset();

        assertEquals(expectedRemoteInputDataset.getParameters().get("inputs"), actualRemoteInputDataset.getParameters().get("inputs"));

        // match document
        sourceAndMetadata.put(DEFAULT_OUTPUT_FIELD_NAME, List.of(ImmutableMap.of("response", Arrays.asList(1, 2, 3, 4))));
        IngestDocument ingestDocument1 = new IngestDocument(sourceAndMetadata, new HashMap<>());
        verify(handler).accept(eq(ingestDocument1), isNull());
        assertEquals(nestedObjectIngestDocument, ingestDocument1);

    }

    public void testExecute_nestedObjectAndNestedObjectDocumentOutputInArraySuccess() {
        List<Map<String, String>> inputMap = getInputMapsForNestedObjectChunks("chunks.*.chunk.text.*.context");

        List<Map<String, String>> outputMap = getOutputMapsForNestedObjectChunks();
        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "model1",
            inputMap,
            outputMap,
            null,
            true,
            "remote",
            false,
            false,
            false,
            null
        );
        ArrayList<List<Integer>> modelPredictionOutput = new ArrayList<>();
        modelPredictionOutput.add(Arrays.asList(1));
        modelPredictionOutput.add(Arrays.asList(2));
        modelPredictionOutput.add(Arrays.asList(3));
        modelPredictionOutput.add(Arrays.asList(4));
        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", modelPredictionOutput)).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        Map<String, Object> sourceAndMetadata = getNestedObjectWithAnotherNestedObjectSource();

        ArgumentCaptor<MLPredictionTaskRequest> argCaptor = ArgumentCaptor.forClass(MLPredictionTaskRequest.class);
        IngestDocument nestedObjectIngestDocument = new IngestDocument(sourceAndMetadata, new HashMap<>());
        processor.execute(nestedObjectIngestDocument, handler);

        // match output dataset
        assertEquals(nestedObjectIngestDocument.getFieldValue("chunks.0.chunk.text.0.embedding", Object.class), Arrays.asList(1));
        assertEquals(nestedObjectIngestDocument.getFieldValue("chunks.0.chunk.text.1.embedding", Object.class), Arrays.asList(2));
        assertEquals(nestedObjectIngestDocument.getFieldValue("chunks.1.chunk.text.0.embedding", Object.class), Arrays.asList(3));
        assertEquals(nestedObjectIngestDocument.getFieldValue("chunks.1.chunk.text.1.embedding", Object.class), Arrays.asList(4));

        // match model input
        verify(client, times(1)).execute(eq(MLPredictionTaskAction.INSTANCE), argCaptor.capture(), any());
        MLPredictionTaskRequest req = argCaptor.getValue();
        MLInput mlInput = req.getMlInput();
        RemoteInferenceInputDataSet inputDataSet = (RemoteInferenceInputDataSet) mlInput.getInputDataset();
        assertEquals(
            toJson(inputDataSet.getParameters()),
            "{\"inputs\":\"[\\\"this is first\\\",\\\"this is second\\\",\\\"this is third\\\",\\\"this is fourth\\\"]\"}"
        );

    }

    public void testExecute_nestedObjectAndNestedObjectDocumentOutputInArrayMissingLeaveSuccess() {
        List<Map<String, String>> inputMap = getInputMapsForNestedObjectChunks("chunks.*.chunk.text.*.context");

        List<Map<String, String>> outputMap = getOutputMapsForNestedObjectChunks();

        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "model1",
            inputMap,
            outputMap,
            null,
            true,
            "remote",
            false,
            false,
            false,
            null
        );
        ArrayList<List<Integer>> modelPredictionOutput = new ArrayList<>();
        modelPredictionOutput.add(Arrays.asList(1));
        modelPredictionOutput.add(Arrays.asList(2));
        modelPredictionOutput.add(null);
        modelPredictionOutput.add(Arrays.asList(4));
        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", modelPredictionOutput)).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        Map<String, Object> sourceAndMetadata = getNestedObjectWithAnotherNestedObjectSource();

        Object jsonObject = JsonPath.parse(sourceAndMetadata).json();
        JsonPath.parse(jsonObject).delete("$.chunks[1].chunk.text[0].context");

        IngestDocument nestedObjectIngestDocument = new IngestDocument((Map<String, Object>) jsonObject, new HashMap<>());
        processor.execute(nestedObjectIngestDocument, handler);

        // match output dataset
        assertEquals(nestedObjectIngestDocument.getFieldValue("chunks.0.chunk.text", ArrayList.class).size(), 2);
        assertEquals(nestedObjectIngestDocument.getFieldValue("chunks.1.chunk.text", ArrayList.class).size(), 2);
        assertEquals(nestedObjectIngestDocument.getFieldValue("chunks.0.chunk.text.0.embedding", Object.class), Arrays.asList(1));
        assertEquals(nestedObjectIngestDocument.getFieldValue("chunks.0.chunk.text.1.embedding", Object.class), Arrays.asList(2));
        assertNull(((ArrayList<?>) nestedObjectIngestDocument.getFieldValue("chunks.1.chunk.text.0.embedding", Object.class))); // missing
        assertEquals(nestedObjectIngestDocument.getFieldValue("chunks.1.chunk.text.1.embedding", Object.class), Arrays.asList(4));
    }

    public void testExecute_InferenceException() {
        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "model1",
            null,
            null,
            null,
            false,
            "remote",
            false,
            false,
            false,
            null
        );
        when(client.execute(any(), any())).thenThrow(new RuntimeException("Executing Model failed with exception"));
        try {
            processor.execute(ingestDocument, handler);
        } catch (RuntimeException e) {
            assertEquals("Executing Model failed with exception", e.getMessage());
        }
    }

    public void testExecute_InferenceOnFailure() {
        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "model1",
            null,
            null,
            null,
            false,
            "remote",
            false,
            false,
            false,
            null
        );
        RuntimeException inferenceFailure = new RuntimeException("Executing Model failed with exception");

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onFailure(inferenceFailure);
            return null;
        }).when(client).execute(any(), any(), any());
        processor.execute(ingestDocument, handler);

        verify(handler).accept(eq(null), eq(inferenceFailure));

    }

    public void testExecute_AppendFieldValueExceptionOnResponse() throws Exception {
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        String originalOutPutFieldName = "response1";
        output.put("text_embedding", originalOutPutFieldName);
        outputMap.add(output);
        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "model1",
            null,
            outputMap,
            null,
            false,
            "remote",
            false,
            false,
            false,
            null
        );

        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", Arrays.asList(1, 2, 3))).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        try {
            processor.execute(ingestDocument, handler);

        } catch (IllegalArgumentException e) {
            assertEquals("model inference output can not find field name: " + originalOutPutFieldName, e.getMessage());
        }

    }

    public void testExecute_whenInputFieldNotFound_ExceptionWithIgnoreMissingFalse() {
        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        String documentFieldPath = "text";
        input.put("inputs", documentFieldPath);
        inputMap.add(input);
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put("text_embedding", "response");
        outputMap.add(output);
        Map<String, String> model_config = new HashMap<>();
        model_config.put("position_embedding_type", "absolute");

        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "model1",
            inputMap,
            outputMap,
            model_config,
            false,
            "remote",
            false,
            false,
            false,
            null
        );

        try {
            processor.execute(ingestDocument, handler);
        } catch (IllegalArgumentException e) {
            assertEquals("field name in input_map: [" + documentFieldPath + "] doesn't exist", e.getMessage());
        }

    }

    public void testExecute_whenInputFieldNotFound_SuccessWithIgnoreMissingTrue() {
        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        String documentFieldPath = "text";
        input.put("inputs", documentFieldPath);
        inputMap.add(input);
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put("text_embedding", "response");
        outputMap.add(output);
        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "model1",
            inputMap,
            outputMap,
            null,
            true,
            "remote",
            false,
            false,
            false,
            null
        );

        processor.execute(ingestDocument, handler);
    }

    public void testExecute_localModelInputFieldNotFound_SuccessWithIgnoreMissingTrue() {
        List<Map<String, String>> inputMap = getInputMapsForNestedObjectChunks("chunks.*.chunk.text.*.context");

        List<Map<String, String>> outputMap = getOutputMapsForNestedObjectChunks();

        Map<String, String> model_config = new HashMap<>();
        model_config.put("return_number", "true");

        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "model1",
            inputMap,
            outputMap,
            model_config,
            true,
            "text_embedding",
            true,
            false,
            false,
            "{ \"text_docs\": ${ml_inference.text_docs} }"
        );

        processor.execute(ingestDocument, handler);
    }

    public void testExecute_whenEmptyInputField_ExceptionWithIgnoreMissingFalse() {
        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        String documentFieldPath = "";  // emptyInputField
        input.put("inputs", documentFieldPath);
        inputMap.add(input);
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put("text_embedding", "response");
        outputMap.add(output);
        Map<String, String> model_config = new HashMap<>();
        model_config.put("position_embedding_type", "absolute");

        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "model1",
            inputMap,
            outputMap,
            model_config,
            false,
            "remote",
            false,
            false,
            false,
            null
        );

        try {
            processor.execute(ingestDocument, handler);
        } catch (IllegalArgumentException e) {
            assertEquals("field name in input_map [ " + documentFieldPath + "] cannot be null nor empty", e.getMessage());
        }
    }

    public void testExecute_whenEmptyInputField_ExceptionWithIgnoreMissingTrue() {
        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        String documentFieldPath = "";  // emptyInputField
        input.put("inputs", documentFieldPath);
        inputMap.add(input);
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put("text_embedding", "response");
        outputMap.add(output);
        Map<String, String> model_config = new HashMap<>();
        model_config.put("position_embedding_type", "absolute");

        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "model1",
            inputMap,
            outputMap,
            model_config,
            true,
            "remote",
            false,
            false,
            false,
            null
        );

        processor.execute(ingestDocument, handler);

    }

    public void testExecute_IOExceptionWithIgnoreMissingFalse() throws JsonProcessingException {
        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        String documentFieldPath = "text";
        input.put("inputs", documentFieldPath);
        inputMap.add(input);
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put("text_embedding", "response");
        outputMap.add(output);
        Map<String, String> model_config = new HashMap<>();
        model_config.put("position_embedding_type", "absolute");

        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.readValue(Mockito.anyString(), eq(Object.class))).thenThrow(JsonProcessingException.class);

        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "model1",
            inputMap,
            outputMap,
            model_config,
            false,
            "remote",
            false,
            false,
            false,
            null
        );

        try {
            processor.execute(ingestDocument, handler);
        } catch (IllegalArgumentException e) {
            assertEquals("field name in input_map: [" + documentFieldPath + "] doesn't exist", e.getMessage());
        }
    }

    public void testExecute_NoModelInput_Exception() {
        MLInferenceIngestProcessor processorIgnoreMissingTrue = createMLInferenceProcessor(
            "model1",
            null,
            null,
            null,
            true,
            "remote",
            false,
            false,
            false,
            null
        );
        MLInferenceIngestProcessor processorIgnoreMissingFalse = createMLInferenceProcessor(
            "model1",
            null,
            null,
            null,
            false,
            "remote",
            false,
            false,
            false,
            null
        );

        MLInferenceIngestProcessor localModelProcessorIgnoreMissingFalse = createMLInferenceProcessor(
            "model1",
            null,
            null,
            null,
            false,
            "text_embedding",
            false,
            false,
            false,
            null
        );

        MLInferenceIngestProcessor localModelProcessorIgnoreMissingTrue = createMLInferenceProcessor(
            "model1",
            null,
            null,
            null,
            true,
            "text_embedding",
            false,
            false,
            false,
            null
        );

        Map<String, Object> sourceAndMetadata = new HashMap<>();
        IngestDocument emptyIngestDocument = new IngestDocument(sourceAndMetadata, new HashMap<>());
        try {
            processorIgnoreMissingTrue.execute(emptyIngestDocument, handler);
        } catch (IllegalArgumentException e) {
            assertEquals("wrong input. The model input cannot be empty.", e.getMessage());
        }
        try {
            processorIgnoreMissingFalse.execute(emptyIngestDocument, handler);
        } catch (IllegalArgumentException e) {
            assertEquals("wrong input. The model input cannot be empty.", e.getMessage());
        }

        try {
            localModelProcessorIgnoreMissingTrue.execute(emptyIngestDocument, handler);
        } catch (IllegalArgumentException e) {
            assertEquals("wrong input. The model input cannot be empty.", e.getMessage());
        }
        try {
            localModelProcessorIgnoreMissingFalse.execute(emptyIngestDocument, handler);
        } catch (IllegalArgumentException e) {
            assertEquals("wrong input. The model input cannot be empty.", e.getMessage());
        }

    }

    public void testExecute_AppendModelOutputSuccess() {
        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "model1",
            null,
            null,
            null,
            true,
            "remote",
            false,
            false,
            false,
            null
        );
        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", Arrays.asList(1, 2, 3))).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        processor.execute(ingestDocument, handler);

        Map<String, Object> sourceAndMetadata = new HashMap<>();
        sourceAndMetadata.put("key1", "value1");
        sourceAndMetadata.put("key2", "value2");
        sourceAndMetadata.put(DEFAULT_OUTPUT_FIELD_NAME, ImmutableMap.of("response", Arrays.asList(1, 2, 3)));
        IngestDocument ingestDocument1 = new IngestDocument(sourceAndMetadata, new HashMap<>());
        verify(handler).accept(eq(ingestDocument1), isNull());
        assertEquals(ingestDocument, ingestDocument1);
    }

    public void testExecute_SingleTensorInDataOutputSuccess() {
        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "model1",
            null,
            null,
            null,
            true,
            "remote",
            false,
            false,
            false,
            null
        );

        Float[] value = new Float[] { 1.0f, 2.0f, 3.0f };
        List<ModelTensors> outputs = new ArrayList<>();
        ModelTensor tensor = ModelTensor
            .builder()
            .data(value)
            .name("test")
            .shape(new long[] { 1, 3 })
            .dataType(MLResultDataType.FLOAT32)
            .byteBuffer(ByteBuffer.wrap(new byte[] { 0, 1, 0, 1 }))
            .build();
        List<ModelTensor> mlModelTensors = Arrays.asList(tensor);
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(mlModelTensors).build();
        outputs.add(modelTensors);
        ModelTensorOutput modelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(outputs).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(modelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        processor.execute(ingestDocument, handler);

        Map<String, Object> sourceAndMetadata = new HashMap<>();
        sourceAndMetadata.put("key1", "value1");
        sourceAndMetadata.put("key2", "value2");
        sourceAndMetadata.put(DEFAULT_OUTPUT_FIELD_NAME, Arrays.asList(1.0f, 2.0f, 3.0f));
        IngestDocument ingestDocument1 = new IngestDocument(sourceAndMetadata, new HashMap<>());
        verify(handler).accept(eq(ingestDocument1), isNull());
        assertEquals(ingestDocument, ingestDocument1);
    }

    public void testExecute_MultipleTensorInDataOutputSuccess() {
        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "model1",
            null,
            null,
            null,
            true,
            "remote",
            false,
            false,
            false,
            null
        );
        List<ModelTensors> outputs = new ArrayList<>();

        Float[] value = new Float[] { 1.0f };
        ModelTensor tensor = ModelTensor
            .builder()
            .data(value)
            .name("test")
            .shape(new long[] { 1, 1 })
            .dataType(MLResultDataType.FLOAT32)
            .byteBuffer(ByteBuffer.wrap(new byte[] { 0, 1, 0, 1 }))
            .build();

        Float[] value1 = new Float[] { 2.0f };
        ModelTensor tensor1 = ModelTensor
            .builder()
            .data(value1)
            .name("test")
            .shape(new long[] { 1, 1 })
            .dataType(MLResultDataType.FLOAT32)
            .byteBuffer(ByteBuffer.wrap(new byte[] { 0, 1, 0, 1 }))
            .build();

        Float[] value2 = new Float[] { 3.0f };
        ModelTensor tensor2 = ModelTensor
            .builder()
            .data(value2)
            .name("test")
            .shape(new long[] { 1, 1 })
            .dataType(MLResultDataType.FLOAT32)
            .byteBuffer(ByteBuffer.wrap(new byte[] { 0, 1, 0, 1 }))
            .build();

        List<ModelTensor> mlModelTensors = Arrays.asList(tensor, tensor1, tensor2);

        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(mlModelTensors).build();
        outputs.add(modelTensors);
        ModelTensorOutput modelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(outputs).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(modelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        processor.execute(ingestDocument, handler);

        Map<String, Object> sourceAndMetadata = new HashMap<>();
        sourceAndMetadata.put("key1", "value1");
        sourceAndMetadata.put("key2", "value2");
        sourceAndMetadata.put(DEFAULT_OUTPUT_FIELD_NAME, Arrays.asList(List.of(1.0f), List.of(2.0f), List.of(3.0f)));
        IngestDocument ingestDocument1 = new IngestDocument(sourceAndMetadata, new HashMap<>());
        verify(handler).accept(eq(ingestDocument1), isNull());
        assertEquals(ingestDocument, ingestDocument1);
    }

    public void testExecute_getModelOutputFieldWithFieldNameSuccess() {
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put("classification", "response");
        outputMap.add(output);

        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "model1",
            null,
            outputMap,
            null,
            true,
            "remote",
            false,
            false,
            false,
            null
        );
        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(ImmutableMap.of("response", ImmutableMap.of("language", "en", "score", "0.9876")))
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        ArgumentCaptor<MLPredictionTaskRequest> argCaptor = ArgumentCaptor.forClass(MLPredictionTaskRequest.class);

        processor.execute(ingestDocument, handler);

        Map<String, Object> sourceAndMetadata = new HashMap<>();
        sourceAndMetadata.put("key1", "value1");
        sourceAndMetadata.put("key2", "value2");
        sourceAndMetadata.put("classification", ImmutableMap.of("language", "en", "score", "0.9876"));
        IngestDocument ingestDocument1 = new IngestDocument(sourceAndMetadata, new HashMap<>());

        // match output
        verify(handler).accept(eq(ingestDocument1), isNull());
        assertEquals(ingestDocument, ingestDocument1);

        // match model input
        verify(client, times(1)).execute(eq(MLPredictionTaskAction.INSTANCE), argCaptor.capture(), any());
        MLPredictionTaskRequest req = argCaptor.getValue();
        MLInput mlInput = req.getMlInput();
        RemoteInferenceInputDataSet inputDataSet = (RemoteInferenceInputDataSet) mlInput.getInputDataset();
        assertEquals(toJson(inputDataSet.getParameters()), "{\"key1\":\"value1\",\"key2\":\"value2\"}");
    }

    public void testExecute_InputMapAndOutputMapSuccess() {
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put("classification", "response");
        outputMap.add(output);

        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put("inputs", "key1");
        inputMap.add(input);

        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "model1",
            inputMap,
            outputMap,
            null,
            true,
            "remote",
            false,
            false,
            false,
            null
        );
        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(ImmutableMap.of("response", ImmutableMap.of("language", "en", "score", "0.9876")))
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        ArgumentCaptor<MLPredictionTaskRequest> argCaptor = ArgumentCaptor.forClass(MLPredictionTaskRequest.class);

        processor.execute(ingestDocument, handler);

        Map<String, Object> sourceAndMetadata = new HashMap<>();
        sourceAndMetadata.put("key1", "value1");
        sourceAndMetadata.put("key2", "value2");
        sourceAndMetadata.put("classification", ImmutableMap.of("language", "en", "score", "0.9876"));
        IngestDocument ingestDocument1 = new IngestDocument(sourceAndMetadata, new HashMap<>());

        // match output
        verify(handler).accept(eq(ingestDocument1), isNull());
        assertEquals(ingestDocument, ingestDocument1);

        // match model input
        verify(client, times(1)).execute(eq(MLPredictionTaskAction.INSTANCE), argCaptor.capture(), any());
        MLPredictionTaskRequest req = argCaptor.getValue();
        MLInput mlInput = req.getMlInput();
        RemoteInferenceInputDataSet inputDataSet = (RemoteInferenceInputDataSet) mlInput.getInputDataset();
        assertEquals(toJson(inputDataSet.getParameters()), "{\"inputs\":\"value1\"}");
    }

    public void testExecute_getModelOutputFieldWithDotPathSuccess() {
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put("language_identification", "response.language");
        outputMap.add(output);

        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "model1",
            null,
            outputMap,
            null,
            true,
            "remote",
            false,
            false,
            false,
            null
        );
        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(ImmutableMap.of("response", ImmutableMap.of("language", List.of("en", "en"), "score", "0.9876")))
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        processor.execute(ingestDocument, handler);

        Map<String, Object> sourceAndMetadata = new HashMap<>();
        sourceAndMetadata.put("key1", "value1");
        sourceAndMetadata.put("key2", "value2");
        sourceAndMetadata.put("language_identification", List.of("en", "en"));
        IngestDocument ingestDocument1 = new IngestDocument(sourceAndMetadata, new HashMap<>());
        verify(handler).accept(eq(ingestDocument1), isNull());
        assertEquals(ingestDocument, ingestDocument1);
    }

    public void testExecute_getModelOutputFieldWithInvalidDotPathSuccess() {

        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put("language_identification", "response.lan");
        outputMap.add(output);

        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "model1",
            null,
            outputMap,
            null,
            true,
            "remote",
            false,
            false,
            false,
            null
        );
        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(ImmutableMap.of("response", ImmutableMap.of("language", "en", "score", "0.9876")))
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());
        Map<String, Object> sourceAndMetadata1 = new HashMap<>();
        sourceAndMetadata1.put("key1", "value1");
        sourceAndMetadata1.put("key2", "value2");
        IngestDocument ingestDocument1 = new IngestDocument(sourceAndMetadata1, new HashMap<>());
        processor.execute(ingestDocument1, handler);

        verify(handler).accept(eq(ingestDocument1), isNull());
        assertNull(ingestDocument1.getIngestMetadata().get("language_identification"));
    }

    public void testExecute_getModelOutputFieldWithInvalidDotPathException() {

        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put("response.lan", "language_identification");
        outputMap.add(output);

        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "model1",
            null,
            outputMap,
            null,
            false,
            "remote",
            false,
            false,
            false,
            null
        );
        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(ImmutableMap.of("response", ImmutableMap.of("language", "en", "score", "0.9876")))
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());
        Map<String, Object> sourceAndMetadata1 = new HashMap<>();
        sourceAndMetadata1.put("key1", "value1");
        sourceAndMetadata1.put("key2", "value2");
        IngestDocument ingestDocument1 = new IngestDocument(sourceAndMetadata1, new HashMap<>());
        try {
            processor.execute(ingestDocument1, handler);
        } catch (IllegalArgumentException e) {
            assertEquals("model inference output can not find field name: " + "response.lan", e.getMessage());
        }
        ;

    }

    public void testExecute_getModelOutputFieldInNestedWithInvalidDotPathException() {

        List<Map<String, String>> inputMap = getInputMapsForNestedObjectChunks("chunks.*.chunk.text.*.context");

        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put("chunks.*.chunk.text.*.context_embedding", "response.language1");
        outputMap.add(output);

        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "model1",
            inputMap,
            outputMap,
            null,
            false,
            "remote",
            false,
            false,
            false,
            null
        );
        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(ImmutableMap.of("response", ImmutableMap.of("language", "en", "score", "0.9876")))
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        processor.execute(nestedObjectIngestDocument, handler);

        verify(handler)
            .accept(
                eq(null),
                argThat(
                    exception -> exception
                        .getMessage()
                        .equals("An unexpected error occurred: model inference output cannot find field name: response.language1")
                )
            );
        ;

    }

    public void testExecute_getModelOutputFieldWithExistedFieldNameException() {
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put("key1", "response");
        outputMap.add(output);

        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "model1",
            null,
            outputMap,
            null,
            false,
            "remote",
            false,
            false,
            true,
            null
        );
        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(ImmutableMap.of("response", ImmutableMap.of("language", "en", "score", "0.9876")))
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        processor.execute(ingestDocument, handler);

        Map<String, Object> sourceAndMetadata = new HashMap<>();
        sourceAndMetadata.put("key1", ImmutableMap.of("language", "en", "score", "0.9876"));
        sourceAndMetadata.put("key2", "value2");
        IngestDocument ingestDocument1 = new IngestDocument(sourceAndMetadata, new HashMap<>());
        verify(handler).accept(eq(ingestDocument1), isNull());
        assertEquals(ingestDocument, ingestDocument1);
    }

    public void testExecute_documentNotExistedFieldNameException() {
        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put("inputs", "key99");
        inputMap.add(input);

        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put("classification", "response");
        outputMap.add(output);

        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "model1",
            inputMap,
            outputMap,
            null,
            false,
            "remote",
            false,
            false,
            false,
            null
        );

        processor.execute(ingestDocument, handler);
        verify(handler)
            .accept(eq(null), argThat(exception -> exception.getMessage().equals("Cannot find field name defined from input map: key99")));
    }

    public void testExecute_nestedDocumentNotExistedFieldNameException() {
        List<Map<String, String>> inputMap = getInputMapsForNestedObjectChunks("chunks.*.chunk.text.*.context1");

        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "model1",
            inputMap,
            null,
            null,
            false,
            "remote",
            false,
            false,
            false,
            null
        );

        processor.execute(ingestDocument, handler);
        verify(handler)
            .accept(
                eq(null),
                argThat(
                    exception -> exception
                        .getMessage()
                        .equals("Cannot find field name defined from input map: chunks.*.chunk.text.*.context1")
                )
            );
    }

    public void testExecute_getModelOutputFieldDifferentLengthException() {
        List<Map<String, String>> inputMap = getInputMapsForNestedObjectChunks("chunks.*.chunk.text.*.context");

        List<Map<String, String>> outputMap = getOutputMapsForNestedObjectChunks();

        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "model1",
            inputMap,
            outputMap,
            null,
            true,
            "remote",
            false,
            false,
            false,
            null
        );
        ArrayList<List<Integer>> modelPredictionOutput = new ArrayList<>();
        modelPredictionOutput.add(Arrays.asList(1));
        modelPredictionOutput.add(Arrays.asList(2));
        modelPredictionOutput.add(Arrays.asList(3));

        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", modelPredictionOutput)).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        Map<String, Object> sourceAndMetadata = getNestedObjectWithAnotherNestedObjectSource();

        IngestDocument nestedObjectIngestDocument = new IngestDocument(sourceAndMetadata, new HashMap<>());

        try {
            processor.execute(nestedObjectIngestDocument, handler);
        } catch (RuntimeException e) {
            assertEquals(
                "the prediction field: response is an array in size of 3 but the document field array from field chunks.*.chunk.text.*.embedding is in size of 4",
                e.getMessage()
            );
        }

    }

    public void testExecute_getModelOutputFieldDifferentLengthIgnoreFailureSuccess() {
        List<Map<String, String>> inputMap = getInputMapsForNestedObjectChunks("chunks.*.chunk.text.*.context");

        List<Map<String, String>> outputMap = getOutputMapsForNestedObjectChunks();

        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "model1",
            inputMap,
            outputMap,
            null,
            true,
            "remote",
            false,
            true,
            false,
            null
        );
        ArrayList<List<Integer>> modelPredictionOutput = new ArrayList<>();
        modelPredictionOutput.add(Arrays.asList(1));
        modelPredictionOutput.add(Arrays.asList(2));
        modelPredictionOutput.add(Arrays.asList(3));

        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", modelPredictionOutput)).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        Map<String, Object> sourceAndMetadata = getNestedObjectWithAnotherNestedObjectSource();

        IngestDocument nestedObjectIngestDocument = new IngestDocument(sourceAndMetadata, new HashMap<>());

        processor.execute(nestedObjectIngestDocument, handler);
        verify(handler).accept(eq(nestedObjectIngestDocument), isNull());
        assertNull(nestedObjectIngestDocument.getIngestMetadata().get("response"));
    }

    public void testExecute_getMlModelTensorsIsNull() {
        List<Map<String, String>> inputMap = getInputMapsForNestedObjectChunks("chunks.*.chunk.text.*.context");

        List<Map<String, String>> outputMap = getOutputMapsForNestedObjectChunks();

        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "model1",
            inputMap,
            outputMap,
            null,
            false,
            "remote",
            false,
            false,
            false,
            null
        );
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(null).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());
        Map<String, Object> sourceAndMetadata = getNestedObjectWithAnotherNestedObjectSource();

        IngestDocument nestedObjectIngestDocument = new IngestDocument(sourceAndMetadata, new HashMap<>());

        processor.execute(nestedObjectIngestDocument, handler);

        verify(handler)
            .accept(
                eq(null),
                argThat(exception -> exception.getMessage().equals("An unexpected error occurred: Output tensors are null or empty."))
            );

    }

    public void testExecute_localMLModelTensorsIsNull() {
        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put("text_docs", "chunks.*.chunk.text.*.context");
        inputMap.add(input);

        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put("chunks.*.chunk.text.*.context_embedding", "$.inference_results[0].output[0].data");
        outputMap.add(output);

        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "model1",
            inputMap,
            outputMap,
            null,
            false,
            "text_embedding",
            true,
            false,
            false,
            "{ \"text_docs\": ${ml_inference.text_docs} }"
        );
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(null).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());
        Map<String, Object> sourceAndMetadata = getNestedObjectWithAnotherNestedObjectSource();

        IngestDocument nestedObjectIngestDocument = new IngestDocument(sourceAndMetadata, new HashMap<>());

        processor.execute(nestedObjectIngestDocument, handler);

        verify(handler)
            .accept(
                eq(null),
                argThat(
                    exception -> exception
                        .getMessage()
                        .equals(
                            "An unexpected error occurred: model inference output "
                                + "cannot find such json path: $.inference_results[0].output[0].data"
                        )
                )
            );

    }

    public void testExecute_getMlModelTensorsIsNullIgnoreFailure() {
        List<Map<String, String>> inputMap = getInputMapsForNestedObjectChunks("chunks.*.chunk.text.*.context");

        List<Map<String, String>> outputMap = getOutputMapsForNestedObjectChunks();

        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "model1",
            inputMap,
            outputMap,
            null,
            true,
            "remote",
            false,
            true,
            false,
            null
        );
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(null).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());
        Map<String, Object> sourceAndMetadata = getNestedObjectWithAnotherNestedObjectSource();

        IngestDocument nestedObjectIngestDocument = new IngestDocument(sourceAndMetadata, new HashMap<>());

        processor.execute(nestedObjectIngestDocument, handler);
        verify(handler).accept(eq(nestedObjectIngestDocument), isNull());
    }

    public void testExecute_modelTensorOutputIsNull() {
        List<Map<String, String>> inputMap = getInputMapsForNestedObjectChunks("chunks.*.chunk.text.*.context");

        List<Map<String, String>> outputMap = getOutputMapsForNestedObjectChunks();

        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "model1",
            inputMap,
            outputMap,
            null,
            true,
            "remote",
            false,
            false,
            false,
            null
        );
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(null).build();
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());
        Map<String, Object> sourceAndMetadata = getNestedObjectWithAnotherNestedObjectSource();

        IngestDocument nestedObjectIngestDocument = new IngestDocument(sourceAndMetadata, new HashMap<>());

        processor.execute(nestedObjectIngestDocument, handler);
        verify(handler)
            .accept(
                eq(null),
                argThat(exception -> exception.getMessage().equals("An unexpected error occurred: Model outputs are null or empty."))
            );

    }

    public void testExecute_modelTensorOutputIsNullIgnoreFailureSuccess() {
        List<Map<String, String>> inputMap = getInputMapsForNestedObjectChunks("chunks.*.chunk.text.*.context");

        List<Map<String, String>> outputMap = getOutputMapsForNestedObjectChunks();

        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "model1",
            inputMap,
            outputMap,
            null,
            true,
            "remote",
            false,
            true,
            false,
            null
        );
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(null).build();
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());
        Map<String, Object> sourceAndMetadata = getNestedObjectWithAnotherNestedObjectSource();

        IngestDocument nestedObjectIngestDocument = new IngestDocument(sourceAndMetadata, new HashMap<>());

        processor.execute(nestedObjectIngestDocument, handler);
        verify(handler).accept(eq(nestedObjectIngestDocument), isNull());
    }

    /**
     * Test processor configuration with nested object document
     * and array of Map<String,Object>, where the value Object is a List<String>
     */
    public void testExecute_localModelSuccess() {

        // Processor configuration
        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put("text_docs", "_ingest._value.title");
        inputMap.add(input);

        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put("_ingest._value.title_embedding", "$.inference_results[0].output[0].data");
        outputMap.add(output);

        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "model_1",
            inputMap,
            outputMap,
            null,
            true,
            "text_embedding",
            true,
            true,
            false,
            "{ \"text_docs\": ${ml_inference.text_docs} }"
        );

        // Mocking the model output
        List<Integer> modelPredictionOutput = Arrays.asList(1, 2, 3, 4);
        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(
                ImmutableMap
                    .of(
                        "inference_results",
                        Arrays
                            .asList(
                                ImmutableMap
                                    .of(
                                        "output",
                                        Arrays.asList(ImmutableMap.of("name", "sentence_embedding", "data", modelPredictionOutput))
                                    )
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

        // Setting up the ingest document
        Map<String, Object> sourceAndMetadata = new HashMap<>();
        List<Map<String, Object>> books = new ArrayList<>();
        Map<String, Object> book1 = new HashMap<>();
        book1.put("title", Arrays.asList("first book"));
        book1.put("description", "This is first book");
        Map<String, Object> book2 = new HashMap<>();
        book2.put("title", Arrays.asList("second book"));
        book2.put("description", "This is second book");
        books.add(book1);
        books.add(book2);
        sourceAndMetadata.put("books", books);

        Map<String, Object> ingestMetadata = new HashMap<>();
        ingestMetadata.put("pipeline", "test_pipeline");
        ingestMetadata.put("timestamp", ZonedDateTime.now());
        Map<String, Object> ingestValue = new HashMap<>();
        ingestValue.put("title", Arrays.asList("first book"));
        ingestValue.put("description", "This is first book");
        ingestMetadata.put("_value", ingestValue);
        sourceAndMetadata.put("_ingest", ingestMetadata);

        IngestDocument nestedObjectIngestDocument = new IngestDocument(sourceAndMetadata, new HashMap<>());
        processor.execute(nestedObjectIngestDocument, handler);

        // Validate the document
        List<Map<String, Object>> updatedBooks = new ArrayList<>();
        Map<String, Object> updatedBook1 = new HashMap<>();
        updatedBook1.put("title", Arrays.asList("first book"));
        updatedBook1.put("description", "This is first book");
        updatedBook1.put("title_embedding", modelPredictionOutput);
        Map<String, Object> updatedBook2 = new HashMap<>();
        updatedBook2.put("title", Arrays.asList("second book"));
        updatedBook2.put("description", "This is second book");
        updatedBook2.put("title_embedding", modelPredictionOutput);
        updatedBooks.add(updatedBook1);
        updatedBooks.add(updatedBook2);
        sourceAndMetadata.put("books", updatedBooks);

        // match meta data
        Map<String, Object> expectedIngestMetadata = new HashMap<>();
        Map<String, Object> valueMap = new HashMap<>();
        Map<String, Object> titleEmbeddingMap = new HashMap<>();
        List<Map<String, Object>> inferenceResultsList = new ArrayList<>();

        Map<String, Object> expectedOutputMap = new HashMap<>();
        List<Map<String, Object>> outputList = new ArrayList<>();
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("data", Arrays.asList(1.0, 2.0, 3.0, 4.0));
        dataMap.put("name", "sentence_embedding");

        Map<String, Object> inferenceResultMap = new HashMap<>();
        List<Map<String, Object>> outputListInner = new ArrayList<>();
        outputListInner.add(dataMap);
        inferenceResultMap.put("output", outputListInner);

        Map<String, Object> dataAsMapMap = new HashMap<>();
        List<Map<String, Object>> inferenceResultsListInner = new ArrayList<>();
        inferenceResultsListInner.add(inferenceResultMap);
        dataAsMapMap.put("inference_results", inferenceResultsListInner);

        Map<String, Object> expectedDataAsMap = new HashMap<>();
        expectedDataAsMap.put("dataAsMap", dataAsMapMap);
        outputList.add(expectedDataAsMap);
        expectedOutputMap.put("output", outputList);
        inferenceResultsList.add(expectedOutputMap);

        titleEmbeddingMap.put("inference_results", inferenceResultsList);
        valueMap.put("title_embedding", titleEmbeddingMap);
        expectedIngestMetadata.put("_value", valueMap);

        IngestDocument ingestDocument1 = new IngestDocument(sourceAndMetadata, expectedIngestMetadata);
        System.out.println(ingestDocument1);
        verify(handler).accept(eq(ingestDocument1), isNull());
        assertEquals(nestedObjectIngestDocument, ingestDocument1);
    }

    public void testExecute_localSparseEncodingModelMultipleModelTensors() {

        // Processor configuration
        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put("text_docs", "chunks.*.chunk.text.*.context");
        inputMap.add(input);

        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put("chunks.*.chunk.text.*.context_embedding", "$.inference_results.*.output.*.dataAsMap.response");
        outputMap.add(output);

        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "model_1",
            inputMap,
            outputMap,
            null,
            true,
            "sparse_encoding",
            true,
            true,
            false,
            "{ \"text_docs\": ${ml_inference.text_docs} }"
        );

        // Mocking the model output with simple values
        List<Map<String, Object>> modelEmbeddings = new ArrayList<>();
        Map<String, Object> embedding = ImmutableMap.of("response", Arrays.asList(1.0, 2.0, 3.0, 4.0));
        for (int i = 1; i <= 4; i++) {
            modelEmbeddings.add(embedding);
        }

        List<ModelTensor> modelTensors = new ArrayList<>();
        for (Map<String, Object> embeddings : modelEmbeddings) {
            modelTensors.add(ModelTensor.builder().dataAsMap(embeddings).build());
        }

        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput
            .builder()
            .mlModelOutputs(Collections.singletonList(ModelTensors.builder().mlModelTensors(modelTensors).build()))
            .build();

        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());

        IngestDocument ingestDocument = new IngestDocument(getNestedObjectWithAnotherNestedObjectSource(), new HashMap<>());
        processor.execute(ingestDocument, handler);
        verify(handler).accept(eq(ingestDocument), isNull());

        List<Map<String, Object>> chunks = (List<Map<String, Object>>) ingestDocument.getFieldValue("chunks", List.class);

        List<Map<String, Object>> firstChunkTexts = (List<Map<String, Object>>) ((Map<String, Object>) chunks.get(0).get("chunk"))
            .get("text");
        Assert.assertEquals(modelEmbeddings.get(0).get("response"), firstChunkTexts.get(0).get("context_embedding"));
        Assert.assertEquals(modelEmbeddings.get(1).get("response"), firstChunkTexts.get(1).get("context_embedding"));

        List<Map<String, Object>> secondChunkTexts = (List<Map<String, Object>>) ((Map<String, Object>) chunks.get(1).get("chunk"))
            .get("text");
        Assert.assertEquals(modelEmbeddings.get(2).get("response"), secondChunkTexts.get(0).get("context_embedding"));
        Assert.assertEquals(modelEmbeddings.get(3).get("response"), secondChunkTexts.get(1).get("context_embedding"));

    }

    public void testExecute_localModelOutputIsNullIgnoreFailureSuccess() {
        List<Map<String, String>> inputMap = getInputMapsForNestedObjectChunks("chunks.*.chunk.text.*.context");

        List<Map<String, String>> outputMap = getOutputMapsForNestedObjectChunks();

        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "model1",
            inputMap,
            outputMap,
            null,
            true,
            "text_embedding",
            true,
            true,
            false,
            "{ \"text_docs\": ${ml_inference.text_docs} }"
        );
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(null).build();
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());
        Map<String, Object> sourceAndMetadata = getNestedObjectWithAnotherNestedObjectSource();

        IngestDocument nestedObjectIngestDocument = new IngestDocument(sourceAndMetadata, new HashMap<>());

        processor.execute(nestedObjectIngestDocument, handler);
        verify(handler).accept(eq(nestedObjectIngestDocument), isNull());
    }

    public void testExecute_localModelTensorsIsNullIgnoreFailure() {
        List<Map<String, String>> inputMap = getInputMapsForNestedObjectChunks("chunks.*.chunk.text.*.context");

        List<Map<String, String>> outputMap = getOutputMapsForNestedObjectChunks();

        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "model1",
            inputMap,
            outputMap,
            null,
            true,
            "remote",
            false,
            true,
            false,
            "{ \"text_docs\": ${ml_inference.text_docs} }"
        );
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(null).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());
        Map<String, Object> sourceAndMetadata = getNestedObjectWithAnotherNestedObjectSource();

        IngestDocument nestedObjectIngestDocument = new IngestDocument(sourceAndMetadata, new HashMap<>());

        processor.execute(nestedObjectIngestDocument, handler);
        verify(handler).accept(eq(nestedObjectIngestDocument), isNull());
    }

    public void testParseGetDataInTensor_IntegerDataType() {
        ModelTensor mockTensor = mock(ModelTensor.class);
        when(mockTensor.getDataType()).thenReturn(MLResultDataType.INT8);
        when(mockTensor.getData()).thenReturn(new Number[] { 1, 2, 3 });
        Object result = ModelExecutor.parseDataInTensor(mockTensor);
        assertEquals(List.of(1, 2, 3), result);
    }

    public void testParseGetDataInTensor_FloatDataType() {
        ModelTensor mockTensor = mock(ModelTensor.class);
        when(mockTensor.getDataType()).thenReturn(MLResultDataType.FLOAT32);
        when(mockTensor.getData()).thenReturn(new Number[] { 1.1, 2.2, 3.3 });
        Object result = ModelExecutor.parseDataInTensor(mockTensor);
        assertEquals(List.of(1.1f, 2.2f, 3.3f), result);
    }

    public void testParseGetDataInTensor_BooleanDataType() {
        ModelTensor mockTensor = mock(ModelTensor.class);
        when(mockTensor.getDataType()).thenReturn(MLResultDataType.BOOLEAN);
        when(mockTensor.getData()).thenReturn(new Number[] { 1, 0, 1 });
        Object result = ModelExecutor.parseDataInTensor(mockTensor);
        assertEquals(List.of(true, false, true), result);
    }

    public void testWriteNewDotPathForNestedObject() {
        Map<String, Object> docSourceAndMetaData = new HashMap<>();
        docSourceAndMetaData.put("_id", randomAlphaOfLength(5));
        docSourceAndMetaData.put("_index", "my_books");

        List<Map<String, String>> books = new ArrayList<>();
        Map<String, String> book1 = new HashMap<>();
        book1.put("title", "first book");
        book1.put("description", "this is first book");
        Map<String, String> book2 = new HashMap<>();
        book2.put("title", "second book");
        book2.put("description", "this is second book");
        books.add(book1);
        books.add(book2);
        docSourceAndMetaData.put("books", books);

        Map<String, Object> ingestMetadata = new HashMap<>();
        ingestMetadata.put("pipeline", "test_pipeline");
        ingestMetadata.put("timeestamp", ZonedDateTime.now());
        Map<String, String> ingestValue = new HashMap<>();
        ingestValue.put("title", "first book");
        ingestValue.put("description", "this is first book");
        ingestMetadata.put("_value", ingestValue);
        docSourceAndMetaData.put("_ingest", ingestMetadata);

        String path = "_ingest._value.title";
        List<String> newPath = modelExecutor.writeNewDotPathForNestedObject(docSourceAndMetaData, path);
        Assert.assertEquals(1, newPath.size());
        Assert.assertEquals(path, newPath.get(0));

        String path2 = "books.*.title";
        List<String> newPath2 = modelExecutor.writeNewDotPathForNestedObject(docSourceAndMetaData, path2);
        Assert.assertEquals(2, newPath2.size());
        Assert.assertEquals("books.0.title", newPath2.get(0));
        Assert.assertEquals("books.1.title", newPath2.get(1));
    }

    private static Map<String, Object> getNestedObjectWithAnotherNestedObjectSource() {
        /**
         * {chunks=[
         *     {chunk={text=[
         *         {context=this is first, chapter=first chapter},
         *         {context=this is second, chapter=first chapter}
         *     ]}},
         *     {chunk={text=[
         *         {context=this is third, chapter=second chapter},
         *         {context=this is fourth, chapter=second chapter}
         *     ]}}
         * ]}
         */
        ArrayList<Object> childDocuments = new ArrayList<>();

        Map<String, Object> childDocument1Text = new HashMap<>();
        ArrayList<Object> grandChildDocuments1 = new ArrayList<>();
        Map<String, Object> grandChildDocument1Text = new HashMap<>();
        grandChildDocument1Text.put("context", "this is first");
        grandChildDocument1Text.put("chapter", "first chapter");
        Map<String, Object> grandChildDocument2Text = new HashMap<>();
        grandChildDocument2Text.put("context", "this is second");
        grandChildDocument2Text.put("chapter", "first chapter");
        grandChildDocuments1.add(grandChildDocument1Text);
        grandChildDocuments1.add(grandChildDocument2Text);
        childDocument1Text.put("text", grandChildDocuments1);

        Map<String, Object> childDocument1 = new HashMap<>();
        childDocument1.put("chunk", childDocument1Text);

        Map<String, Object> childDocument2 = new HashMap<>();
        Map<String, Object> childDocument2Text = new HashMap<>();
        ArrayList<Object> grandChildDocuments2 = new ArrayList<>();

        Map<String, Object> grandChildDocument3Text = new HashMap<>();
        grandChildDocument3Text.put("context", "this is third");
        grandChildDocument3Text.put("chapter", "second chapter");
        Map<String, Object> grandChildDocument4Text = new HashMap<>();
        grandChildDocument4Text.put("context", "this is fourth");
        grandChildDocument4Text.put("chapter", "second chapter");
        grandChildDocuments2.add(grandChildDocument3Text);
        grandChildDocuments2.add(grandChildDocument4Text);

        childDocument2Text.put("text", grandChildDocuments2);
        childDocument2.put("chunk", childDocument2Text);

        childDocuments.add(childDocument1);
        childDocuments.add(childDocument2);

        Map<String, Object> sourceAndMetadata = new HashMap<>();
        sourceAndMetadata.put("chunks", childDocuments);
        return sourceAndMetadata;
    }

    private static List<Map<String, String>> getOutputMapsForNestedObjectChunks() {
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        String modelOutputPath = "response";
        String documentFieldName = "chunks.*.chunk.text.*.embedding";
        output.put(documentFieldName, modelOutputPath);
        outputMap.add(output);
        return outputMap;
    }

    public void testExecute_MeanPoolingTransformation() {
        List<Map<String, String>> outputMap = new ArrayList<>();
        Map<String, String> output = new HashMap<>();
        output.put("multi_vectors", "image_embeddings[0]");
        output.put("knn_vector", "image_embeddings[0].meanPooling()");
        outputMap.add(output);

        MLInferenceIngestProcessor processor = createMLInferenceProcessor(
            "model1",
            null,
            outputMap,
            null,
            false,
            "REMOTE",
            false,
            false,
            false,
            null
        );

        Map<String, Object> sourceAndMetadata = new HashMap<>();
        sourceAndMetadata.put("key1", "value1");
        IngestDocument ingestDocument = new IngestDocument(sourceAndMetadata, new HashMap<>());

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

        BiConsumer<IngestDocument, Exception> handler = mock(BiConsumer.class);
        processor.execute(ingestDocument, handler);

        verify(handler).accept(eq(ingestDocument), isNull());

        // Verify multi_vectors contains the original nested array
        assertEquals(imageEmbeddings, ingestDocument.getFieldValue("multi_vectors", Object.class));

        // Verify knn_vector contains the mean pooled result
        List<Double> meanPooled = (List<Double>) ingestDocument.getFieldValue("knn_vector", Object.class);
        assertEquals(3, meanPooled.size());
        assertEquals(4.0, meanPooled.get(0), 0.001); // (1+4+7)/3
        assertEquals(5.0, meanPooled.get(1), 0.001); // (2+5+8)/3
        assertEquals(6.0, meanPooled.get(2), 0.001); // (3+6+9)/3
    }

    private static List<Map<String, String>> getInputMapsForNestedObjectChunks(String documentFieldPath) {
        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put("inputs", documentFieldPath);
        inputMap.add(input);
        return inputMap;
    }
}
