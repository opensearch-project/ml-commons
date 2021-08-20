/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.common.transport.prediction;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.Strings;
import org.opensearch.common.io.stream.InputStreamStreamInput;
import org.opensearch.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.MLInputDatasetReader;
import org.opensearch.ml.common.parameter.MLParameter;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

import static org.opensearch.action.ValidateActions.addValidationError;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class MLPredictionTaskRequest extends ActionRequest {

    /**
     * the name of algorithm
     */
    String algorithm;

    /**
     * list of ml parameters
     */
    List<MLParameter> parameters;

    /**
     * input data set
     */
    @ToString.Exclude
    MLInputDataset inputDataset;

    /**
     * Trained model id
     */
    String modelId;

    /**
     * version id, in case there is future schema change. This can be used to detect which version the client is using.
     */
    int version;

    @Builder
    public MLPredictionTaskRequest(String algorithm, List<MLParameter> parameters,
                                   String modelId, MLInputDataset inputDataset) {
        this.algorithm = algorithm;
        this.parameters = parameters;
        this.modelId = modelId;
        this.inputDataset = inputDataset;
        this.version = 1;
    }

    public MLPredictionTaskRequest(StreamInput in) throws IOException {
        super(in);
        this.algorithm = in.readString();
        this.parameters = in.readList(MLParameter::new);
        this.modelId = in.readOptionalString();
        this.inputDataset = new MLInputDatasetReader().read(in);
        this.version = in.readInt();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(this.algorithm);
        if (this.parameters != null) {
            out.writeList(this.parameters);
        } else {
            List<MLParameter> emptyParameters = new ArrayList<>();
            out.writeList(emptyParameters);
        }
        out.writeOptionalString(this.modelId);
        this.inputDataset.writeTo(out);
        out.writeInt(this.version);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if(Strings.isNullOrEmpty(this.algorithm)) {
            exception = addValidationError("algorithm name can't be null or empty", exception);
        }
        if(Objects.isNull(this.inputDataset)) {
            exception = addValidationError("input data can't be null", exception);
        }

        return exception;
    }

    public static MLPredictionTaskRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLPredictionTaskRequest) {
            return (MLPredictionTaskRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLPredictionTaskRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLPredictionTaskRequest", e);
        }
    }
}
