/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.config;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.MASTER_KEY;
import static org.opensearch.ml.common.CommonValue.ML_CONFIG_INDEX;
import static org.opensearch.ml.utils.MLNodeUtils.createXContentParserFromRegistry;

import org.opensearch.ExceptionsHelper;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.MLConfig;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.config.MLConfigGetAction;
import org.opensearch.ml.common.transport.config.MLConfigGetRequest;
import org.opensearch.ml.common.transport.config.MLConfigGetResponse;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

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
        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, actionListener)) {
            return;
        }

        // In the get request tenantId will be used as a part of SDKClient migration
        GetDataObjectRequest getDataObjectRequest = GetDataObjectRequest
            .builder()
            .index(ML_CONFIG_INDEX)
            .id(configId)
            .tenantId(tenantId)
            .build();

        if (configId.equals(MASTER_KEY)) {
            actionListener.onFailure(new OpenSearchStatusException("You are not allowed to access this config doc", RestStatus.FORBIDDEN));
            return;
        }

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<MLConfigGetResponse> wrappedListener = ActionListener.runBefore(actionListener, context::restore);
            sdkClient.getDataObjectAsync(getDataObjectRequest).whenComplete((r, throwable) -> {
                log.debug("Completed Get Agent Request, id:{}", configId);
                if (throwable != null) {
                    Exception e = SdkClientUtils.unwrapAndConvertToException(throwable);
                    if (ExceptionsHelper.unwrap(e, IndexNotFoundException.class) != null) {
                        wrappedListener.onFailure(new OpenSearchStatusException("Failed to get config index", RestStatus.NOT_FOUND));
                    } else {
                        log.error("Failed to get ML config {}", configId, e);
                        wrappedListener.onFailure(e);
                    }
                } else {
                    GetResponse response = r.getResponse();
                    if (response != null && response.isExists()) {
                        try (XContentParser parser = createXContentParserFromRegistry(xContentRegistry, response.getSourceAsBytesRef())) {
                            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                            MLConfig mlConfig = MLConfig.parse(parser);
                            wrappedListener.onResponse(MLConfigGetResponse.builder().mlConfig(mlConfig).build());
                        } catch (Exception e) {
                            log.error("Failed to parse ml config{}", response.getId(), e);
                            wrappedListener.onFailure(e);
                        }
                    } else {
                        wrappedListener
                            .onFailure(
                                new OpenSearchStatusException(
                                    "Failed to find config with the provided config id: " + configId,
                                    RestStatus.NOT_FOUND
                                )
                            );
                    }
                }
            });
        } catch (Exception e) {
            log.error("Failed to get ML config {}", configId, e);
            actionListener.onFailure(e);
        }
    }
}
