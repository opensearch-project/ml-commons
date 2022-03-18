/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.execute;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.ml.action.MLCommonsIntegTestCase;
import org.opensearch.ml.common.parameter.FunctionName;
import org.opensearch.ml.common.parameter.Input;
import org.opensearch.ml.common.parameter.LocalSampleCalculatorInput;
import org.opensearch.ml.common.parameter.LocalSampleCalculatorOutput;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskResponse;
import org.opensearch.test.OpenSearchIntegTestCase;

import com.google.common.collect.ImmutableList;

@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.SUITE, numDataNodes = 3)
public class ExecuteITTests extends MLCommonsIntegTestCase {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    public void testExecuteLocalSampleCalculator() {
        Input input = new LocalSampleCalculatorInput("sum", ImmutableList.of(1.0, 2.0, 3.0));
        MLExecuteTaskRequest request = new MLExecuteTaskRequest(FunctionName.LOCAL_SAMPLE_CALCULATOR, input);
        MLExecuteTaskResponse executeTaskResponse = client().execute(MLExecuteTaskAction.INSTANCE, request).actionGet(5000);
        LocalSampleCalculatorOutput output = (LocalSampleCalculatorOutput) executeTaskResponse.getOutput();
        assertEquals(6.0, output.getResult(), 1e-5);
    }
}
