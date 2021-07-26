/*
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  The OpenSearch Contributors require contributions made to
 *  this file be licensed under the Apache-2.0 license or a
 *  compatible open source license.
 *
 *  Modifications Copyright OpenSearch Contributors. See
 *  GitHub history for details.
 */

package org.opensearch.ml.client;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionListener;
import org.opensearch.client.node.NodeClient;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskResponse;
import org.opensearch.ml.common.transport.search.SearchTaskAction;
import org.opensearch.ml.common.transport.search.SearchTaskRequest;
import org.opensearch.ml.common.transport.search.SearchTaskResponse;
import org.opensearch.ml.common.transport.training.MLTrainingTaskAction;
import org.opensearch.ml.common.transport.training.MLTrainingTaskRequest;
import org.opensearch.ml.common.transport.training.MLTrainingTaskResponse;
import org.opensearch.ml.common.transport.upload.UploadTaskAction;
import org.opensearch.ml.common.transport.upload.UploadTaskRequest;
import org.opensearch.ml.common.transport.upload.UploadTaskResponse;

import static org.junit.Assert.assertEquals;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MachineLearningNodeClientTest {

    @Mock(answer = RETURNS_DEEP_STUBS)
    NodeClient client;

    @Mock
    MLInputDataset input;

    @Mock
    DataFrame output;

    @Mock
    ActionListener<DataFrame> dataFrameActionListener;

    @Mock
    ActionListener<String> trainingActionListener;

    @Mock
    ActionListener<String> uploadActionListener;

    @Mock
    ActionListener<String> searchActionListener;

    @InjectMocks
    MachineLearningNodeClient machineLearningNodeClient;

    private final String bodyExample = "PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiIHN0YW5kYWxvbmU9InllcyI/Pgo8UE1NTCB4bWxucz0iaHR0cDovL3d3dy5kbWcub3JnL1BNTUwtNF80IiB4bWxuczpkYXRhPSJodHRwOi8vanBtbWwub3JnL2pwbW1sLW1vZGVsL0lubGluZVRhYmxlIiB2ZXJzaW9uPSI0LjQiPgoJPEhlYWRlcj4KCQk8QXBwbGljYXRpb24gbmFtZT0iSlBNTUwtU2tMZWFybiIgdmVyc2lvbj0iMS42LjE4Ii8+CgkJPFRpbWVzdGFtcD4yMDIxLTA3LTE5VDIzOjIwOjI4WjwvVGltZXN0YW1wPgoJPC9IZWFkZXI+Cgk8RGF0YURpY3Rpb25hcnk+CgkJPERhdGFGaWVsZCBuYW1lPSJ4MSIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZmxvYXQiLz4KCTwvRGF0YURpY3Rpb25hcnk+Cgk8VHJhbnNmb3JtYXRpb25EaWN0aW9uYXJ5Lz4KCTxNaW5pbmdNb2RlbCBmdW5jdGlvbk5hbWU9InJlZ3Jlc3Npb24iIGFsZ29yaXRobU5hbWU9InNrbGVhcm4uZW5zZW1ibGUuX2lmb3Jlc3QuSXNvbGF0aW9uRm9yZXN0Ij4KCQk8TWluaW5nU2NoZW1hPgoJCQk8TWluaW5nRmllbGQgbmFtZT0ieDEiLz4KCQk8L01pbmluZ1NjaGVtYT4KCQk8T3V0cHV0PgoJCQk8T3V0cHV0RmllbGQgbmFtZT0icmF3QW5vbWFseVNjb3JlIiBvcHR5cGU9ImNvbnRpbnVvdXMiIGRhdGFUeXBlPSJkb3VibGUiIGlzRmluYWxSZXN1bHQ9ImZhbHNlIi8+CgkJCTxPdXRwdXRGaWVsZCBuYW1lPSJub3JtYWxpemVkQW5vbWFseVNjb3JlIiBvcHR5cGU9ImNvbnRpbnVvdXMiIGRhdGFUeXBlPSJkb3VibGUiIGZlYXR1cmU9InRyYW5zZm9ybWVkVmFsdWUiIGlzRmluYWxSZXN1bHQ9ImZhbHNlIj4KCQkJCTxBcHBseSBmdW5jdGlvbj0iLyI+CgkJCQkJPEZpZWxkUmVmIGZpZWxkPSJyYXdBbm9tYWx5U2NvcmUiLz4KCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+Mi4zMjcwMjAwNTIwNDI4NDc8L0NvbnN0YW50PgoJCQkJPC9BcHBseT4KCQkJPC9PdXRwdXRGaWVsZD4KCQkJPE91dHB1dEZpZWxkIG5hbWU9ImRlY2lzaW9uRnVuY3Rpb24iIG9wdHlwZT0iY29udGludW91cyIgZGF0YVR5cGU9ImRvdWJsZSIgZmVhdHVyZT0idHJhbnNmb3JtZWRWYWx1ZSI+CgkJCQk8QXBwbHkgZnVuY3Rpb249Ii0iPgoJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4wLjU8L0NvbnN0YW50PgoJCQkJCTxBcHBseSBmdW5jdGlvbj0icG93Ij4KCQkJCQkJPENvbnN0YW50IGRhdGFUeXBlPSJkb3VibGUiPjIuMDwvQ29uc3RhbnQ+CgkJCQkJCTxBcHBseSBmdW5jdGlvbj0iKiI+CgkJCQkJCQk8Q29uc3RhbnQgZGF0YVR5cGU9ImRvdWJsZSI+LTEuMDwvQ29uc3RhbnQ+CgkJCQkJCQk8RmllbGRSZWYgZmllbGQ9Im5vcm1hbGl6ZWRBbm9tYWx5U2NvcmUiLz4KCQkJCQkJPC9BcHBseT4KCQkJCQk8L0FwcGx5PgoJCQkJPC9BcHBseT4KCQkJPC9PdXRwdXRGaWVsZD4KCQkJPE91dHB1dEZpZWxkIG5hbWU9Im91dGxpZXIiIG9wdHlwZT0iY2F0ZWdvcmljYWwiIGRhdGFUeXBlPSJib29sZWFuIiBmZWF0dXJlPSJ0cmFuc2Zvcm1lZFZhbHVlIj4KCQkJCTxBcHBseSBmdW5jdGlvbj0ibGVzc09yRXF1YWwiPgoJCQkJCTxGaWVsZFJlZiBmaWVsZD0iZGVjaXNpb25GdW5jdGlvbiIvPgoJCQkJCTxDb25zdGFudCBkYXRhVHlwZT0iZG91YmxlIj4wLjA8L0NvbnN0YW50PgoJCQkJPC9BcHBseT4KCQkJPC9PdXRwdXRGaWVsZD4KCQk8L091dHB1dD4KCQk8TG9jYWxUcmFuc2Zvcm1hdGlvbnM+CgkJCTxEZXJpdmVkRmllbGQgbmFtZT0iZG91YmxlKHgxKSIgb3B0eXBlPSJjb250aW51b3VzIiBkYXRhVHlwZT0iZG91YmxlIj4KCQkJCTxGaWVsZFJlZiBmaWVsZD0ieDEiLz4KCQkJPC9EZXJpdmVkRmllbGQ+CgkJPC9Mb2NhbFRyYW5zZm9ybWF0aW9ucz4KCQk8U2VnbWVudGF0aW9uIG11bHRpcGxlTW9kZWxNZXRob2Q9ImF2ZXJhZ2UiPgoJCQk8U2VnbWVudCBpZD0iMSI+CgkJCQk8VHJ1ZS8+CgkJCQk8VHJlZU1vZGVsIGZ1bmN0aW9uTmFtZT0icmVncmVzc2lvbiIgbWlzc2luZ1ZhbHVlU3RyYXRlZ3k9Im51bGxQcmVkaWN0aW9uIiBub1RydWVDaGlsZFN0cmF0ZWd5PSJyZXR1cm5MYXN0UHJlZGljdGlvbiI+CgkJCQkJPE1pbmluZ1NjaGVtYT4KCQkJCQkJPE1pbmluZ0ZpZWxkIG5hbWU9ImRvdWJsZSh4MSkiLz4KCQkJCQk8L01pbmluZ1NjaGVtYT4KCQkJCQk8Tm9kZSBzY29yZT0iMS4wIj4KCQkJCQkJPFRydWUvPgoJCQkJCQk8Tm9kZSBzY29yZT0iMy4wIj4KCQkJCQkJCTxTaW1wbGVQcmVkaWNhdGUgZmllbGQ9ImRvdWJsZSh4MSkiIG9wZXJhdG9yPSJsZXNzT3JFcXVhbCIgdmFsdWU9IjIuMjU2NTYwNzQyNTA4ODgxNCIvPgoJCQkJCQkJPE5vZGUgc2NvcmU9IjMuMCI+CgkJCQkJCQkJPFNpbXBsZVByZWRpY2F0ZSBmaWVsZD0iZG91YmxlKHgxKSIgb3BlcmF0b3I9Imxlc3NPckVxdWFsIiB2YWx1ZT0iMS45NjkzMTc0NzU3ODUxODM1Ii8+CgkJCQkJCQk8L05vZGU+CgkJCQkJCTwvTm9kZT4KCQkJCQk8L05vZGU+CgkJCQk8L1RyZWVNb2RlbD4KCQkJPC9TZWdtZW50PgoJCQk8U2VnbWVudCBpZD0iMiI+CgkJCQk8VHJ1ZS8+CgkJCQk8VHJlZU1vZGVsIGZ1bmN0aW9uTmFtZT0icmVncmVzc2lvbiIgbWlzc2luZ1ZhbHVlU3RyYXRlZ3k9Im51bGxQcmVkaWN0aW9uIiBub1RydWVDaGlsZFN0cmF0ZWd5PSJyZXR1cm5MYXN0UHJlZGljdGlvbiI+CgkJCQkJPE1pbmluZ1NjaGVtYT4KCQkJCQkJPE1pbmluZ0ZpZWxkIG5hbWU9ImRvdWJsZSh4MSkiLz4KCQkJCQk8L01pbmluZ1NjaGVtYT4KCQkJCQk8Tm9kZSBzY29yZT0iMi4wIj4KCQkJCQkJPFRydWUvPgoJCQkJCQk8Tm9kZSBzY29yZT0iMi4wIj4KCQkJCQkJCTxTaW1wbGVQcmVkaWNhdGUgZmllbGQ9ImRvdWJsZSh4MSkiIG9wZXJhdG9yPSJsZXNzT3JFcXVhbCIgdmFsdWU9IjEuNDQ0MzAyMjE5MTczMDgwMyIvPgoJCQkJCQk8L05vZGU+CgkJCQkJCTxOb2RlIHNjb3JlPSIzLjAiPgoJCQkJCQkJPFNpbXBsZVByZWRpY2F0ZSBmaWVsZD0iZG91YmxlKHgxKSIgb3BlcmF0b3I9Imxlc3NPckVxdWFsIiB2YWx1ZT0iMi45OTIxMDc2NDkzMzAwOTk4Ii8+CgkJCQkJCTwvTm9kZT4KCQkJCQk8L05vZGU+CgkJCQk8L1RyZWVNb2RlbD4KCQkJPC9TZWdtZW50PgoJCQk8U2VnbWVudCBpZD0iMyI+CgkJCQk8VHJ1ZS8+CgkJCQk8VHJlZU1vZGVsIGZ1bmN0aW9uTmFtZT0icmVncmVzc2lvbiIgbWlzc2luZ1ZhbHVlU3RyYXRlZ3k9Im51bGxQcmVkaWN0aW9uIiBub1RydWVDaGlsZFN0cmF0ZWd5PSJyZXR1cm5MYXN0UHJlZGljdGlvbiI+CgkJCQkJPE1pbmluZ1NjaGVtYT4KCQkJCQkJPE1pbmluZ0ZpZWxkIG5hbWU9ImRvdWJsZSh4MSkiLz4KCQkJCQk8L01pbmluZ1NjaGVtYT4KCQkJCQk8Tm9kZSBzY29yZT0iMi4wIj4KCQkJCQkJPFRydWUvPgoJCQkJCQk8Tm9kZSBzY29yZT0iMi4wIj4KCQkJCQkJCTxTaW1wbGVQcmVkaWNhdGUgZmllbGQ9ImRvdWJsZSh4MSkiIG9wZXJhdG9yPSJsZXNzT3JFcXVhbCIgdmFsdWU9IjEuNDAxMTYyMDIxOTgwMjMwOCIvPgoJCQkJCQk8L05vZGU+CgkJCQkJCTxOb2RlIHNjb3JlPSIzLjAiPgoJCQkJCQkJPFNpbXBsZVByZWRpY2F0ZSBmaWVsZD0iZG91YmxlKHgxKSIgb3BlcmF0b3I9Imxlc3NPckVxdWFsIiB2YWx1ZT0iMi42MDkwNDIzOTA5OTg5MzgiLz4KCQkJCQkJPC9Ob2RlPgoJCQkJCTwvTm9kZT4KCQkJCTwvVHJlZU1vZGVsPgoJCQk8L1NlZ21lbnQ+CgkJCTxTZWdtZW50IGlkPSI0Ij4KCQkJCTxUcnVlLz4KCQkJCTxUcmVlTW9kZWwgZnVuY3Rpb25OYW1lPSJyZWdyZXNzaW9uIiBtaXNzaW5nVmFsdWVTdHJhdGVneT0ibnVsbFByZWRpY3Rpb24iIG5vVHJ1ZUNoaWxkU3RyYXRlZ3k9InJldHVybkxhc3RQcmVkaWN0aW9uIj4KCQkJCQk8TWluaW5nU2NoZW1hPgoJCQkJCQk8TWluaW5nRmllbGQgbmFtZT0iZG91YmxlKHgxKSIvPgoJCQkJCTwvTWluaW5nU2NoZW1hPgoJCQkJCTxOb2RlIHNjb3JlPSIyLjAiPgoJCQkJCQk8VHJ1ZS8+CgkJCQkJCTxOb2RlIHNjb3JlPSIyLjAiPgoJCQkJCQkJPFNpbXBsZVByZWRpY2F0ZSBmaWVsZD0iZG91YmxlKHgxKSIgb3BlcmF0b3I9Imxlc3NPckVxdWFsIiB2YWx1ZT0iMS4xNTA1NjkxNTQwMTk3MzI1Ii8+CgkJCQkJCTwvTm9kZT4KCQkJCQkJPE5vZGUgc2NvcmU9IjMuMCI+CgkJCQkJCQk8U2ltcGxlUHJlZGljYXRlIGZpZWxkPSJkb3VibGUoeDEpIiBvcGVyYXRvcj0ibGVzc09yRXF1YWwiIHZhbHVlPSIyLjc2OTA0MjIzOTg4MjUzNyIvPgoJCQkJCQk8L05vZGU+CgkJCQkJPC9Ob2RlPgoJCQkJPC9UcmVlTW9kZWw+CgkJCTwvU2VnbWVudD4KCQkJPFNlZ21lbnQgaWQ9IjUiPgoJCQkJPFRydWUvPgoJCQkJPFRyZWVNb2RlbCBmdW5jdGlvbk5hbWU9InJlZ3Jlc3Npb24iIG1pc3NpbmdWYWx1ZVN0cmF0ZWd5PSJudWxsUHJlZGljdGlvbiIgbm9UcnVlQ2hpbGRTdHJhdGVneT0icmV0dXJuTGFzdFByZWRpY3Rpb24iPgoJCQkJCTxNaW5pbmdTY2hlbWE+CgkJCQkJCTxNaW5pbmdGaWVsZCBuYW1lPSJkb3VibGUoeDEpIi8+CgkJCQkJPC9NaW5pbmdTY2hlbWE+CgkJCQkJPE5vZGUgc2NvcmU9IjEuMCI+CgkJCQkJCTxUcnVlLz4KCQkJCQkJPE5vZGUgc2NvcmU9IjMuMCI+CgkJCQkJCQk8U2ltcGxlUHJlZGljYXRlIGZpZWxkPSJkb3VibGUoeDEpIiBvcGVyYXRvcj0ibGVzc09yRXF1YWwiIHZhbHVlPSIyLjIwNjI1NDk5NTA1ODQwOTQiLz4KCQkJCQkJCTxOb2RlIHNjb3JlPSIzLjAiPgoJCQkJCQkJCTxTaW1wbGVQcmVkaWNhdGUgZmllbGQ9ImRvdWJsZSh4MSkiIG9wZXJhdG9yPSJsZXNzT3JFcXVhbCIgdmFsdWU9IjEuMjM4ODg0MTM3MTIzMzAzNSIvPgoJCQkJCQkJPC9Ob2RlPgoJCQkJCQk8L05vZGU+CgkJCQkJPC9Ob2RlPgoJCQkJPC9UcmVlTW9kZWw+CgkJCTwvU2VnbWVudD4KCQk8L1NlZ21lbnRhdGlvbj4KCTwvTWluaW5nTW9kZWw+CjwvUE1NTD4K";

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void predict() {
        doAnswer(invocation -> {
            ActionListener<MLPredictionTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLPredictionTaskResponse.builder()
                .status("Success")
                .predictionResult(output)
                .taskId("taskId")
                .build());
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(), any());

        ArgumentCaptor<DataFrame> dataFrameArgumentCaptor = ArgumentCaptor.forClass(DataFrame.class);
        machineLearningNodeClient.predict("algo", null, input, null, dataFrameActionListener);

        verify(client).execute(eq(MLPredictionTaskAction.INSTANCE), isA(MLPredictionTaskRequest.class),
            any(ActionListener.class));
        verify(dataFrameActionListener).onResponse(dataFrameArgumentCaptor.capture());
        assertEquals(output, dataFrameArgumentCaptor.getValue());
    }

    @Test
    public void predict_Exception_WithNullAlgorithm() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("algorithm name can't be null or empty");
        machineLearningNodeClient.predict(null, null, input, null, dataFrameActionListener);
    }

    @Test
    public void predict_Exception_WithNullDataSet() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("input data set can't be null");
        machineLearningNodeClient.predict("algo", null, (MLInputDataset) null, null, dataFrameActionListener);
    }

    @Test
    public void train() {
        doAnswer(invocation -> {
            ActionListener<MLTrainingTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLTrainingTaskResponse.builder()
                .status("InProgress")
                .taskId("taskId")
                .build());
            return null;
        }).when(client).execute(eq(MLTrainingTaskAction.INSTANCE), any(), any());

        ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);

        machineLearningNodeClient.train("algo", null, input, trainingActionListener);

        verify(client).execute(eq(MLTrainingTaskAction.INSTANCE), isA(MLTrainingTaskRequest.class),
            any(ActionListener.class));
        verify(trainingActionListener).onResponse(argumentCaptor.capture());
        assertEquals("taskId", argumentCaptor.getValue());
    }

    @Test
    public void train_Exception_WithNullAlgorithm() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("algorithm name can't be null or empty");
        machineLearningNodeClient.train(null, null, input, trainingActionListener);
    }

    @Test
    public void train_Exception_WithNullDataSet() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("input data set can't be null");
        machineLearningNodeClient.train("algo", null, (MLInputDataset) null, trainingActionListener);
    }

    @Test
    public void upload() {
        doAnswer(invocation -> {
            ActionListener<UploadTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(UploadTaskResponse.builder()
                .modelId("modelId")
                .build());
            return null;
        }).when(client).execute(eq(UploadTaskAction.INSTANCE), any(), any());

        ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
        machineLearningNodeClient.upload(
            "test",
            "pmml",
            "isolationforest",
            bodyExample,
            uploadActionListener
        );

        verify(client).execute(eq(UploadTaskAction.INSTANCE), isA(UploadTaskRequest.class),
            any(ActionListener.class));
        verify(uploadActionListener).onResponse(argumentCaptor.capture());
        assertEquals("modelId", argumentCaptor.getValue());
    }

    @Test
    public void upload_Exception_WithNullName() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model name can't be null or empty");
        machineLearningNodeClient.upload(null, "pmml", "isolationforest", bodyExample, uploadActionListener);
    }

    @Test
    public void upload_Exception_WithNullFormat() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model format can't be null or empty");
        machineLearningNodeClient.upload("test", null, "isolationforest", bodyExample, uploadActionListener);
    }

    @Test
    public void upload_Exception_WithNullAlgorithm() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("algorithm name can't be null or empty");
        machineLearningNodeClient.upload("test", "pmml", null, bodyExample, uploadActionListener);
    }

    @Test
    public void upload_Exception_WithEmptyBody() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model body can't be null or empty");
        machineLearningNodeClient.upload("test", "pmml", "isolationforest", "", uploadActionListener);
    }

    @Test
    public void upload_Exception_WithInvalidBody() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("can't retrieve model from body passed in");
        machineLearningNodeClient.upload("test", "pmml", "isolationforest", "123", uploadActionListener);
    }

    @Test
    public void search() {
        doAnswer(invocation -> {
            ActionListener<SearchTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(SearchTaskResponse.builder()
                .models("[]")
                .build());
            return null;
        }).when(client).execute(eq(SearchTaskAction.INSTANCE), any(), any());

        ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
        machineLearningNodeClient.search(
            null,
            "pmml",
            null,
            null,
            searchActionListener
        );

        verify(client).execute(eq(SearchTaskAction.INSTANCE), isA(SearchTaskRequest.class),
            any(ActionListener.class));
        verify(searchActionListener).onResponse(argumentCaptor.capture());
        assertEquals("[]", argumentCaptor.getValue());
    }
}