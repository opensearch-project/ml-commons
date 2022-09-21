/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model.upload;

import lombok.Builder;
import lombok.Data;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.common.xcontent.ToXContentObject;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

/**
 * ML input data: algirithm name, parameters and input data set.
 */
@Data
public class MLUploadInput implements ToXContentObject, Writeable {

    public static final String ALGORITHM_FIELD = "algorithm";
    public static final String NAME_FIELD = "name";
    public static final String VERSION_FIELD = "version";
    public static final String URL_FIELD = "url";
    public static final String MODEL_FORMAT_FIELD = "model_format";
    public static final String MODEL_CONFIG_FIELD = "model_config";

    private String name;
    private Integer version;
    private String url;
    private MLModelFormat modelFormat;
    private MLModelConfig modelConfig;

    @Builder(toBuilder = true)
    public MLUploadInput(String name, Integer version, String url, MLModelFormat modelFormat, MLModelConfig modelConfig) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(version);
        Objects.requireNonNull(url);
        this.name = name;
        this.version = version;
        this.url = url;
        this.modelFormat = modelFormat;
        this.modelConfig = modelConfig;
    }


    public MLUploadInput(StreamInput in) throws IOException {
        this.name = in.readString();
        this.version = in.readInt();
        this.url = in.readString();
        this.modelFormat = in.readEnum(MLModelFormat.class);
        if (in.readBoolean()) {
            this.modelConfig = new TextEmbeddingModelConfig(in);
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(name);
        out.writeInt(version);
        out.writeString(url);
        out.writeEnum(modelFormat);
        if (modelConfig != null) {
            modelConfig.writeTo(out);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(NAME_FIELD, name);
        builder.field(VERSION_FIELD, version);
        builder.field(URL_FIELD, url);
        builder.field(MODEL_FORMAT_FIELD, modelFormat);
        if (modelConfig != null) {
            builder.field(MODEL_CONFIG_FIELD, modelConfig);
        }
        builder.endObject();
        return builder;
    }

    public static MLUploadInput parse(XContentParser parser) throws IOException {
        String name = null;
        Integer version = null;
        String url = null;
        MLModelFormat modelFormat = null;
        MLModelConfig modelConfig = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case NAME_FIELD:
                    name = parser.text();
                    break;
                case VERSION_FIELD:
                    version = parser.intValue();
                    break;
                case URL_FIELD:
                    url = parser.text();
                    break;
                case MODEL_FORMAT_FIELD:
                    modelFormat = MLModelFormat.from(parser.text().toUpperCase(Locale.ROOT));
                    break;
                case MODEL_CONFIG_FIELD:
                    modelConfig = TextEmbeddingModelConfig.parse(parser);
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new MLUploadInput(name, version, url, modelFormat, modelConfig);
    }


}
