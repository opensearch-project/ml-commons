/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.functions.postprocess;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.output.model.ModelTensor;

public class BedrockBatchJobArnPostProcessFunction implements ConnectorPostProcessFunction {
    public static final String JOB_ARN = "jobArn";
    public static final String PROCESSED_JOB_ARN = "processedJobArn";

    @Override
    public void validate(Object input) {
        if (!(input instanceof Map)) {
            throw new IllegalArgumentException("Post process function input is not a Map.");
        }
        Map<String, String> jobInfo = (Map<String, String>) input;
        if (!(jobInfo.containsKey(JOB_ARN))) {
            throw new IllegalArgumentException("job arn is missing.");
        }
    }

    @Override
    public List<ModelTensor> process(Object input) {
        Map<String, String> jobInfo = (Map<String, String>) input;
        List<ModelTensor> modelTensors = new ArrayList<>();
        Map<String, String> processedResult = new HashMap<>();
        processedResult.putAll(jobInfo);
        String jobArn = jobInfo.get(JOB_ARN);
        processedResult.put(PROCESSED_JOB_ARN, jobArn.replace("/", "%2F"));
        modelTensors.add(ModelTensor.builder().name("response").dataAsMap(processedResult).build());
        return modelTensors;
    }
}
