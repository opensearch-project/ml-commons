/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.DIMENSION_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.EMBEDDING_MODEL_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.EMBEDDING_MODEL_TYPE_FIELD;

import java.io.IOException;
import java.util.Map;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.connector.Connector;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Remote store configuration for storing memory in remote locations like AWS OpenSearch Serverless
 */
@Data
@EqualsAndHashCode
public class RemoteStore implements ToXContentObject, Writeable {

    public static final String TYPE_FIELD = "type";
    public static final String CONNECTOR_FIELD = "connector";
    public static final String CONNECTOR_ID_FIELD = "connector_id";
    public static final String ENDPOINT_FIELD = "endpoint";
    public static final String PARAMETERS_FIELD = "parameters";
    public static final String CREDENTIAL_FIELD = "credential";
    public static final String EMBEDDING_MODEL_FIELD = "embedding_model";
    public static final String INGEST_PIPELINE_FIELD = "ingest_pipeline";
    public static final String SEARCH_PIPELINE_FIELD = "search_pipeline";

    private RemoteStoreType type;
    private Connector connector;
    private String connectorId;
    private FunctionName embeddingModelType;
    private String embeddingModelId;
    private Integer embeddingDimension;

    // Auto-connector creation fields
    private String endpoint;
    private Map<String, String> parameters;
    private Map<String, String> credential;

    // Auto embedding model creation
    private RemoteEmbeddingModel embeddingModel;

    // Optional pre-existing pipeline configuration
    private String ingestPipeline;
    private String searchPipeline;

    @Builder
    public RemoteStore(
        RemoteStoreType type,
        Connector connector,
        String connectorId,
        FunctionName embeddingModelType,
        String embeddingModelId,
        Integer embeddingDimension,
        String endpoint,
        Map<String, String> parameters,
        Map<String, String> credential,
        RemoteEmbeddingModel embeddingModel,
        String ingestPipeline,
        String searchPipeline
    ) {
        if (type == null) {
            throw new IllegalArgumentException("Invalid remote store type");
        }
        this.type = type;
        this.connector = connector;
        this.connectorId = connectorId;
        this.embeddingModelType = embeddingModelType;
        this.embeddingModelId = embeddingModelId;
        this.embeddingDimension = embeddingDimension;
        this.endpoint = endpoint;
        this.parameters = parameters != null ? new java.util.HashMap<>(parameters) : new java.util.HashMap<>();
        this.credential = credential != null ? new java.util.HashMap<>(credential) : new java.util.HashMap<>();
        this.embeddingModel = embeddingModel;
        this.ingestPipeline = ingestPipeline;
        this.searchPipeline = searchPipeline;
    }

    public RemoteStore(StreamInput input) throws IOException {
        this.type = input.readEnum(RemoteStoreType.class);
        if (input.readBoolean()) {
            this.connector = Connector.fromStream(input);
        }
        this.connectorId = input.readOptionalString();
        if (input.readOptionalBoolean()) {
            this.embeddingModelType = input.readEnum(FunctionName.class);
        }
        this.embeddingModelId = input.readOptionalString();
        this.embeddingDimension = input.readOptionalInt();
        this.endpoint = input.readOptionalString();
        if (input.readBoolean()) {
            this.parameters = input.readMap(StreamInput::readString, StreamInput::readString);
        } else {
            this.parameters = new java.util.HashMap<>();
        }
        if (input.readBoolean()) {
            this.credential = input.readMap(StreamInput::readString, StreamInput::readString);
        } else {
            this.credential = new java.util.HashMap<>();
        }
        if (input.readBoolean()) {
            this.embeddingModel = new RemoteEmbeddingModel(input);
        }
        this.ingestPipeline = input.readOptionalString();
        this.searchPipeline = input.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeEnum(type);
        if (connector != null) {
            out.writeBoolean(true);
            connector.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalString(connectorId);
        if (embeddingModelType != null) {
            out.writeBoolean(true);
            out.writeEnum(embeddingModelType);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalString(embeddingModelId);
        out.writeOptionalInt(embeddingDimension);
        out.writeOptionalString(endpoint);
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
        if (embeddingModel != null) {
            out.writeBoolean(true);
            embeddingModel.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalString(ingestPipeline);
        out.writeOptionalString(searchPipeline);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (type != null) {
            builder.field(TYPE_FIELD, type);
        }
        if (connector != null) {
            builder.field(CONNECTOR_FIELD, connector);
        }
        if (connectorId != null) {
            builder.field(CONNECTOR_ID_FIELD, connectorId);
        }
        if (embeddingModelType != null) {
            builder.field(EMBEDDING_MODEL_TYPE_FIELD, embeddingModelType);
        }
        if (embeddingModelId != null) {
            builder.field(EMBEDDING_MODEL_ID_FIELD, embeddingModelId);
        }
        if (embeddingDimension != null) {
            builder.field(DIMENSION_FIELD, embeddingDimension);
        }
        if (endpoint != null) {
            builder.field(ENDPOINT_FIELD, endpoint);
        }
        if (parameters != null && !parameters.isEmpty()) {
            builder.field(PARAMETERS_FIELD, parameters);
        }
        if (embeddingModel != null) {
            builder.field(EMBEDDING_MODEL_FIELD, embeddingModel);
        }
        if (ingestPipeline != null) {
            builder.field(INGEST_PIPELINE_FIELD, ingestPipeline);
        }
        if (searchPipeline != null) {
            builder.field(SEARCH_PIPELINE_FIELD, searchPipeline);
        }
        // Don't serialize credentials for security - they are stored in the connector
        builder.endObject();
        return builder;
    }

    public static RemoteStore parse(XContentParser parser) throws IOException {
        RemoteStoreType type = null;
        Connector connector = null;
        String connectorId = null;
        FunctionName embeddingModelType = null;
        String embeddingModelId = null;
        Integer embeddingDimension = null;
        String endpoint = null;
        Map<String, String> parameters = new java.util.HashMap<>();
        Map<String, String> credential = new java.util.HashMap<>();
        RemoteEmbeddingModel embeddingModel = null;
        String ingestPipeline = null;
        String searchPipeline = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case TYPE_FIELD:
                    type = RemoteStoreType.fromString(parser.text());
                    break;
                case CONNECTOR_FIELD:
                    connector = Connector.createConnector(parser);
                    break;
                case CONNECTOR_ID_FIELD:
                    connectorId = parser.text();
                    break;
                case EMBEDDING_MODEL_TYPE_FIELD:
                    embeddingModelType = FunctionName.from(parser.text());
                    break;
                case EMBEDDING_MODEL_ID_FIELD:
                    embeddingModelId = parser.text();
                    break;
                case DIMENSION_FIELD:
                    embeddingDimension = parser.intValue();
                    break;
                case ENDPOINT_FIELD:
                    endpoint = parser.text();
                    break;
                case PARAMETERS_FIELD:
                    parameters = parser.mapStrings();
                    break;
                case CREDENTIAL_FIELD:
                    credential = parser.mapStrings();
                    break;
                case EMBEDDING_MODEL_FIELD:
                    embeddingModel = RemoteEmbeddingModel.parse(parser);
                    break;
                case INGEST_PIPELINE_FIELD:
                    ingestPipeline = parser.text();
                    break;
                case SEARCH_PIPELINE_FIELD:
                    searchPipeline = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return RemoteStore
            .builder()
            .type(type)
            .connector(connector)
            .connectorId(connectorId)
            .embeddingModelType(embeddingModelType)
            .embeddingModelId(embeddingModelId)
            .embeddingDimension(embeddingDimension)
            .endpoint(endpoint)
            .parameters(parameters)
            .credential(credential)
            .embeddingModel(embeddingModel)
            .ingestPipeline(ingestPipeline)
            .searchPipeline(searchPipeline)
            .build();
    }
}
