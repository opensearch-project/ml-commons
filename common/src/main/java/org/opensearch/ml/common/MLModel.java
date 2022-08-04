/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import lombok.Builder;
import lombok.Getter;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.ToXContentObject;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.commons.authuser.User;

import java.io.IOException;
import java.util.Base64;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.USER;

@Getter
public class MLModel implements ToXContentObject {
    public static final String ALGORITHM_FIELD = "algorithm";
    public static final String MODEL_NAME_FIELD = "name";
    public static final String MODEL_VERSION_FIELD = "version";
    public static final String OLD_MODEL_CONTENT_FIELD = "content";
    public static final String MODEL_CONTENT_FIELD = "model_content";

    private String name;
    private FunctionName algorithm;
    private Integer version;
    private String content;
    private User user;

    @Builder
    public MLModel(String name, FunctionName algorithm, Integer version, String content, User user) {
        this.name = name;
        this.algorithm = algorithm;
        this.version = version;
        this.content = content;
        this.user = user;
    }

    public MLModel(FunctionName algorithm, Model model) {
        this(model.getName(), algorithm, model.getVersion(), Base64.getEncoder().encodeToString(model.getContent()), null);
    }

    public MLModel(StreamInput input) throws IOException{
        name = input.readOptionalString();
        algorithm = input.readEnum(FunctionName.class);
        version = input.readInt();
        content = input.readOptionalString();
        if (input.readBoolean()) {
            this.user = new User(input);
        } else {
            user = null;
        }
    }

    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(name);
        out.writeEnum(algorithm);
        out.writeInt(version);
        out.writeOptionalString(content);
        if (user != null) {
            out.writeBoolean(true); // user exists
            user.writeTo(out);
        } else {
            out.writeBoolean(false); // user does not exist
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (name != null) {
            builder.field(MODEL_NAME_FIELD, name);
        }
        if (algorithm != null) {
            builder.field(ALGORITHM_FIELD, algorithm);
        }
        if (version != null) {
            builder.field(MODEL_VERSION_FIELD, version);
        }
        if (content != null) {
            builder.field(MODEL_CONTENT_FIELD, content);
        }
        if (user != null) {
            builder.field(USER, user);
        }
        builder.endObject();
        return builder;
    }

    public static MLModel parse(XContentParser parser) throws IOException {
        String name = null;
        FunctionName algorithm = null;
        Integer version = null;
        String content = null;
        String oldContent = null;
        User user = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case MODEL_NAME_FIELD:
                    name = parser.text();
                    break;
                case MODEL_CONTENT_FIELD:
                    content = parser.text();
                    break;
                case OLD_MODEL_CONTENT_FIELD:
                    oldContent = parser.text();
                    break;
                case MODEL_VERSION_FIELD:
                    version = parser.intValue(false);
                    break;
                case USER:
                    user = User.parse(parser);
                    break;
                case ALGORITHM_FIELD:
                    algorithm = FunctionName.from(parser.text());
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return MLModel.builder()
                .name(name)
                .algorithm(algorithm)
                .version(version)
                .content(content == null ? oldContent : content)
                .user(user)
                .build();
        }

    public static MLModel fromStream(StreamInput in) throws IOException {
        MLModel mlModel = new MLModel(in);
        return mlModel;
    }
}
