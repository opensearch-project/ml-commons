/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.utils.StringUtils;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Configuration for embedding model in remote store
 * Allows automatic creation of embedding model in remote AOSS collection
 */
@Data
@EqualsAndHashCode
public class RemoteEmbeddingModel implements ToXContentObject, Writeable {

    public static final String PROVIDER_FIELD = "model_provider";
    public static final String MODEL_ID_FIELD = "model_id";
    public static final String MODEL_TYPE_FIELD = "model_type";
    public static final String DIMENSION_FIELD = "dimension";
    public static final String PARAMETERS_FIELD = "parameters";
    public static final String CREDENTIAL_FIELD = "credential";

    private String provider; // e.g., "bedrock", "openai", "cohere"
    private String modelId; // e.g., "amazon.titan-embed-text-v2:0"
    private FunctionName modelType; // TEXT_EMBEDDING or SPARSE_ENCODING
    private Integer dimension;
    private Map<String, String> parameters;
    private Map<String, String> credential;

    @Builder
    public RemoteEmbeddingModel(
        String provider,
        String modelId,
        FunctionName modelType,
        Integer dimension,
        Map<String, String> parameters,
        Map<String, String> credential
    ) {
        this.provider = provider;
        this.modelId = modelId;
        this.modelType = modelType;
        this.dimension = dimension;
        this.parameters = parameters != null ? new HashMap<>(parameters) : new HashMap<>();
        this.credential = credential != null ? new HashMap<>(credential) : new HashMap<>();
    }

    public RemoteEmbeddingModel(StreamInput input) throws IOException {
        this.provider = input.readOptionalString();
        this.modelId = input.readOptionalString();
        if (input.readOptionalBoolean()) {
            this.modelType = input.readEnum(FunctionName.class);
        }
        this.dimension = input.readOptionalInt();
        if (input.readBoolean()) {
            this.parameters = input.readMap(StreamInput::readString, StreamInput::readString);
        } else {
            this.parameters = new HashMap<>();
        }
        if (input.readBoolean()) {
            this.credential = input.readMap(StreamInput::readString, StreamInput::readString);
        } else {
            this.credential = new HashMap<>();
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(provider);
        out.writeOptionalString(modelId);
        if (modelType != null) {
            out.writeBoolean(true);
            out.writeEnum(modelType);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalInt(dimension);
        if (parameters != null && !parameters.isEmpty()) {
            out.writeBoolean(true);
            out.writeMap(parameters, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            out.writeBoolean(false);
        }
        if (credential != null && !credential.isEmpty()) {
            out.writeBoolean(true);
            out.writeMap(credential, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            out.writeBoolean(false);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (provider != null) {
            builder.field(PROVIDER_FIELD, provider);
        }
        if (modelId != null) {
            builder.field(MODEL_ID_FIELD, modelId);
        }
        if (modelType != null) {
            builder.field(MODEL_TYPE_FIELD, modelType);
        }
        if (dimension != null) {
            builder.field(DIMENSION_FIELD, dimension);
        }
        if (parameters != null && !parameters.isEmpty()) {
            builder.field(PARAMETERS_FIELD, parameters);
        }
        // Don't serialize credentials for security
        builder.endObject();
        return builder;
    }

    public static RemoteEmbeddingModel parse(XContentParser parser) throws IOException {
        String provider = null;
        String modelId = null;
        FunctionName modelType = null;
        Integer dimension = null;
        Map<String, String> parameters = new HashMap<>();
        Map<String, String> credential = new HashMap<>();

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case PROVIDER_FIELD:
                    provider = parser.text();
                    break;
                case MODEL_ID_FIELD:
                    modelId = parser.text();
                    break;
                case MODEL_TYPE_FIELD:
                    modelType = FunctionName.from(parser.text());
                    break;
                case DIMENSION_FIELD:
                    dimension = parser.intValue();
                    break;
                case PARAMETERS_FIELD:
                    parameters = StringUtils.getParameterMap(parser.map());
                    break;
                case CREDENTIAL_FIELD:
                    credential = StringUtils.getParameterMap(parser.map());
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return RemoteEmbeddingModel
            .builder()
            .provider(provider)
            .modelId(modelId)
            .modelType(modelType)
            .dimension(dimension)
            .parameters(parameters)
            .credential(credential)
            .build();
    }
}
