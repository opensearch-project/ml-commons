/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model.upload_chunk;

import lombok.Builder;
import lombok.Data;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.common.xcontent.ToXContentObject;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentParser;

import java.io.IOException;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

/**
 * ML input data: algorithm name, parameters and input data set.
 */
@Data
public class MLUploadChunkInput implements ToXContentObject, Writeable {

    public static final String CONTENT_FIELD = "model_content";
    public static final String MODEL_ID_FIELD = "model_id";
    public static final String CHUNK_NUMBER_FIELD = "chunk_number";

    private byte[] content;
    private String modelId;
    private Integer chunkNumber;

    @Builder(toBuilder = true)
    public MLUploadChunkInput(String modelId, Integer chunkNumber, byte[] content) {
        this.content = content;
        this.modelId = modelId;
        this.chunkNumber = chunkNumber;
    }


    public MLUploadChunkInput(StreamInput in) throws IOException {
        this.modelId = in.readString();
        this.chunkNumber = in.readInt();
        boolean uploadModel = in.readBoolean();
        if (uploadModel) {
            this.content = in.readByteArray();
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(modelId);
        out.writeInt(chunkNumber);
        if (content == null) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            out.writeByteArray(content);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(MODEL_ID_FIELD, modelId);
        builder.field(CHUNK_NUMBER_FIELD, chunkNumber);
        builder.field(CONTENT_FIELD, content);
        builder.endObject();
        return builder;
    }

    public static MLUploadChunkInput parse(XContentParser parser, byte[] content) throws IOException {
        Integer chunkNumber = -1;
        String modelId = null;
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case MODEL_ID_FIELD:
                    modelId = parser.text();
                    break;
                case CHUNK_NUMBER_FIELD:
                    chunkNumber = parser.intValue();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new MLUploadChunkInput(modelId, chunkNumber, content);
    }
}
