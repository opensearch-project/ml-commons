package org.opensearch.ml.common.transport.load;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DataFrameBuilder;
import org.opensearch.ml.common.dataset.MLInputDataType;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;

import static javax.swing.UIManager.put;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class LoadModelInputTest {

    private MLTask mlTask;
    private LoadModelInput loadModelInput;

    @Before
    public void setUp() throws Exception {
        Instant time = Instant.now();
        mlTask = MLTask.builder()
                .taskId("mlTaskTaskId")
                .modelId("mlTaskModelId")
                .taskType(MLTaskType.PREDICTION)
                .functionName(FunctionName.LINEAR_REGRESSION)
                .state(MLTaskState.RUNNING)
                .inputType(MLInputDataType.DATA_FRAME)
                .workerNode("node1")
                .progress(0.0f)
                .outputIndex("test_index")
                .error("test_error")
                .createTime(time.minus(1, ChronoUnit.MINUTES))
                .lastUpdateTime(time)
                .build();

        loadModelInput = LoadModelInput.builder()
                .modelId("loadModelInputModelId")
                .taskId("loadModelInputTaskId")
                .modelContentHash("modelContentHash")
                .nodeCount(3)
                .coordinatingNodeId("coordinatingNodeId")
                .mlTask(mlTask)
                .build();
    }

    @Test
    public void readInputStream() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        loadModelInput.writeTo(bytesStreamOutput);
        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        LoadModelInput parsedInput = new LoadModelInput(streamInput);
        assertEquals(loadModelInput.getModelId(), parsedInput.getModelId());
        assertEquals(loadModelInput.getTaskId(), parsedInput.getTaskId());
    }
}
