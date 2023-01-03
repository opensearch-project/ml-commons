package org.opensearch.ml.common.transport.forward;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamInput;
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
import org.opensearch.ml.common.transport.upload.MLUploadInput;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.function.Consumer;

import static org.junit.Assert.*;


@RunWith(MockitoJUnitRunner.class)
public class MLForwardInputTest {

    private MLForwardInput forwardInput;
    private final FunctionName functionName = FunctionName.KMEANS;


    @Before
    public void setUp() throws Exception {
        Instant time = Instant.now();
        MLTask mlTask = MLTask.builder()
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

        DataFrame dataFrame = DataFrameBuilder.load(Collections.singletonList(new HashMap<String, Object>() {{
            put("key1", 2.0D);
        }}));
        MLInput modelInput = MLInput.builder()
                .algorithm(FunctionName.KMEANS)
                .parameters(KMeansParams.builder().centroids(1).build())
                .inputDataset(DataFrameInputDataset.builder().dataFrame(dataFrame).build())
                .build();
        MLModelConfig config = TextEmbeddingModelConfig.builder()
                .modelType("uploadInputModelType")
                .allConfig("{\"field1\":\"value1\",\"field2\":\"value2\"}")
                .frameworkType(TextEmbeddingModelConfig.FrameworkType.SENTENCE_TRANSFORMERS)
                .embeddingDimension(100)
                .build();
        MLUploadInput uploadInput = MLUploadInput.builder()
                .functionName(functionName)
                .modelName("uploadInputModelName")
                .version("uploadInputVersion")
                .url("url")
                .modelFormat(MLModelFormat.ONNX)
                .modelConfig(config)
                .loadModel(true)
                .modelNodeIds(new String[]{"modelNodeIds"})
                .build();

        forwardInput = MLForwardInput.builder()
                .taskId("forwardInputTaskId")
                .modelId("forwardInputModelId")
                .workerNodeId("forwardInputWorkerNodeId")
                .requestType(MLForwardRequestType.LOAD_MODEL_DONE)
                .mlTask(mlTask)
                .modelInput(modelInput)
                .error("forwardInputError")
                .workerNodes(new String [] {"forwardInputNodeId1", "forwardInputNodeId2", "forwardInputNodeId3"})
                .uploadInput(uploadInput)
                .build();
    }

    @Test
    public void readInputStream_Success() throws IOException {
        readInputStream(forwardInput, parsedInput -> {
            assertEquals(forwardInput.getTaskId(), parsedInput.getTaskId());
            assertEquals(forwardInput.getModelId(), parsedInput.getModelId());
        });
    }


    @Test
    public void readInputStream_SuccessWithNullFields() throws IOException {
        forwardInput.setMlTask(null);
        forwardInput.setModelInput(null);
        forwardInput.setUploadInput(null);
        readInputStream(forwardInput, parsedInput -> {
            assertNull(parsedInput.getMlTask());
            assertNull(parsedInput.getModelInput());
            assertNull(parsedInput.getUploadInput());
        });
    }


    private void readInputStream(MLForwardInput input, Consumer<MLForwardInput> verify) throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        input.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLForwardInput parsedInput = new MLForwardInput(streamInput);
        verify.accept(parsedInput);
    }
}
