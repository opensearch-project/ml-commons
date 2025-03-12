/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.connector.MLPostProcessFunction.BEDROCK_BATCH_JOB_ARN;
import static org.opensearch.ml.common.connector.MLPostProcessFunction.BEDROCK_EMBEDDING;
import static org.opensearch.ml.common.connector.MLPostProcessFunction.BEDROCK_RERANK;
import static org.opensearch.ml.common.connector.MLPostProcessFunction.BEDROCK_V2_EMBEDDING_BINARY;
import static org.opensearch.ml.common.connector.MLPostProcessFunction.BEDROCK_V2_EMBEDDING_FLOAT;
import static org.opensearch.ml.common.connector.MLPostProcessFunction.COHERE_EMBEDDING;
import static org.opensearch.ml.common.connector.MLPostProcessFunction.COHERE_RERANK;
import static org.opensearch.ml.common.connector.MLPostProcessFunction.COHERE_V2_EMBEDDING_BINARY;
import static org.opensearch.ml.common.connector.MLPostProcessFunction.COHERE_V2_EMBEDDING_FLOAT32;
import static org.opensearch.ml.common.connector.MLPostProcessFunction.COHERE_V2_EMBEDDING_INT8;
import static org.opensearch.ml.common.connector.MLPostProcessFunction.COHERE_V2_EMBEDDING_UBINARY;
import static org.opensearch.ml.common.connector.MLPostProcessFunction.COHERE_V2_EMBEDDING_UINT8;
import static org.opensearch.ml.common.connector.MLPostProcessFunction.DEFAULT_EMBEDDING;
import static org.opensearch.ml.common.connector.MLPostProcessFunction.DEFAULT_RERANK;
import static org.opensearch.ml.common.connector.MLPostProcessFunction.OPENAI_EMBEDDING;
import static org.opensearch.ml.common.connector.MLPreProcessFunction.IMAGE_TO_COHERE_MULTI_MODAL_EMBEDDING_INPUT;
import static org.opensearch.ml.common.connector.MLPreProcessFunction.TEXT_DOCS_TO_BEDROCK_EMBEDDING_INPUT;
import static org.opensearch.ml.common.connector.MLPreProcessFunction.TEXT_DOCS_TO_COHERE_EMBEDDING_INPUT;
import static org.opensearch.ml.common.connector.MLPreProcessFunction.TEXT_DOCS_TO_DEFAULT_EMBEDDING_INPUT;
import static org.opensearch.ml.common.connector.MLPreProcessFunction.TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT;
import static org.opensearch.ml.common.connector.MLPreProcessFunction.TEXT_IMAGE_TO_BEDROCK_EMBEDDING_INPUT;
import static org.opensearch.ml.common.connector.MLPreProcessFunction.TEXT_SIMILARITY_TO_BEDROCK_RERANK_INPUT;
import static org.opensearch.ml.common.connector.MLPreProcessFunction.TEXT_SIMILARITY_TO_COHERE_RERANK_INPUT;
import static org.opensearch.ml.common.connector.MLPreProcessFunction.TEXT_SIMILARITY_TO_DEFAULT_INPUT;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.text.StringSubstitutor;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class ConnectorAction implements ToXContentObject, Writeable {

    public static final String ACTION_TYPE_FIELD = "action_type";
    public static final String METHOD_FIELD = "method";
    public static final String URL_FIELD = "url";
    public static final String HEADERS_FIELD = "headers";
    public static final String REQUEST_BODY_FIELD = "request_body";
    public static final String ACTION_PRE_PROCESS_FUNCTION = "pre_process_function";
    public static final String ACTION_POST_PROCESS_FUNCTION = "post_process_function";
    public static final String OPENAI = "openai";
    public static final String COHERE = "cohere";
    public static final String BEDROCK = "bedrock";
    public static final String SAGEMAKER = "sagemaker";
    public static final List<String> SUPPORTED_REMOTE_SERVERS_FOR_DEFAULT_ACTION_TYPES = List.of(SAGEMAKER, OPENAI, BEDROCK, COHERE);

    private static final String INBUILT_FUNC_PREFIX = "connector.";

    private ActionType actionType;
    private String method;
    private String url;
    private Map<String, String> headers;
    private String requestBody;
    private String preProcessFunction;
    private String postProcessFunction;

    @Builder(toBuilder = true)
    public ConnectorAction(
        ActionType actionType,
        String method,
        String url,
        Map<String, String> headers,
        String requestBody,
        String preProcessFunction,
        String postProcessFunction
    ) {
        if (actionType == null) {
            throw new IllegalArgumentException("action type can't be null");
        }
        if (url == null) {
            throw new IllegalArgumentException("url can't be null");
        }
        if (method == null) {
            throw new IllegalArgumentException("method can't be null");
        }
        this.actionType = actionType;
        this.method = method;
        this.url = url;
        this.headers = headers;
        this.requestBody = requestBody;
        this.preProcessFunction = preProcessFunction;
        this.postProcessFunction = postProcessFunction;
    }

    public ConnectorAction(StreamInput input) throws IOException {
        this.actionType = input.readEnum(ActionType.class);
        this.method = input.readString();
        this.url = input.readString();
        if (input.readBoolean()) {
            this.headers = input.readMap(StreamInput::readString, StreamInput::readString);
        }
        this.requestBody = input.readOptionalString();
        this.preProcessFunction = input.readOptionalString();
        this.postProcessFunction = input.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeEnum(actionType);
        out.writeString(method);
        out.writeString(url);
        if (headers != null) {
            out.writeBoolean(true);
            out.writeMap(headers, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalString(requestBody);
        out.writeOptionalString(preProcessFunction);
        out.writeOptionalString(postProcessFunction);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder xContentBuilder, Params params) throws IOException {
        XContentBuilder builder = xContentBuilder.startObject();
        if (actionType != null) {
            builder.field(ACTION_TYPE_FIELD, actionType);
        }
        if (method != null) {
            builder.field(METHOD_FIELD, method);
        }
        if (url != null) {
            builder.field(URL_FIELD, url);
        }
        if (headers != null) {
            builder.field(HEADERS_FIELD, headers);
        }
        if (requestBody != null) {
            builder.field(REQUEST_BODY_FIELD, requestBody);
        }
        if (preProcessFunction != null) {
            builder.field(ACTION_PRE_PROCESS_FUNCTION, preProcessFunction);
        }
        if (postProcessFunction != null) {
            builder.field(ACTION_POST_PROCESS_FUNCTION, postProcessFunction);
        }
        return builder.endObject();
    }

    public static ConnectorAction fromStream(StreamInput in) throws IOException {
        ConnectorAction action = new ConnectorAction(in);
        return action;
    }

    public static ConnectorAction parse(XContentParser parser) throws IOException {
        ActionType actionType = null;
        String method = null;
        String url = null;
        Map<String, String> headers = null;
        String requestBody = null;
        String preProcessFunction = null;
        String postProcessFunction = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case ACTION_TYPE_FIELD:
                    actionType = ActionType.valueOf(parser.text().toUpperCase(Locale.ROOT));
                    break;
                case METHOD_FIELD:
                    method = parser.text();
                    break;
                case URL_FIELD:
                    url = parser.text();
                    break;
                case HEADERS_FIELD:
                    headers = parser.mapStrings();
                    break;
                case REQUEST_BODY_FIELD:
                    requestBody = parser.text();
                    break;
                case ACTION_PRE_PROCESS_FUNCTION:
                    preProcessFunction = parser.text();
                    break;
                case ACTION_POST_PROCESS_FUNCTION:
                    postProcessFunction = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return ConnectorAction
            .builder()
            .actionType(actionType)
            .method(method)
            .url(url)
            .headers(headers)
            .requestBody(requestBody)
            .preProcessFunction(preProcessFunction)
            .postProcessFunction(postProcessFunction)
            .build();
    }

    public void validatePrePostProcessFunctions(Map<String, String> parameters) {
        StringSubstitutor substitutor = new StringSubstitutor(parameters, "${parameters.", "}");
        String endPoint = substitutor.replace(url);
        String remoteServer = getRemoteServerFromURL(endPoint);
        validatePreProcessFunctions(remoteServer);
        validatePostProcessFunctions(remoteServer);
    }

    private void validatePreProcessFunctions(String remoteServer) {
        if (isInBuiltFunction(preProcessFunction)) {
            switch (remoteServer) {
                case OPENAI:
                    if (!TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT.equals(preProcessFunction)) {
                        throw new IllegalArgumentException(
                            "LLM service is " + OPENAI + ", so PreProcessFunction should be " + TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT
                        );
                    }
                    break;
                case COHERE:
                    if (!(TEXT_DOCS_TO_COHERE_EMBEDDING_INPUT.equals(preProcessFunction)
                        || IMAGE_TO_COHERE_MULTI_MODAL_EMBEDDING_INPUT.equals(preProcessFunction)
                        || TEXT_SIMILARITY_TO_COHERE_RERANK_INPUT.equals(preProcessFunction))) {
                        throw new IllegalArgumentException(
                            "LLM service is "
                                + COHERE
                                + ", so PreProcessFunction should be "
                                + TEXT_DOCS_TO_COHERE_EMBEDDING_INPUT
                                + " or "
                                + IMAGE_TO_COHERE_MULTI_MODAL_EMBEDDING_INPUT
                                + " or "
                                + TEXT_SIMILARITY_TO_COHERE_RERANK_INPUT
                        );
                    }
                    break;
                case BEDROCK:
                    if (!(TEXT_DOCS_TO_BEDROCK_EMBEDDING_INPUT.equals(preProcessFunction)
                        || TEXT_IMAGE_TO_BEDROCK_EMBEDDING_INPUT.equals(preProcessFunction)
                        || TEXT_SIMILARITY_TO_BEDROCK_RERANK_INPUT.equals(preProcessFunction))) {
                        throw new IllegalArgumentException(
                            "LLM service is "
                                + BEDROCK
                                + ", so PreProcessFunction should be "
                                + TEXT_DOCS_TO_BEDROCK_EMBEDDING_INPUT
                                + " or "
                                + TEXT_IMAGE_TO_BEDROCK_EMBEDDING_INPUT
                                + " or "
                                + TEXT_SIMILARITY_TO_BEDROCK_RERANK_INPUT
                        );
                    }
                    break;
                case SAGEMAKER:
                    if (!(TEXT_DOCS_TO_DEFAULT_EMBEDDING_INPUT.equals(preProcessFunction)
                        || TEXT_SIMILARITY_TO_DEFAULT_INPUT.equals(preProcessFunction))) {
                        throw new IllegalArgumentException(
                            "LLM service is "
                                + SAGEMAKER
                                + ", so PreProcessFunction should be "
                                + TEXT_DOCS_TO_DEFAULT_EMBEDDING_INPUT
                                + " or "
                                + TEXT_SIMILARITY_TO_DEFAULT_INPUT
                        );
                    }
            }
        }
    }

    private void validatePostProcessFunctions(String remoteServer) {
        if (isInBuiltFunction(postProcessFunction)) {
            switch (remoteServer) {
                case OPENAI:
                    if (!OPENAI_EMBEDDING.equals(postProcessFunction)) {
                        throw new IllegalArgumentException(
                            "LLM service is " + OPENAI + ", so PostProcessFunction should be " + OPENAI_EMBEDDING
                        );
                    }
                    break;
                case COHERE:
                    if (!(COHERE_EMBEDDING.equals(postProcessFunction)
                        || COHERE_RERANK.equals(postProcessFunction)
                        || COHERE_V2_EMBEDDING_FLOAT32.equals(postProcessFunction)
                        || COHERE_V2_EMBEDDING_INT8.equals(postProcessFunction)
                        || COHERE_V2_EMBEDDING_UINT8.equals(postProcessFunction)
                        || COHERE_V2_EMBEDDING_BINARY.equals(postProcessFunction)
                        || COHERE_V2_EMBEDDING_UBINARY.equals(postProcessFunction))) {
                        throw new IllegalArgumentException(
                            "LLM service is "
                                + COHERE
                                + ", so PostProcessFunction should be "
                                + COHERE_EMBEDDING
                                + " or "
                                + COHERE_RERANK
                                + " or "
                                + COHERE_V2_EMBEDDING_FLOAT32
                                + " or "
                                + COHERE_V2_EMBEDDING_INT8
                                + " or "
                                + COHERE_V2_EMBEDDING_UINT8
                                + " or "
                                + COHERE_V2_EMBEDDING_BINARY
                                + " or "
                                + COHERE_V2_EMBEDDING_UBINARY
                        );
                    }
                    break;
                case BEDROCK:
                    if (!(BEDROCK_EMBEDDING.equals(postProcessFunction)
                        || BEDROCK_BATCH_JOB_ARN.equals(postProcessFunction)
                        || BEDROCK_RERANK.equals(postProcessFunction)
                        || BEDROCK_V2_EMBEDDING_FLOAT.equals(postProcessFunction)
                        || BEDROCK_V2_EMBEDDING_BINARY.equals(postProcessFunction))) {
                        throw new IllegalArgumentException(
                            "LLM service is "
                                + BEDROCK
                                + ", so PostProcessFunction should be "
                                + BEDROCK_EMBEDDING
                                + " or "
                                + BEDROCK_BATCH_JOB_ARN
                                + " or "
                                + BEDROCK_RERANK
                                + " or "
                                + BEDROCK_V2_EMBEDDING_FLOAT
                                + " or "
                                + BEDROCK_V2_EMBEDDING_BINARY
                        );
                    }
                    break;
                case SAGEMAKER:
                    if (!(DEFAULT_EMBEDDING.equals(postProcessFunction) || DEFAULT_RERANK.equals(postProcessFunction))) {
                        throw new IllegalArgumentException(
                            "LLM service is "
                                + SAGEMAKER
                                + ", so PostProcessFunction should be "
                                + DEFAULT_EMBEDDING
                                + " or "
                                + DEFAULT_RERANK
                        );
                    }
            }
        }
    }

    private boolean isInBuiltFunction(String function) {
        return (function != null && function.startsWith(INBUILT_FUNC_PREFIX));
    }

    public static String getRemoteServerFromURL(String url) {
        return SUPPORTED_REMOTE_SERVERS_FOR_DEFAULT_ACTION_TYPES.stream().filter(url::contains).findFirst().orElse("");
    }

    public enum ActionType {
        PREDICT,
        EXECUTE,
        BATCH_PREDICT,
        CANCEL_BATCH_PREDICT,
        BATCH_PREDICT_STATUS;

        public static ActionType from(String value) {
            try {
                return ActionType.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (Exception e) {
                throw new IllegalArgumentException("Wrong Action Type of " + value);
            }
        }

        private static final HashSet<ActionType> MODEL_SUPPORT_ACTIONS = new HashSet<>(Set.of(PREDICT, BATCH_PREDICT));

        public static boolean isValidActionInModelPrediction(ActionType actionType) {
            return MODEL_SUPPORT_ACTIONS.contains(actionType);
        }

        public static boolean isValidAction(String action) {
            try {
                ActionType.valueOf(action.toUpperCase(Locale.ROOT));
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
    }
}
