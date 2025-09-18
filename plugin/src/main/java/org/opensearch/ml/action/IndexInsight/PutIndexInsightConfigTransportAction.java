/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.IndexInsight;

import static org.opensearch.ml.common.CommonValue.ML_INDEX_INSIGHT_CONFIG_INDEX;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_INDEX_INSIGHT_FEATURE_ENABLED;
import static org.opensearch.ml.engine.encryptor.EncryptorImpl.DEFAULT_TENANT_ID;
import static org.opensearch.ml.helper.ConnectorAccessControlHelper.isAdmin;

import java.util.Optional;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.support.clustermanager.AcknowledgedResponse;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.MLIndex;
import org.opensearch.ml.common.indexInsight.IndexInsightConfig;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightConfigPutAction;
import org.opensearch.ml.common.transport.indexInsight.MLIndexInsightConfigPutRequest;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.PutDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

/**
 * This action will put a config object into system index, then
 * Create an index to store index insight using the provided index name if not exists.
 */

@Log4j2
public class PutIndexInsightConfigTransportAction extends HandledTransportAction<ActionRequest, AcknowledgedResponse> {
    private final Client client;
    private final SdkClient sdkClient;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    private final MLIndicesHandler mlIndicesHandler;

    @Inject
    public PutIndexInsightConfigTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        Client client,
        SdkClient sdkClient,
        MLIndicesHandler mlIndicesHandler
    ) {
        super(MLIndexInsightConfigPutAction.NAME, transportService, actionFilters, MLIndexInsightConfigPutRequest::new);
        this.client = client;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.sdkClient = sdkClient;
        this.mlIndicesHandler = mlIndicesHandler;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<AcknowledgedResponse> listener) {
        MLIndexInsightConfigPutRequest mlIndexInsightConfigPutRequest = MLIndexInsightConfigPutRequest.fromActionRequest(request);
        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, mlIndexInsightConfigPutRequest.getTenantId(), listener)) {
            return;
        }

        if (!this.mlFeatureEnabledSetting.isIndexInsightEnabled()) {
            listener
                .onFailure(
                    new RuntimeException(
                        "Index insight feature is not enabled yet. To enable, please update the setting "
                            + ML_COMMONS_INDEX_INSIGHT_FEATURE_ENABLED.getKey()
                    )
                );
            return;
        }

        User user = RestActionUtils.getUserContext(client);
        // Two scenario we can put config
        // 1. user is null: security is not enabled/super admin
        // 2. admin user
        if (user != null && !isAdmin(user)) {
            listener.onFailure(new RuntimeException("You don't have permission to put index insight config. Please contact admin user."));
            return;
        }

        String tenantId = mlIndexInsightConfigPutRequest.getTenantId();
        IndexInsightConfig indexInsightConfig = IndexInsightConfig
            .builder()
            .isEnable(mlIndexInsightConfigPutRequest.getIsEnable())
            .tenantId(tenantId)
            .build();

        mlIndicesHandler.initMLIndexIfAbsent(MLIndex.INDEX_INSIGHT_CONFIG, ActionListener.wrap(r -> {
            indexIndexInsightConfig(indexInsightConfig, ActionListener.wrap(r1 -> {
                if (indexInsightConfig.getIsEnable()) {
                    mlIndicesHandler.initMLIndexIfAbsent(MLIndex.INDEX_INSIGHT_STORAGE, ActionListener.wrap(r2 -> {
                        log.info("Successfully created index insight data index");
                        listener.onResponse(new AcknowledgedResponse(true));
                    }, e -> {
                        log.error("Failed to create index insight config", e);
                        listener.onFailure(e);
                    }));
                } else {
                    listener.onResponse(new AcknowledgedResponse(true));
                }
            }, listener::onFailure));
        }, listener::onFailure));

    }

    private void indexIndexInsightConfig(IndexInsightConfig indexInsightConfig, ActionListener<Boolean> listener) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            String docId = Optional.ofNullable(indexInsightConfig.getTenantId()).orElse(DEFAULT_TENANT_ID);
            sdkClient
                .putDataObjectAsync(
                    PutDataObjectRequest
                        .builder()
                        .tenantId(indexInsightConfig.getTenantId())
                        .index(ML_INDEX_INSIGHT_CONFIG_INDEX)
                        .dataObject(indexInsightConfig)
                        .id(docId)
                        .build()
                )
                .whenComplete((r, throwable) -> {
                    context.restore();
                    if (throwable != null) {
                        Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                        log.error("Failed to index index insight config", cause);
                        listener.onFailure(cause);
                    } else {
                        try {
                            IndexResponse indexResponse = r.indexResponse();
                            assert indexResponse != null;
                            if (indexResponse.getResult() == DocWriteResponse.Result.CREATED
                                || indexResponse.getResult() == DocWriteResponse.Result.UPDATED) {
                                String generatedId = indexResponse.getId();
                                log.info("Successfully created index insight with ID: {}", generatedId);
                                listener.onResponse(true);
                            } else {
                                listener.onFailure(new RuntimeException("Failed to create index insight config"));
                            }
                        } catch (Exception e) {
                            listener.onFailure(e);
                        }
                    }
                });
        } catch (Exception e) {
            log.error("Failed to create index insight config", e);
            listener.onFailure(e);
        }
    }
}
