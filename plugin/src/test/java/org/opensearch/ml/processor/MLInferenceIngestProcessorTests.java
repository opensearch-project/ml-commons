/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.processor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.common.utils.StringUtils.toJson;
import static org.opensearch.ml.processor.MLInferenceIngestProcessor.DEFAULT_OUTPUT_FIELD_NAME;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opensearch.client.Client;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ingest.IngestDocument;
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
        String model_id,
        Map<String, String> model_config,
        List<Map<String, String>> input_map,
        List<Map<String, String>> output_map,
        boolean ignoreMissing,
        boolean ignoreFailure
    ) {
        return new MLInferenceIngestProcessor(
            model_id,
            input_map,
            output_map,
            model_config,
            RANDOM_MULTIPLIER,
            PROCESSOR_TAG,
            DESCRIPTION,
            ignoreMissing,
            ignoreFailure,
            scriptService,
            client
        );
    }

    public void testExecute_Exception() throws Exception {
        MLInferenceIngestProcessor processor = createMLInferenceProcessor("model1", null, null, null, false, false);
        try {
            IngestDocument document = processor.execute(ingestDocument);
        } catch (UnsupportedOperationException e) {
            assertEquals("this method should not get executed.", e.getMessage());
        }

    }

    /**
     * test nested object document with array of Map<String,String>
     */
    public void testExecute_nestedObjectStringDocumentSuccess() {

        List<Map<String, String>> inputMap = getInputMapsForNestedObjectChunks("chunks.*.chunk");

        MLInferenceIngestProcessor processor = createMLInferenceProcessor("model1", null, inputMap, null, true, false);
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
    public void testExecute_nestedObjectMapDocumentSuccess() {
        List<Map<String, String>> inputMap = getInputMapsForNestedObjectChunks("chunks.*.chunk.text");

        MLInferenceIngestProcessor processor = createMLInferenceProcessor("model1", null, inputMap, null, true, false);
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

        MLPredictionTaskRequest expectedRequest = (MLPredictionTaskRequest) modelExecutor
            .getRemoteModelInferenceRequest(inputParameters, "model1");
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
    public void testExecute_nestedObjectAndNestedObjectDocumentOutputInOneFieldSuccess() {
        List<Map<String, String>> inputMap = getInputMapsForNestedObjectChunks("chunks.*.chunk.text.*.context");

        MLInferenceIngestProcessor processor = createMLInferenceProcessor("model1", null, inputMap, null, true, false);
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

        MLPredictionTaskRequest expectedRequest = (MLPredictionTaskRequest) modelExecutor
            .getRemoteModelInferenceRequest(inputParameters, "model1");
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
        MLInferenceIngestProcessor processor = createMLInferenceProcessor("model1", null, inputMap, outputMap, true, false);
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

        MLInferenceIngestProcessor processor = createMLInferenceProcessor("model1", null, inputMap, outputMap, true, false);
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
        MLInferenceIngestProcessor processor = createMLInferenceProcessor("model1", null, null, null, false, false);
        when(client.execute(any(), any())).thenThrow(new RuntimeException("Executing Model failed with exception"));
        try {
            processor.execute(ingestDocument, handler);
        } catch (RuntimeException e) {
            assertEquals("Executing Model failed with exception", e.getMessage());
        }
    }

    public void testExecute_InferenceOnFailure() {
        MLInferenceIngestProcessor processor = createMLInferenceProcessor("model1", null, null, null, false, false);
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
        MLInferenceIngestProcessor processor = createMLInferenceProcessor("model1", null, null, outputMap, false, false);

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

        MLInferenceIngestProcessor processor = createMLInferenceProcessor("model1", model_config, inputMap, outputMap, false, false);

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
        MLInferenceIngestProcessor processor = createMLInferenceProcessor("model1", null, inputMap, outputMap, true, false);

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

        MLInferenceIngestProcessor processor = createMLInferenceProcessor("model1", model_config, inputMap, outputMap, false, false);

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

        MLInferenceIngestProcessor processor = createMLInferenceProcessor("model1", model_config, inputMap, outputMap, true, false);

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

        MLInferenceIngestProcessor processor = createMLInferenceProcessor("model1", model_config, inputMap, outputMap, false, false);

        try {
            processor.execute(ingestDocument, handler);
        } catch (IllegalArgumentException e) {
            assertEquals("field name in input_map: [" + documentFieldPath + "] doesn't exist", e.getMessage());
        }
    }

    public void testExecute_NoModelInput_Exception() {
        MLInferenceIngestProcessor processorIgnoreMissingTrue = createMLInferenceProcessor("model1", null, null, null, true, false);
        MLInferenceIngestProcessor processorIgnoreMissingFalse = createMLInferenceProcessor("model1", null, null, null, false, false);

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

    }

    public void testExecute_AppendModelOutputSuccess() {
        MLInferenceIngestProcessor processor = createMLInferenceProcessor("model1", null, null, null, true, false);
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
        MLInferenceIngestProcessor processor = createMLInferenceProcessor("model1", null, null, null, true, false);

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
        MLInferenceIngestProcessor processor = createMLInferenceProcessor("model1", null, null, null, true, false);
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

        MLInferenceIngestProcessor processor = createMLInferenceProcessor("model1", null, null, outputMap, true, false);
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

        MLInferenceIngestProcessor processor = createMLInferenceProcessor("model1", null, inputMap, outputMap, false, false);
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

        MLInferenceIngestProcessor processor = createMLInferenceProcessor("model1", null, null, outputMap, true, false);
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

        MLInferenceIngestProcessor processor = createMLInferenceProcessor("model1", null, null, outputMap, true, false);
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

        MLInferenceIngestProcessor processor = createMLInferenceProcessor("model1", null, null, outputMap, false, false);
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

        MLInferenceIngestProcessor processor = createMLInferenceProcessor("model1", null, inputMap, outputMap, false, false);
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

        MLInferenceIngestProcessor processor = createMLInferenceProcessor("model1", null, null, outputMap, false, false);
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
        verify(handler)
            .accept(
                eq(null),
                argThat(
                    exception -> exception
                        .getMessage()
                        .equals(
                            "document already has field name key1. Not allow to overwrite the same field name, please check output_map."
                        )
                )
            );
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

        MLInferenceIngestProcessor processor = createMLInferenceProcessor("model1", null, inputMap, outputMap, false, false);

        processor.execute(ingestDocument, handler);
        verify(handler)
            .accept(eq(null), argThat(exception -> exception.getMessage().equals("Cannot find field name defined from input map: key99")));
    }

    public void testExecute_nestedDocumentNotExistedFieldNameException() {
        List<Map<String, String>> inputMap = getInputMapsForNestedObjectChunks("chunks.*.chunk.text.*.context1");

        MLInferenceIngestProcessor processor = createMLInferenceProcessor("model1", null, inputMap, null, false, false);

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

        MLInferenceIngestProcessor processor = createMLInferenceProcessor("model1", null, inputMap, outputMap, true, false);
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

        MLInferenceIngestProcessor processor = createMLInferenceProcessor("model1", null, inputMap, outputMap, true, true);
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

        MLInferenceIngestProcessor processor = createMLInferenceProcessor("model1", null, inputMap, outputMap, true, false);
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

    public void testExecute_getMlModelTensorsIsNullIgnoreFailure() {
        List<Map<String, String>> inputMap = getInputMapsForNestedObjectChunks("chunks.*.chunk.text.*.context");

        List<Map<String, String>> outputMap = getOutputMapsForNestedObjectChunks();

        MLInferenceIngestProcessor processor = createMLInferenceProcessor("model1", null, inputMap, outputMap, true, true);
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

        MLInferenceIngestProcessor processor = createMLInferenceProcessor("model1", null, inputMap, outputMap, true, false);
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(null).build();
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(any(), any(), any());
        Map<String, Object> sourceAndMetadata = getNestedObjectWithAnotherNestedObjectSource();

        IngestDocument nestedObjectIngestDocument = new IngestDocument(sourceAndMetadata, new HashMap<>());

        processor.execute(nestedObjectIngestDocument, handler);
        verify(handler).accept(eq(null), argThat(exception -> exception.getMessage().equals("model inference output cannot be null")));

    }

    public void testExecute_modelTensorOutputIsNullIgnoreFailureSuccess() {
        List<Map<String, String>> inputMap = getInputMapsForNestedObjectChunks("chunks.*.chunk.text.*.context");

        List<Map<String, String>> outputMap = getOutputMapsForNestedObjectChunks();

        MLInferenceIngestProcessor processor = createMLInferenceProcessor("model1", null, inputMap, outputMap, true, true);
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

    private static List<Map<String, String>> getInputMapsForNestedObjectChunks(String documentFieldPath) {
        List<Map<String, String>> inputMap = new ArrayList<>();
        Map<String, String> input = new HashMap<>();
        input.put("inputs", documentFieldPath);
        inputMap.add(input);
        return inputMap;
    }
}
