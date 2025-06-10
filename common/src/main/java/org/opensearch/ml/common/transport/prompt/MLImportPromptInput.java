/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.prompt;

import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;

import java.io.IOException;
import java.util.Objects;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

@Data
public class MLImportPromptInput implements ToXContentObject, Writeable {
    public static final String PUBLIC_KEY = "public_key";
    public static final String ACCESS_KEY = "access_key";
    public static final String LIMIT = "limit";

    private String publicKey;
    private String accessKey;
    private String limit;
    private String tenantId;

    @Builder(toBuilder = true)
    public MLImportPromptInput(@NonNull String publicKey, @NonNull String accessKey, String limit, String tenantId) {
        Objects.requireNonNull(publicKey, "public key can not be null");
        Objects.requireNonNull(accessKey, "access key can not be null");
        this.publicKey = publicKey;
        this.accessKey = accessKey;
        this.limit = limit;
        this.tenantId = tenantId;
    }

    public MLImportPromptInput(StreamInput input) throws IOException {
        this.publicKey = input.readOptionalString();
        this.publicKey = input.readOptionalString();
        this.limit = input.readOptionalString();
        this.tenantId = input.readOptionalString();
    }

    public void writeTo(StreamOutput output) throws IOException {
        output.writeOptionalString(publicKey);
        output.writeOptionalString(accessKey);
        output.writeOptionalString(limit);
        output.writeOptionalString(tenantId);
    }

    public static MLImportPromptInput parse(XContentParser parser) throws IOException {
        String publicKey = null;
        String accessKey = null;
        String limit = null;
        String tenantId = null;

        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            switch (fieldName) {
                case PUBLIC_KEY:
                    publicKey = parser.text();
                    break;
                case ACCESS_KEY:
                    accessKey = parser.text();
                    break;
                case LIMIT:
                    limit = parser.text();
                    break;
                case TENANT_ID_FIELD:
                    tenantId = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return MLImportPromptInput.builder().publicKey(publicKey).accessKey(accessKey).limit(limit).tenantId(tenantId).build();
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (publicKey != null) {
            builder.field(PUBLIC_KEY, publicKey);
        }
        if (accessKey != null) {
            builder.field(ACCESS_KEY, accessKey);
        }
        if (limit != null) {
            builder.field(LIMIT, limit);
        }
        if (tenantId != null) {
            builder.field(TENANT_ID_FIELD, tenantId);
        }
        builder.endObject();
        return builder;
    }
}
