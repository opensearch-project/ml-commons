/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.config;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_CONFIG_INDEX;
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.MLConfig;
import org.opensearch.ml.common.transport.config.MLConfigGetAction;
import org.opensearch.ml.common.transport.config.MLConfigGetRequest;
import org.opensearch.ml.common.transport.config.MLConfigGetResponse;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.sdk.GetDataObjectRequest;
import org.opensearch.sdk.SdkClient;
import org.opensearch.sdk.SdkClientUtils;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class GetConfigTransportAction extends HandledTransportAction<ActionRequest, MLConfigGetResponse> {

    Client client;
    SdkClient sdkClient;
    NamedXContentRegistry xContentRegistry;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public GetConfigTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        NamedXContentRegistry xContentRegistry,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLConfigGetAction.NAME, transportService, actionFilters, MLConfigGetRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.xContentRegistry = xContentRegistry;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLConfigGetResponse> actionListener) {
        MLConfigGetRequest mlConfigGetRequest = MLConfigGetRequest.fromActionRequest(request);
        String configId = mlConfigGetRequest.getConfigId();
        String tenantId = mlConfigGetRequest.getTenantId();

        FetchSourceContext fetchSourceContext = new FetchSourceContext(true, Strings.EMPTY_ARRAY, Strings.EMPTY_ARRAY);
        GetDataObjectRequest getDataObjectRequest = GetDataObjectRequest
            .builder()
            .index(ML_CONFIG_INDEX)
            .id(configId)
            .tenantId(tenantId)
            .fetchSourceContext(fetchSourceContext)
            .build();

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            sdkClient
                .getDataObjectAsync(getDataObjectRequest, client.threadPool().executor(GENERAL_THREAD_POOL))
                .whenComplete((r, throwable) -> {
                    context.restore();
                    log.debug("Completed Get Config Request, id:{}", configId);
                    if (throwable != null) {
                        Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                        if (cause instanceof IndexNotFoundException) {
                            log.error("Failed to get config index", cause);
                            actionListener.onFailure(new OpenSearchStatusException("Failed to get config index", RestStatus.NOT_FOUND));
                        } else {
                            log.error("Failed to get ML config {}", configId, cause);
                            actionListener.onFailure(cause);
                        }
                    } else {
                        try {
                            GetResponse gr = r.parser() == null ? null : GetResponse.fromXContent(r.parser());
                            if (gr != null && gr.isExists()) {
                                try (
                                    XContentParser parser = jsonXContent
                                        .createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, gr.getSourceAsString())
                                ) {
                                    ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                                    MLConfig mlConfig = MLConfig.parse(parser);
                                    if (!TenantAwareHelper
                                        .validateTenantResource(
                                            mlFeatureEnabledSetting,
                                            tenantId,
                                            mlConfig.getTenantId(),
                                            actionListener
                                        )) {
                                        return;
                                    }
                                    actionListener.onResponse(MLConfigGetResponse.builder().mlConfig(mlConfig).build());
                                } catch (Exception e) {
                                    log.error("Failed to parse ml config{}", gr.getId(), e);
                                    actionListener.onFailure(e);
                                }
                            } else {
                                actionListener
                                    .onFailure(
                                        new OpenSearchStatusException(
                                            "Failed to find config with the provided config id: " + configId,
                                            RestStatus.NOT_FOUND
                                        )
                                    );
                            }
                        } catch (Exception e) {
                            log.error("Failed to get ML config {}", configId, e);
                            actionListener.onFailure(e);
                        }
                    }
                });
        } catch (Exception e) {
            log.error("Failed to get ML config {}", configId, e);
            actionListener.onFailure(e);
        }
    }
}
