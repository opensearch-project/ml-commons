/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.controller;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.utils.StringUtils.getParameterMap;

@Data
public class MLModelController implements ToXContentObject, Writeable {

    public static final String MODEL_ID_FIELD = "model_id"; // mandatory
    public static final String USER_RATE_LIMITER_CONFIG = "user_rate_limiter_config";

    @Getter
    private String modelId;
    // The String is the username field where the MLRateLimiter is its corresponding rate limiter config.
    private Map<String, MLRateLimiter> userRateLimiterConfig;

    @Builder(toBuilder = true)
    public MLModelController(String modelId, Map<String, MLRateLimiter> userRateLimiterConfig) {
        this.modelId = modelId;
        this.userRateLimiterConfig = userRateLimiterConfig;
    }

    public static MLModelController parse(XContentParser parser) throws IOException {
        String modelId = null;
        Map<String, MLRateLimiter> userRateLimiterConfig = new HashMap<>();

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case MODEL_ID_FIELD:
                    modelId = parser.text();
                    break;
                case USER_RATE_LIMITER_CONFIG:
                    Map<String, String> userRateLimiterConfigStringMap = getParameterMap(parser.map());
                    userRateLimiterConfigStringMap.forEach((user, rateLimiterString) -> {
                        try {
                            XContentParser rateLimiterParser = XContentType.JSON.xContent().createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, rateLimiterString);
                            rateLimiterParser.nextToken();
                            MLRateLimiter rateLimiter = MLRateLimiter.parse(rateLimiterParser);
                            if (!rateLimiter.isEmpty()) {
                                userRateLimiterConfig.put(user, rateLimiter);
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        // Model ID can only be set through RestRequest.
        return new MLModelController(modelId, userRateLimiterConfig);
    }

    public MLModelController(StreamInput in) throws IOException{
        modelId = in.readString();
        if (in.readBoolean()) {
            userRateLimiterConfig = in.readMap(StreamInput::readString, MLRateLimiter::new);
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(modelId);
        if (userRateLimiterConfig != null) {
            out.writeBoolean(true);
            out.writeMap(userRateLimiterConfig, StreamOutput::writeString, (streamOutput, rateLimiter) -> rateLimiter.writeTo(streamOutput));
        } else {
            out.writeBoolean(false);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        builder.field(MODEL_ID_FIELD, modelId);
        if (userRateLimiterConfig != null) {
            builder.field(USER_RATE_LIMITER_CONFIG, userRateLimiterConfig);
        }
        builder.endObject();
        return builder;
    }


    /**
     * Checks if a deployment is required after updating the MLModelController.
     *
     * @param updateContent The updated MLModelController object.
     * @return True if a deployment is required, false otherwise.
     */
    public boolean isDeployRequiredAfterUpdate(MLModelController updateContent) {
        if (updateContent != null && updateContent.getUserRateLimiterConfig() != null && !updateContent.getUserRateLimiterConfig().isEmpty()) {
            Map<String, MLRateLimiter> updateUserRateLimiterConfig = updateContent.getUserRateLimiterConfig();
            for (Map.Entry<String, MLRateLimiter> entry : updateUserRateLimiterConfig.entrySet()) {
                String newUser = entry.getKey();
                MLRateLimiter newRateLimiter = entry.getValue();
                if (this.userRateLimiterConfig.containsKey(newUser)) {
                    MLRateLimiter oldRateLimiter = this.userRateLimiterConfig.get(newUser);
                    if (MLRateLimiter.isDeployRequiredAfterUpdate(oldRateLimiter, newRateLimiter)) {
                        return true;
                    }
                } else {
                    if (newRateLimiter.isValid()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void update(MLModelController updateContent) {
        Map<String, MLRateLimiter> updateUserRateLimiterConfig = updateContent.getUserRateLimiterConfig();
        if (updateUserRateLimiterConfig != null && !updateUserRateLimiterConfig.isEmpty()) {
            updateUserRateLimiterConfig.forEach((user, rateLimiter) -> {
                // rateLimiter can't be null due to parsing exception
                    if (this.userRateLimiterConfig.containsKey(user)) {
                        this.userRateLimiterConfig.get(user).update(rateLimiter);
                    } else {
                        this.userRateLimiterConfig.put(user, rateLimiter);
                    }
            });
        }
    }
}
