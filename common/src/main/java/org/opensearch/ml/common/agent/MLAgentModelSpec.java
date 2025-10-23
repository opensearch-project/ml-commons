/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.utils.StringUtils.getParameterMap;

import java.io.IOException;
import java.util.Map;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Specification for model configuration in agent registration
 */
@EqualsAndHashCode
@Getter
public class MLAgentModelSpec implements ToXContentObject {
    public static final String MODEL_ID_FIELD = "model_id";
    public static final String MODEL_PROVIDER_FIELD = "model_provider";
    public static final String CREDENTIAL_FIELD = "credential";
    public static final String MODEL_PARAMETERS_FIELD = "model_parameters";

    private final String modelId;
    private final String modelProvider;
    private Map<String, String> credential;
    private Map<String, String> modelParameters;

    @Builder(toBuilder = true)
    public MLAgentModelSpec(String modelId, String modelProvider, Map<String, String> credential, Map<String, String> modelParameters) {
        if (modelId == null) {
            throw new IllegalArgumentException("model_id must be provided");
        }

        if (modelProvider == null) {
            throw new IllegalArgumentException("model_provider must be provided");
        }
        this.modelId = modelId;
        this.modelProvider = modelProvider;
        this.credential = credential;
        this.modelParameters = modelParameters;
    }

    public MLAgentModelSpec(StreamInput input) throws IOException {
        modelId = input.readString();
        modelProvider = input.readString();
        if (input.readBoolean()) {
            credential = input.readMap(StreamInput::readString, StreamInput::readOptionalString);
        }
        if (input.readBoolean()) {
            modelParameters = input.readMap(StreamInput::readString, StreamInput::readOptionalString);
        }
    }

    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(modelId);
        out.writeString(modelProvider);
        if (credential != null && !credential.isEmpty()) {
            out.writeBoolean(true);
            out.writeMap(credential, StreamOutput::writeString, StreamOutput::writeOptionalString);
        } else {
            out.writeBoolean(false);
        }
        if (modelParameters != null && !modelParameters.isEmpty()) {
            out.writeBoolean(true);
            out.writeMap(modelParameters, StreamOutput::writeString, StreamOutput::writeOptionalString);
        } else {
            out.writeBoolean(false);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (modelId != null) {
            builder.field(MODEL_ID_FIELD, modelId);
        }
        if (modelProvider != null) {
            builder.field(MODEL_PROVIDER_FIELD, modelProvider);
        }
        if (credential != null && !credential.isEmpty()) {
            builder.field(CREDENTIAL_FIELD, credential);
        }
        if (modelParameters != null && !modelParameters.isEmpty()) {
            builder.field(MODEL_PARAMETERS_FIELD, modelParameters);
        }
        builder.endObject();
        return builder;
    }

    public static MLAgentModelSpec parse(XContentParser parser) throws IOException {
        String model = null;
        String modelProvider = null;
        Map<String, String> credential = null;
        Map<String, String> modelParameters = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case MODEL_ID_FIELD:
                    model = parser.text();
                    break;
                case MODEL_PROVIDER_FIELD:
                    modelProvider = parser.text();
                    break;
                case CREDENTIAL_FIELD:
                    credential = getParameterMap(parser.map());
                    break;
                case MODEL_PARAMETERS_FIELD:
                    modelParameters = getParameterMap(parser.map());
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return new MLAgentModelSpec(model, modelProvider, credential, modelParameters);
    }

    public static MLAgentModelSpec fromStream(StreamInput in) throws IOException {
        return new MLAgentModelSpec(in);
    }
}
