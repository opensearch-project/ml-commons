package org.opensearch.ml.common.transport.forward;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DataFrameBuilder;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.parameter.clustering.KMeansParams;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;

@RunWith(MockitoJUnitRunner.class)
public class MLForwardRequestTest {

    private MLForwardInput forwardInput;
    private MLTask mlTask;
    private MLInput modelInput;
    private MLRegisterModelInput registerModelInput;
    private final FunctionName functionName = FunctionName.KMEANS;

    @Before
    public void setUp() throws Exception {
        Instant time = Instant.now();
        mlTask = MLTask
            .builder()
            .taskId("mlTaskTaskId")
            .modelId("mlTaskModelId")
            .taskType(MLTaskType.PREDICTION)
            .functionName(functionName)
            .state(MLTaskState.RUNNING)
            .inputType(MLInputDataType.DATA_FRAME)
            .workerNodes(Arrays.asList("mlTaskNode1"))
            .progress(0.0f)
            .outputIndex("test_index")
            .error("test_error")
            .createTime(time.minus(1, ChronoUnit.MINUTES))
            .lastUpdateTime(time)
            .build();

        DataFrame dataFrame = DataFrameBuilder.load(Collections.singletonList(new HashMap<String, Object>() {
            {
                put("key1", 2.0D);
            }
        }));
        modelInput = MLInput
            .builder()
            .algorithm(FunctionName.KMEANS)
            .parameters(KMeansParams.builder().centroids(1).build())
            .inputDataset(DataFrameInputDataset.builder().dataFrame(dataFrame).build())
            .build();
        MLModelConfig config = TextEmbeddingModelConfig
            .builder()
            .modelType("testModelType")
            .allConfig("{\"field1\":\"value1\",\"field2\":\"value2\"}")
            .frameworkType(TextEmbeddingModelConfig.FrameworkType.SENTENCE_TRANSFORMERS)
            .embeddingDimension(100)
            .build();
        registerModelInput = MLRegisterModelInput
            .builder()
            .functionName(functionName)
            .modelName("testModelName")
            .version("testModelVersion")
            .modelGroupId("modelGroupId")
            .url("url")
            .modelFormat(MLModelFormat.ONNX)
            .modelConfig(config)
            .deployModel(true)
            .modelNodeIds(new String[] { "modelNodeIds" })
            .build();

        forwardInput = MLForwardInput
            .builder()
            .taskId("forwardInputTaskId")
            .modelId("forwardInputModelId")
            .workerNodeId("forwardInputWorkerNodeId")
            .requestType(MLForwardRequestType.DEPLOY_MODEL_DONE)
            .mlTask(mlTask)
            .modelInput(modelInput)
            .error("forwardInputError")
            .workerNodes(new String[] { "forwardInputNodeId1", "forwardInputNodeId2", "forwardInputNodeId3" })
            .registerModelInput(registerModelInput)
            .build();
    }

    @Test
    public void writeTo_Success() throws IOException {

        MLForwardRequest request = MLForwardRequest.builder().forwardInput(forwardInput).build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        request.writeTo(bytesStreamOutput);
        request = new MLForwardRequest(bytesStreamOutput.bytes().streamInput());
        assertEquals("forwardInputTaskId", request.getForwardInput().getTaskId());
        assertEquals("forwardInputModelId", request.getForwardInput().getModelId());
        assertEquals("forwardInputWorkerNodeId", request.getForwardInput().getWorkerNodeId());
        assertEquals(MLForwardRequestType.DEPLOY_MODEL_DONE, request.getForwardInput().getRequestType());
        assertEquals("forwardInputError", request.getForwardInput().getError());
        assertArrayEquals(
            new String[] { "forwardInputNodeId1", "forwardInputNodeId2", "forwardInputNodeId3" },
            request.getForwardInput().getWorkerNodes()
        );
        assertEquals(mlTask.getTaskId(), request.getForwardInput().getMlTask().getTaskId());
        assertEquals(modelInput.getAlgorithm().toString(), request.getForwardInput().getModelInput().getAlgorithm().toString());
        assertEquals(registerModelInput.getModelName(), request.getForwardInput().getRegisterModelInput().getModelName());
    }

    @Test
    public void validate_Success() {
        MLForwardRequest request = MLForwardRequest.builder().forwardInput(forwardInput).build();

        assertNull(request.validate());
    }

    @Test
    public void validate_Exception_NullMLInput() {
        MLForwardRequest request = MLForwardRequest.builder().build();
        ActionRequestValidationException exception = request.validate();
        assertEquals("Validation Failed: 1: ML input can't be null;", exception.getMessage());
    }

    @Test
    // MLForwardInput check its parameters when created, so exception is not thrown here
    public void validate_Exception_NullMLModelName() {
        forwardInput.setTaskId(null);
        MLForwardRequest request = MLForwardRequest.builder().forwardInput(forwardInput).build();

        assertNull(request.validate());
        assertNull(request.getForwardInput().getTaskId());
    }

    @Test
    public void fromActionRequest_Success_WithMLForwardRequest() {
        MLForwardRequest request = MLForwardRequest.builder().forwardInput(forwardInput).build();

        assertSame(MLForwardRequest.fromActionRequest(request), request);
    }

    @Test
    public void fromActionRequest_Success_WithNonMLForwardRequest() {
        MLForwardRequest request = MLForwardRequest.builder().forwardInput(forwardInput).build();
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                request.writeTo(out);
            }
        };
        MLForwardRequest result = MLForwardRequest.fromActionRequest(actionRequest);
        assertNotSame(result, request);
        assertEquals(request.getForwardInput().getTaskId(), result.getForwardInput().getTaskId());
        assertEquals(request.getForwardInput().getMlTask().getTaskId(), result.getForwardInput().getMlTask().getTaskId());
        assertEquals(
            request.getForwardInput().getModelInput().getAlgorithm().toString(),
            result.getForwardInput().getModelInput().getAlgorithm().toString()
        );
        assertEquals(
            request.getForwardInput().getRegisterModelInput().getModelName(),
            result.getForwardInput().getRegisterModelInput().getModelName()
        );
    }

    @Test(expected = UncheckedIOException.class)
    public void fromActionRequest_IOException() {
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException("test");
            }
        };
        MLForwardRequest.fromActionRequest(actionRequest);
    }

}
