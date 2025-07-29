/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.agent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

/**
 * ML execute agent response.
 */
public class MLExecuteAgentResponse extends ActionResponse implements ToXContentObject {

    private List<Map<String, List<Map<String, String>>>> inferenceResults;

    public MLExecuteAgentResponse(StreamInput in) throws IOException {
        super(in);
        int size = in.readVInt();
        inferenceResults = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Map<String, List<Map<String, String>>> inferenceResult = new HashMap<>();
            int outputSize = in.readVInt();
            List<Map<String, String>> outputList = new ArrayList<>(outputSize);
            for (int j = 0; j < outputSize; j++) {
                Map<String, String> outputMap = new HashMap<>();
                if (in.readBoolean()) { // Check if 'name' field exists
                    outputMap.put("name", in.readString());
                }
                outputMap.put("result", in.readString());
                outputList.add(outputMap);
            }
            inferenceResult.put("output", outputList);
            inferenceResults.add(inferenceResult);
        }
    }

    public MLExecuteAgentResponse(List<Map<String, List<Map<String, String>>>> inferenceResults) {
        this.inferenceResults = inferenceResults;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeVInt(inferenceResults.size());
        for (Map<String, List<Map<String, String>>> inferenceResult : inferenceResults) {
            List<Map<String, String>> outputList = inferenceResult.get("output");
            out.writeVInt(outputList.size());
            for (Map<String, String> outputMap : outputList) {
                if (outputMap.containsKey("name")) {
                    out.writeBoolean(true);
                    out.writeString(outputMap.get("name"));
                } else {
                    out.writeBoolean(false);
                }
                out.writeString(outputMap.get("result"));
            }
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.startArray("inference_results");
        for (Map<String, List<Map<String, String>>> inferenceResult : inferenceResults) {
            builder.startObject();
            builder.startArray("output");
            for (Map<String, String> outputMap : inferenceResult.get("output")) {
                builder.startObject();
                if (outputMap.containsKey("name")) {
                    builder.field("name", outputMap.get("name"));
                }
                builder.field("result", outputMap.get("result"));
                builder.endObject();
            }
            builder.endArray();
            builder.endObject();
        }
        builder.endArray();
        builder.endObject();
        return builder;
    }

    public List<Map<String, List<Map<String, String>>>> getInferenceResults() {
        return this.inferenceResults;
    }
}
