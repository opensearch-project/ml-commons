/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.IndexInsight;

import static org.opensearch.ml.common.CommonValue.INDEX_INSIGHT_INDEX_NAME;
import static org.opensearch.ml.common.CommonValue.ML_INDEX_INSIGHT_CONFIG_INDEX;
import static org.opensearch.ml.common.indexInsight.IndexInsight.CONTENT_FIELD;
import static org.opensearch.ml.common.indexInsight.IndexInsight.INDEX_NAME_FIELD;
import static org.opensearch.ml.common.indexInsight.IndexInsight.LAST_UPDATE_FIELD;
import static org.opensearch.ml.common.indexInsight.IndexInsight.STATUS_FIELD;
import static org.opensearch.ml.common.indexInsight.IndexInsight.TASK_TYPE_FIELD;
import static org.opensearch.ml.engine.encryptor.EncryptorImpl.DEFAULT_TENANT_ID;
import static org.opensearch.ml.helper.ConnectorAccessControlHelper.isAdmin;

import java.util.HashMap;
import java.util.Map;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.admin.indices.create.CreateIndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.support.clustermanager.AcknowledgedResponse;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
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
    private Client client;
    private final SdkClient sdkClient;
    private NamedXContentRegistry xContentRegistry;
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;
    private final MLIndicesHandler mlIndicesHandler;

    @Inject
    public PutIndexInsightConfigTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        NamedXContentRegistry xContentRegistry,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        Client client,
        SdkClient sdkClient,
        MLIndicesHandler mlIndicesHandler
    ) {
        super(MLIndexInsightConfigPutAction.NAME, transportService, actionFilters, MLIndexInsightConfigPutRequest::new);
        this.client = client;

        this.xContentRegistry = xContentRegistry;
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
                    initIndexInsightIndex(INDEX_INSIGHT_INDEX_NAME, ActionListener.wrap(r2 -> {
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
            String docId = indexInsightConfig.getTenantId();
            if (docId == null) {
                docId = DEFAULT_TENANT_ID;
            }
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

    private void initIndexInsightIndex(String indexName, ActionListener<Boolean> listener) {
        Map<String, Object> indexSettings = new HashMap<>();
        Map<String, Object> indexMappings = new HashMap<>();

        // Build index mappings based on semantic storage config
        Map<String, Object> properties = new HashMap<>();

        // Common fields for all index types
        // Use keyword type for ID fields that need exact matching
        properties.put(INDEX_NAME_FIELD, Map.of("type", "keyword"));
        properties.put(CONTENT_FIELD, Map.of("type", "text"));
        properties.put(STATUS_FIELD, Map.of("type", "keyword"));
        properties.put(TASK_TYPE_FIELD, Map.of("type", "text")); // Keep as text for full-text search
        properties.put(LAST_UPDATE_FIELD, Map.of("type", "date", "format", "strict_date_time||epoch_millis"));
        indexMappings.put("properties", properties);
        client
            .admin()
            .indices()
            .create(new CreateIndexRequest(indexName).settings(indexSettings).mapping(indexMappings), ActionListener.wrap(response -> {
                if (response.isAcknowledged()) {
                    log.info("Successfully created index insight data index: {}", indexName);
                    listener.onResponse(true);
                } else {
                    listener.onFailure(new RuntimeException("Failed to create index insight data index: " + indexName));
                }
            }, e -> {
                if (e instanceof org.opensearch.ResourceAlreadyExistsException) {
                    log.info("index insight data index already exists: {}", indexName);
                    listener.onResponse(true);
                } else {
                    log.error("Error creating index insight data index: {}", indexName, e);
                    listener.onFailure(e);
                }
            }));
    }
}
