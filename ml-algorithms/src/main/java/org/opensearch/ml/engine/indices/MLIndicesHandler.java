/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.indices;

import static org.opensearch.ml.common.CommonValue.META;
import static org.opensearch.ml.common.CommonValue.SCHEMA_VERSION_FIELD;
import static org.opensearch.ml.common.utils.IndexUtils.ALL_NODES_REPLICA_INDEX_SETTINGS;
import static org.opensearch.ml.common.utils.IndexUtils.DEFAULT_INDEX_SETTINGS;
import static org.opensearch.ml.common.utils.IndexUtils.UPDATED_ALL_NODES_REPLICA_INDEX_SETTINGS;
import static org.opensearch.ml.common.utils.IndexUtils.UPDATED_DEFAULT_INDEX_SETTINGS;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opensearch.OpenSearchWrapperException;
import org.opensearch.ResourceAlreadyExistsException;
import org.opensearch.action.admin.indices.create.CreateIndexRequest;
import org.opensearch.action.admin.indices.create.CreateIndexResponse;
import org.opensearch.action.admin.indices.mapping.put.PutMappingRequest;
import org.opensearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.MLIndex;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.transport.client.Client;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Log4j2
public class MLIndicesHandler {

    ClusterService clusterService;
    Client client;
    private static final Map<String, AtomicBoolean> indexMappingUpdated = new HashMap<>();

    static {
        for (MLIndex mlIndex : MLIndex.values()) {
            indexMappingUpdated.put(mlIndex.getIndexName(), new AtomicBoolean(false));
        }
    }

    public void initModelGroupIndexIfAbsent(ActionListener<Boolean> listener) {
        initMLIndexIfAbsent(MLIndex.MODEL_GROUP, listener);
    }

    public void initModelIndexIfAbsent(ActionListener<Boolean> listener) {
        initMLIndexIfAbsent(MLIndex.MODEL, listener);
    }

    public void initMLTaskIndex(ActionListener<Boolean> listener) {
        initMLIndexIfAbsent(MLIndex.TASK, listener);
    }

    public void initMLConnectorIndex(ActionListener<Boolean> listener) {
        initMLIndexIfAbsent(MLIndex.CONNECTOR, listener);
    }

    public void initMemoryMetaIndex(ActionListener<Boolean> listener) {
        initMLIndexIfAbsent(MLIndex.MEMORY_META, listener);
    }

    public void initMemoryMessageIndex(ActionListener<Boolean> listener) {
        initMLIndexIfAbsent(MLIndex.MEMORY_MESSAGE, listener);
    }

    public void initMLConfigIndex(ActionListener<Boolean> listener) {
        initMLIndexIfAbsent(MLIndex.CONFIG, listener);
    }

    public void initMLControllerIndex(ActionListener<Boolean> listener) {
        initMLIndexIfAbsent(MLIndex.CONTROLLER, listener);
    }

    public void initMLMCPSessionManagementIndex(ActionListener<Boolean> listener) {
        initMLIndexIfAbsent(MLIndex.MCP_SESSION_MANAGEMENT, listener);
    }

    public void initMLAgentIndex(ActionListener<Boolean> listener) {
        initMLIndexIfAbsent(MLIndex.AGENT, listener);
    }

    public void initMLIndexIfAbsent(MLIndex index, ActionListener<Boolean> listener) {
        String indexName = index.getIndexName();
        String mapping = index.getMapping();
        try (ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<Boolean> internalListener = ActionListener.runBefore(listener, () -> threadContext.restore());
            if (!clusterService.state().metadata().hasIndex(indexName)) {
                ActionListener<CreateIndexResponse> actionListener = ActionListener.wrap(r -> {
                    if (r.isAcknowledged()) {
                        log.info("create index:{}", indexName);
                        internalListener.onResponse(true);
                    } else {
                        internalListener.onResponse(false);
                    }
                }, e -> {
                    if (e instanceof ResourceAlreadyExistsException
                        || (e instanceof OpenSearchWrapperException && e.getCause() instanceof ResourceAlreadyExistsException)) {
                        log.info("Skip creating the Index:{} that is already created by another parallel request", indexName);
                        internalListener.onResponse(true);
                    } else {
                        log.error("Failed to create index {}", indexName, e);
                        internalListener.onFailure(e);
                    }
                });
                CreateIndexRequest request = new CreateIndexRequest(indexName)
                    .mapping(mapping, XContentType.JSON)
                    .settings(indexName.equals(MLIndex.CONFIG.getIndexName()) ? ALL_NODES_REPLICA_INDEX_SETTINGS : DEFAULT_INDEX_SETTINGS);
                client.admin().indices().create(request, actionListener);
            } else {
                log.debug("index:{} is already created", indexName);
                if (indexMappingUpdated.containsKey(indexName) && !indexMappingUpdated.get(indexName).get()) {
                    shouldUpdateIndex(indexName, index.getVersion(), ActionListener.wrap(r -> {
                        if (r) {
                            // return true if should update index
                            client
                                .admin()
                                .indices()
                                .putMapping(
                                    new PutMappingRequest().indices(indexName).source(mapping, XContentType.JSON),
                                    ActionListener.wrap(response -> {
                                        if (response.isAcknowledged()) {
                                            UpdateSettingsRequest updateSettingRequest = new UpdateSettingsRequest();
                                            updateSettingRequest
                                                .indices(indexName)
                                                .settings(
                                                    indexName.equals(MLIndex.CONFIG.getIndexName())
                                                        ? UPDATED_ALL_NODES_REPLICA_INDEX_SETTINGS
                                                        : UPDATED_DEFAULT_INDEX_SETTINGS
                                                );
                                            client
                                                .admin()
                                                .indices()
                                                .updateSettings(updateSettingRequest, ActionListener.wrap(updateResponse -> {
                                                    if (response.isAcknowledged()) {
                                                        indexMappingUpdated.get(indexName).set(true);
                                                        internalListener.onResponse(true);
                                                    } else {
                                                        internalListener
                                                            .onFailure(new MLException("Failed to update index setting for: " + indexName));
                                                    }
                                                }, exception -> {
                                                    log.error("Failed to update index setting for: {}", indexName, exception);
                                                    internalListener.onFailure(exception);
                                                }));
                                        } else {
                                            internalListener.onFailure(new MLException("Failed to update index: " + indexName));
                                        }
                                    }, exception -> {
                                        log.error("Failed to update index {}", indexName, exception);
                                        internalListener.onFailure(exception);
                                    })
                                );
                        } else {
                            // no need to update index if it does not exist or the version is already
                            // up-to-date.
                            indexMappingUpdated.get(indexName).set(true);
                            internalListener.onResponse(true);
                        }
                    }, e -> {
                        log.error("Failed to update index mapping", e);
                        internalListener.onFailure(e);
                    }));
                } else {
                    // No need to update index if it's not ML system index or it's already updated.
                    internalListener.onResponse(true);
                }
            }
        } catch (Exception e) {
            log.error("Failed to init index {}", indexName, e);
            listener.onFailure(e);
        }
    }

    /**
     * Check if we should update index based on schema version.
     * 
     * @param indexName  index name
     * @param newVersion new index mapping version
     * @param listener   action listener, if should update index, will pass true to
     *                   its onResponse method
     */
    public void shouldUpdateIndex(String indexName, Integer newVersion, ActionListener<Boolean> listener) {
        IndexMetadata indexMetaData = clusterService.state().getMetadata().indices().get(indexName);
        if (indexMetaData == null || indexMetaData.mapping() == null) {
            listener.onResponse(Boolean.FALSE);
            return;
        }
        Integer oldVersion = CommonValue.NO_SCHEMA_VERSION;
        Map<String, Object> indexMapping = indexMetaData.mapping().getSourceAsMap();
        Object meta = indexMapping.get(META);
        if (meta instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> metaMapping = (Map<String, Object>) meta;
            Object schemaVersion = metaMapping.get(SCHEMA_VERSION_FIELD);
            if (schemaVersion instanceof Integer) {
                oldVersion = (Integer) schemaVersion;
            }
        }
        listener.onResponse(newVersion > oldVersion);
    }

}
