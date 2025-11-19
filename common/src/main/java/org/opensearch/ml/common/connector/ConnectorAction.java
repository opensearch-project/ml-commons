/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.VERSION_3_4_0;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.text.StringSubstitutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
    public static final String NAME_FIELD = "name";
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
    public static final String SAGEMAKER_PRE_POST_FUNC_TEXT = "default";
    public static final List<String> SUPPORTED_REMOTE_SERVERS_FOR_DEFAULT_ACTION_TYPES = List.of(SAGEMAKER, OPENAI, BEDROCK, COHERE);

    private static final String INBUILT_FUNC_PREFIX = "connector.";
    private static final String PRE_PROCESS_FUNC = "PreProcessFunction";
    private static final String POST_PROCESS_FUNC = "PostProcessFunction";
    private static final Logger logger = LogManager.getLogger(ConnectorAction.class);

    private ActionType actionType;
    private String name;
    private String method;
    private String url;
    private Map<String, String> headers;
    private String requestBody;
    private String preProcessFunction;
    private String postProcessFunction;

    @Builder(toBuilder = true)
    public ConnectorAction(
        ActionType actionType,
        String name,
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
        // The 'name' field is an optional identifier for this specific action within a connector.
        // It allows running a specific action by name when a connector has multiple actions of the same type.
        // We validate that 'name' is not an action type (PREDICT, EXECUTE, etc.) to avoid ambiguity
        // when resolving actions.
        if (name != null && ActionType.isValidAction(name)) {
            throw new IllegalArgumentException("name can't be one of action type " + Arrays.toString(ActionType.values()));
        }
        this.actionType = actionType;
        this.name = name;
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
        if (input.getVersion().onOrAfter(VERSION_3_4_0)) {
            this.name = input.readOptionalString();
        }
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
        if (out.getVersion().onOrAfter(VERSION_3_4_0)) {
            out.writeOptionalString(name);
        }
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
        if (name != null) {
            builder.field(NAME_FIELD, name);
        }
        return builder.endObject();
    }

    public static ConnectorAction fromStream(StreamInput in) throws IOException {
        ConnectorAction action = new ConnectorAction(in);
        return action;
    }

    public static ConnectorAction parse(XContentParser parser) throws IOException {
        ActionType actionType = null;
        String name = null;
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
                case NAME_FIELD:
                    name = parser.text();
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
            .name(name)
            .method(method)
            .url(url)
            .headers(headers)
            .requestBody(requestBody)
            .preProcessFunction(preProcessFunction)
            .postProcessFunction(postProcessFunction)
            .build();
    }

    /**
     * Checks the compatibility of pre and post-process functions with the selected LLM service.
     * Each LLM service (eg: Bedrock, OpenAI, SageMaker) has recommended pre and post-process functions
     * designed for optimal performance. While it's possible to use functions from other services,
     * it's strongly advised to use the corresponding functions for the best results.
     * This method logs a warning if non-corresponding functions are detected, but allows the
     * configuration to proceed. Users should be aware that using mismatched functions may lead
     * to unexpected behavior or reduced performance, though it won't necessarily cause failures.
     *
     * @param parameters - connector parameters
     */
    public void validatePrePostProcessFunctions(Map<String, String> parameters) {
        StringSubstitutor substitutor = new StringSubstitutor(parameters, "${parameters.", "}");
        String endPoint = substitutor.replace(url);
        String remoteServer = getRemoteServerFromURL(endPoint);
        if (!remoteServer.isEmpty()) {
            validateProcessFunctions(remoteServer, preProcessFunction, PRE_PROCESS_FUNC);
            validateProcessFunctions(remoteServer, postProcessFunction, POST_PROCESS_FUNC);
        }
    }

    /**
     * To get the remote server name from url
     *
     * @param url - remote server url
     * @return - returns the corresponding remote server name for url, if server is not in the pre-defined list,
     * it returns null
     */
    public static String getRemoteServerFromURL(String url) {
        return SUPPORTED_REMOTE_SERVERS_FOR_DEFAULT_ACTION_TYPES.stream().filter(url::contains).findFirst().orElse("");
    }

    private void validateProcessFunctions(String remoteServer, String processFunction, String funcNameForWarnText) {
        if (isInBuiltProcessFunction(processFunction)) {
            switch (remoteServer) {
                case OPENAI:
                    if (!processFunction.contains(OPENAI)) {
                        logWarningForInvalidProcessFunc(OPENAI, funcNameForWarnText);
                    }
                    break;
                case COHERE:
                    if (!processFunction.contains(COHERE)) {
                        logWarningForInvalidProcessFunc(COHERE, funcNameForWarnText);
                    }
                    break;
                case BEDROCK:
                    if (!processFunction.contains(BEDROCK)) {
                        logWarningForInvalidProcessFunc(BEDROCK, funcNameForWarnText);
                    }
                    break;
                case SAGEMAKER:
                    if (!processFunction.contains(SAGEMAKER_PRE_POST_FUNC_TEXT)) {
                        logWarningForInvalidProcessFunc(SAGEMAKER, funcNameForWarnText);
                    }
            }
        }
    }

    private boolean isInBuiltProcessFunction(String processFunction) {
        return (processFunction != null && processFunction.startsWith(INBUILT_FUNC_PREFIX));
    }

    private void logWarningForInvalidProcessFunc(String remoteServer, String funcNameForWarnText) {
        logger
            .warn(
                "LLM service is "
                    + remoteServer
                    + ", so "
                    + funcNameForWarnText
                    + " should be corresponding to "
                    + remoteServer
                    + " for better results."
            );
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
