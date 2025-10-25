/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.execute;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLCommonsClassLoader;
import org.opensearch.ml.common.output.AGUIOutput;
import org.opensearch.ml.common.output.Output;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;

import com.google.gson.Gson;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

@Getter
@ToString
public class MLExecuteTaskResponse extends ActionResponse implements ToXContentObject {

    FunctionName functionName;
    Output output;

    @Builder
    public MLExecuteTaskResponse(@NonNull FunctionName functionName, Output output) {
        this.functionName = functionName;
        this.output = output;
    }

    public MLExecuteTaskResponse(StreamInput in) throws IOException {
        super(in);
        this.functionName = in.readEnum(FunctionName.class);
        if (in.readBoolean()) {
            output = MLCommonsClassLoader.initExecuteOutputInstance(functionName, in, StreamInput.class);
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeEnum(functionName);
        if (output == null) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            output.writeTo(out);
        }
    }

    public static MLExecuteTaskResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLExecuteTaskResponse) {
            return (MLExecuteTaskResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLExecuteTaskResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionResponse into MLExecuteTaskResponse", e);
        }
    }

    @Override
    public XContentBuilder toXContent(final XContentBuilder builder, final Params params) throws IOException {
        if (functionName == FunctionName.AGENT && output instanceof ModelTensorOutput) {
            ModelTensorOutput modelOutput = (ModelTensorOutput) output;
            if (isAGUIOutput(modelOutput)) {
                return extractAGUIOutput(modelOutput).toXContent(builder, params);
            }
        }
        return output.toXContent(builder, params);
    }

    private boolean isAGUIOutput(ModelTensorOutput modelOutput) {
        if (modelOutput.getMlModelOutputs() != null && modelOutput.getMlModelOutputs().size() == 1) {
            ModelTensors modelTensors = modelOutput.getMlModelOutputs().get(0);
            if (modelTensors.getMlModelTensors() != null && modelTensors.getMlModelTensors().size() == 1) {
                return "ag_ui_events".equals(modelTensors.getMlModelTensors().get(0).getName());
            }
        }
        return false;
    }

    private AGUIOutput extractAGUIOutput(ModelTensorOutput modelOutput) {
        try {
            ModelTensors modelTensors = modelOutput.getMlModelOutputs().get(0);
            String eventsJson = modelTensors.getMlModelTensors().get(0).getResult();
            Gson gson = new Gson();
            List<Object> events = gson.fromJson(eventsJson, List.class);
            return AGUIOutput.builder().events(events).build();
        } catch (Exception e) {
            return AGUIOutput.builder().events(new ArrayList<>()).build();
        }
    }
}
