/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.plugin.grpc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.task.MLTaskRunner;
import org.opensearch.test.OpenSearchTestCase;

public class TaskRunnerAdapterTests extends OpenSearchTestCase {

    @Mock
    private MLTaskRunner<MLPredictionTaskRequest, MLTaskResponse> mockDelegate;

    @Mock
    private MLPredictionTaskRequest mockRequest;

    @Mock
    private ActionListener<?> mockListener;

    private TaskRunnerAdapter adapter;

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        adapter = new TaskRunnerAdapter((MLTaskRunner) mockDelegate);
    }

    @SuppressWarnings("unchecked")
    public void testCheckCBAndExecute_delegatesToRealRunner() {
        adapter.checkCBAndExecute(FunctionName.REMOTE, mockRequest, mockListener);

        verify(mockDelegate).checkCBAndExecute(eq(FunctionName.REMOTE), eq(mockRequest), any(ActionListener.class));
    }

    @SuppressWarnings("unchecked")
    public void testCheckCBAndExecute_withAgentFunction() {
        adapter.checkCBAndExecute(FunctionName.AGENT, mockRequest, mockListener);

        verify(mockDelegate).checkCBAndExecute(eq(FunctionName.AGENT), eq(mockRequest), any(ActionListener.class));
    }
}
