/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.upload_chunk;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.Builder;
import lombok.Data;

@Data
public class MLUploadModelChunkInput implements ToXContentObject, Writeable {

    public static final String CONTENT_FIELD = "model_content";
    public static final String MODEL_ID_FIELD = "model_id";
    public static final String CHUNK_NUMBER_FIELD = "chunk_number";

    private byte[] content;
    private String modelId;
    private Integer chunkNumber;

    @Builder(toBuilder = true)
    public MLUploadModelChunkInput(String modelId, Integer chunkNumber, byte[] content) {
        this.content = content;
        this.modelId = modelId;
        this.chunkNumber = chunkNumber;
    }

    public MLUploadModelChunkInput(StreamInput in) throws IOException {
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
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        builder.field(MODEL_ID_FIELD, modelId);
        builder.field(CHUNK_NUMBER_FIELD, chunkNumber);
        builder.field(CONTENT_FIELD, content);
        builder.endObject();
        return builder;
    }

    public static MLUploadModelChunkInput parse(XContentParser parser, byte[] content) throws IOException {
        Integer chunkNumber = null;
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
        return new MLUploadModelChunkInput(modelId, chunkNumber, content);
    }
}
