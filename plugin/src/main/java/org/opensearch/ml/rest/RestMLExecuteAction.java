/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.core.rest.RestStatus.BAD_REQUEST;
import static org.opensearch.core.rest.RestStatus.INTERNAL_SERVER_ERROR;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_EXECUTE_TOOL_DISABLED_MESSAGE;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.MLExceptionUtils.AGENT_FRAMEWORK_DISABLED_ERR_MSG;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_AGENT_ID;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_ALGORITHM;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_TOOL_NAME;
import static org.opensearch.ml.utils.RestActionUtils.getAlgorithm;
import static org.opensearch.ml.utils.RestActionUtils.isAsync;
import static org.opensearch.ml.utils.TenantAwareHelper.getTenantID;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.execute.agent.AgentMLInput;
import org.opensearch.ml.common.input.execute.tool.ToolMLInput;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskResponse;
import org.opensearch.ml.repackage.com.google.common.annotations.VisibleForTesting;
import org.opensearch.ml.repackage.com.google.common.collect.ImmutableList;
import org.opensearch.ml.utils.error.ErrorMessage;
import org.opensearch.ml.utils.error.ErrorMessageFactory;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class RestMLExecuteAction extends BaseRestHandler {
    private static final String ML_EXECUTE_ACTION = "ml_execute_action";
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    /**
     * Constructor
     */
    public RestMLExecuteAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return ML_EXECUTE_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList
            .of(
                new Route(RestRequest.Method.POST, String.format(Locale.ROOT, "%s/_execute/{%s}", ML_BASE_URI, PARAMETER_ALGORITHM)),
                new Route(RestRequest.Method.POST, String.format(Locale.ROOT, "%s/agents/{%s}/_execute", ML_BASE_URI, PARAMETER_AGENT_ID)),
                new Route(RestRequest.Method.POST, String.format(Locale.ROOT, "%s/tools/_execute/{%s}", ML_BASE_URI, PARAMETER_TOOL_NAME))
            );
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLExecuteTaskRequest mlExecuteTaskRequest = getRequest(request);

        return channel -> client.execute(MLExecuteTaskAction.INSTANCE, mlExecuteTaskRequest, new ActionListener<>() {
            @Override
            public void onResponse(MLExecuteTaskResponse response) {
                try {
                    sendResponse(channel, response);
                } catch (Exception e) {
                    reportError(channel, e, INTERNAL_SERVER_ERROR);
                }
            }

            @Override
            public void onFailure(Exception e) {
                RestStatus status;
                if (isClientError(e)) {
                    status = BAD_REQUEST;
                } else {
                    status = INTERNAL_SERVER_ERROR;
                }
                reportError(channel, e, status);
            }
        });
    }

    /**
     * Creates a MLExecuteTaskRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLExecuteTaskRequest
     */
    @VisibleForTesting
    MLExecuteTaskRequest getRequest(RestRequest request) throws IOException {
        XContentParser parser = request.contentParser();
        boolean async = isAsync(request);
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);

        String uri = request.getHttpRequest().uri();
        FunctionName functionName = null;
        Input input = null;
        if (uri.startsWith(ML_BASE_URI + "/agents/")) {
            if (!mlFeatureEnabledSetting.isAgentFrameworkEnabled()) {
                throw new IllegalStateException(AGENT_FRAMEWORK_DISABLED_ERR_MSG);
            }
            String tenantId = getTenantID(mlFeatureEnabledSetting.isMultiTenancyEnabled(), request);
            String agentId = request.param(PARAMETER_AGENT_ID);
            functionName = FunctionName.AGENT;
            input = MLInput.parse(parser, functionName.name());
            ((AgentMLInput) input).setAgentId(agentId);
            ((AgentMLInput) input).setTenantId(tenantId);
            ((AgentMLInput) input).setIsAsync(async);
        } else if (uri.startsWith(ML_BASE_URI + "/tools/")) {
            if (!mlFeatureEnabledSetting.isToolExecuteEnabled()) {
                throw new IllegalStateException(ML_COMMONS_EXECUTE_TOOL_DISABLED_MESSAGE);
            }
            String toolName = request.param(PARAMETER_TOOL_NAME);
            functionName = FunctionName.TOOL;
            input = MLInput.parse(parser, functionName.name());
            ((ToolMLInput) input).setToolName(toolName);
        } else {
            String algorithm = getAlgorithm(request).toUpperCase(Locale.ROOT);
            functionName = FunctionName.from(algorithm);
            input = parser.namedObject(Input.class, functionName.name(), null);
        }

        return new MLExecuteTaskRequest(functionName, input);
    }

    private void sendResponse(RestChannel channel, MLExecuteTaskResponse response) throws Exception {
        channel.sendResponse(new RestToXContentListener<MLExecuteTaskResponse>(channel).buildResponse(response));
    }

    private void reportError(final RestChannel channel, final Exception e, final RestStatus status) {
        ErrorMessage errorMessage = ErrorMessageFactory.createErrorMessage(e, status.getStatus());
        try {
            XContentBuilder builder = channel.newBuilder();
            builder.startObject();
            builder.field("status", errorMessage.getStatus());
            builder.startObject("error");
            builder.field("type", errorMessage.getType());
            builder.field("reason", errorMessage.getReason());
            builder.field("details", errorMessage.getDetails());
            builder.endObject();
            builder.endObject();
            channel.sendResponse(new BytesRestResponse(RestStatus.fromCode(errorMessage.getStatus()), builder));
        } catch (Exception exception) {
            log.error("Failed to build xContent for an error response, so reply with a plain string.", exception);
            channel.sendResponse(new BytesRestResponse(RestStatus.fromCode(errorMessage.getStatus()), errorMessage.toString()));
        }
    }

    private boolean isClientError(Exception e) {
        return e instanceof IllegalArgumentException || e instanceof IllegalAccessException;
    }
}
