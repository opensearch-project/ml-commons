/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.contextmanagement;

import org.opensearch.action.admin.indices.create.CreateIndexRequest;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

/**
 * Utility class for managing context management template indices.
 * Handles index creation, mapping definition, and settings configuration.
 */
@Log4j2
public class ContextManagementIndexUtils {

    public static final String CONTEXT_MANAGEMENT_TEMPLATES_INDEX = "ml_context_management_templates";

    private final Client client;
    private final ClusterService clusterService;

    public ContextManagementIndexUtils(Client client, ClusterService clusterService) {
        this.client = client;
        this.clusterService = clusterService;
    }

    /**
     * Check if the context management templates index exists
     * @return true if the index exists, false otherwise
     */
    public boolean doesIndexExist() {
        return clusterService.state().metadata().hasIndex(CONTEXT_MANAGEMENT_TEMPLATES_INDEX);
    }

    /**
     * Create the context management templates index if it doesn't exist
     * @param listener ActionListener for the response
     */
    public void createIndexIfNotExists(ActionListener<Boolean> listener) {
        if (doesIndexExist()) {
            log.debug("Context management templates index already exists");
            listener.onResponse(true);
            return;
        }

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<Boolean> wrappedListener = ActionListener.runBefore(listener, context::restore);

            CreateIndexRequest createIndexRequest = new CreateIndexRequest(CONTEXT_MANAGEMENT_TEMPLATES_INDEX).settings(getIndexSettings());

            client.admin().indices().create(createIndexRequest, ActionListener.wrap(createIndexResponse -> {
                log.info("Successfully created context management templates index");
                wrappedListener.onResponse(true);
            }, exception -> {
                if (exception instanceof org.opensearch.ResourceAlreadyExistsException) {
                    log.debug("Context management templates index already exists");
                    wrappedListener.onResponse(true);
                } else {
                    log.error("Failed to create context management templates index", exception);
                    wrappedListener.onFailure(exception);
                }
            }));
        } catch (Exception e) {
            log.error("Error creating context management templates index", e);
            listener.onFailure(e);
        }
    }

    /**
     * Get the index settings for context management templates
     * @return Settings for the index
     */
    private Settings getIndexSettings() {
        return Settings
            .builder()
            .put("index.number_of_shards", 1)
            .put("index.number_of_replicas", 1)
            .put("index.auto_expand_replicas", "0-1")
            .build();
    }

    /**
     * Get the index name for context management templates
     * @return The index name
     */
    public static String getIndexName() {
        return CONTEXT_MANAGEMENT_TEMPLATES_INDEX;
    }
}
