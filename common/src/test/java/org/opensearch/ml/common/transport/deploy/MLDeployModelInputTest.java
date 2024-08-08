package org.opensearch.ml.common.transport.deploy;

import static org.junit.Assert.*;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.dataset.MLInputDataType;

@RunWith(MockitoJUnitRunner.class)
public class MLDeployModelInputTest {

    private MLTask mlTask;
    private MLDeployModelInput mlDeployModelInput;

    @Before
    public void setUp() throws Exception {
        Instant time = Instant.now();
        mlTask = MLTask
            .builder()
            .taskId("mlTaskTaskId")
            .modelId("mlTaskModelId")
            .taskType(MLTaskType.PREDICTION)
            .functionName(FunctionName.LINEAR_REGRESSION)
            .state(MLTaskState.RUNNING)
            .inputType(MLInputDataType.DATA_FRAME)
            .workerNodes(Arrays.asList("node1"))
            .progress(0.0f)
            .outputIndex("test_index")
            .error("test_error")
            .createTime(time.minus(1, ChronoUnit.MINUTES))
            .lastUpdateTime(time)
            .build();

        mlDeployModelInput = mlDeployModelInput
            .builder()
            .modelId("testModelId")
            .taskId("testTaskId")
            .modelContentHash("modelContentHash")
            .nodeCount(3)
            .coordinatingNodeId("coordinatingNodeId")
            .mlTask(mlTask)
            .build();
    }

    @Test
    public void readInputStream() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        mlDeployModelInput.writeTo(bytesStreamOutput);
        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLDeployModelInput parsedInput = new MLDeployModelInput(streamInput);
        assertEquals(mlDeployModelInput.getModelId(), parsedInput.getModelId());
        assertEquals(mlDeployModelInput.getTaskId(), parsedInput.getTaskId());
    }
}
