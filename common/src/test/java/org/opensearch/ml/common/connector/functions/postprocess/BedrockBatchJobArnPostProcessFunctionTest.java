/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.functions.postprocess;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.opensearch.ml.common.connector.functions.postprocess.BedrockBatchJobArnPostProcessFunction.JOB_ARN;
import static org.opensearch.ml.common.connector.functions.postprocess.BedrockBatchJobArnPostProcessFunction.PROCESSED_JOB_ARN;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.output.model.ModelTensor;

public class BedrockBatchJobArnPostProcessFunctionTest {
    BedrockBatchJobArnPostProcessFunction function;

    @Before
    public void setUp() {
        function = new BedrockBatchJobArnPostProcessFunction();
    }

    @Test
    public void process_WrongInput_NotMap() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> function.apply("abc", null));
        assertEquals("Post process function input is not a Map.", exception.getMessage());
    }

    @Test
    public void process_WrongInput_NotContainJobArn() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> function.apply(Map.of("test", "value"), null)
        );
        assertEquals("job arn is missing.", exception.getMessage());
    }

    @Test
    public void process_CorrectInput() {
        String jobArn = "arn:aws:bedrock:us-east-1:12345678912:model-invocation-job/w1xtlm0ik3e1";
        List<ModelTensor> result = function.apply(Map.of(JOB_ARN, jobArn), null);
        assertEquals(1, result.size());
        assertEquals(jobArn, result.get(0).getDataAsMap().get(JOB_ARN));
        assertEquals(
            "arn:aws:bedrock:us-east-1:12345678912:model-invocation-job%2Fw1xtlm0ik3e1",
            result.get(0).getDataAsMap().get(PROCESSED_JOB_ARN)
        );
    }
}
