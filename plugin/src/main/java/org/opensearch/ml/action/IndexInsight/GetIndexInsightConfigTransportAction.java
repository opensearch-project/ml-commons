/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.IndexInsight;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_INDEX_INSIGHT_CONFIG_INDEX;
import static org.opensearch.ml.engine.encryptor.EncryptorImpl.DEFAULT_TENANT_ID;

import java.util.Optional;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.indexInsight.IndexInsightConfig;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightConfigGetAction;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightConfigGetRequest;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightConfigGetResponse;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class GetIndexInsightConfigTransportAction extends HandledTransportAction<ActionRequest, MLIndexInsightConfigGetResponse> {
    private Client client;
    private SdkClient sdkClient;
    private NamedXContentRegistry xContentRegistry;
    private MLIndicesHandler mlIndicesHandler;
    private ClusterService clusterService;
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public GetIndexInsightConfigTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        NamedXContentRegistry xContentRegistry,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        Client client,
        SdkClient sdkClient,
        MLIndicesHandler mlIndicesHandler,
        ClusterService clusterService
    ) {
        super(MLIndexInsightConfigGetAction.NAME, transportService, actionFilters, MLIndexInsightConfigGetRequest::new);
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.mlIndicesHandler = mlIndicesHandler;
        this.clusterService = clusterService;
        this.sdkClient = sdkClient;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLIndexInsightConfigGetResponse> listener) {
        MLIndexInsightConfigGetRequest mlIndexInsightConfigGetRequest = MLIndexInsightConfigGetRequest.fromActionRequest(request);
        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, mlIndexInsightConfigGetRequest.getTenantId(), listener)) {
            return;
        }

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            String docId = Optional.ofNullable(mlIndexInsightConfigGetRequest.getTenantId()).orElse(DEFAULT_TENANT_ID);
            sdkClient
                .getDataObjectAsync(
                    GetDataObjectRequest
                        .builder()
                        .tenantId(mlIndexInsightConfigGetRequest.getTenantId())
                        .index(ML_INDEX_INSIGHT_CONFIG_INDEX)
                        .id(docId)
                        .build()
                )
                .whenComplete((r, throwable) -> {
                    context.restore();
                    if (throwable != null) {
                        Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                        log.error("Failed to get index insight config", cause);
                        listener.onFailure(cause);
                    } else {
                        try {
                            GetResponse getResponse = r.getResponse();
                            assert getResponse != null;
                            if (getResponse.isExists()) {
                                try (
                                    XContentParser parser = jsonXContent
                                        .createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, getResponse.getSourceAsString())
                                ) {
                                    ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                                    IndexInsightConfig indexInsightConfig = IndexInsightConfig.parse(parser);
                                    MLIndexInsightConfigGetResponse mlIndexInsightConfigGetResponse = new MLIndexInsightConfigGetResponse(
                                        indexInsightConfig
                                    );
                                    listener.onResponse(mlIndexInsightConfigGetResponse);
                                } catch (Exception e) {
                                    listener.onFailure(e);
                                }
                            } else {
                                listener.onFailure(new RuntimeException("Failed to get index insight config"));
                            }
                        } catch (Exception e) {
                            listener.onFailure(e);
                        }
                    }
                });
        } catch (Exception e) {
            log.error("Failed to get index insight config", e);
            listener.onFailure(e);
        }

    }
}
