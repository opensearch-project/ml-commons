/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE;

import org.apache.commons.lang3.StringUtils;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLSearchMemoriesBaseInput;
import org.opensearch.ml.helper.MemoryContainerHelper;

/**
 * Shared validation utilities for semantic and hybrid search transport actions.
 */
public class MemorySearchValidationUtils {

    private MemorySearchValidationUtils() {}

    /**
     * Validates common preconditions for memory search actions.
     * Returns false and calls actionListener.onFailure() if validation fails.
     */
    public static boolean validateSearchRequest(
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        MLSearchMemoriesBaseInput input,
        String tenantId,
        String searchType,
        ActionListener<SearchResponse> actionListener
    ) {
        if (!mlFeatureEnabledSetting.isAgenticMemoryEnabled()) {
            actionListener.onFailure(new OpenSearchStatusException(ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE, RestStatus.FORBIDDEN));
            return false;
        }
        if (input == null) {
            actionListener.onFailure(new IllegalArgumentException(searchType + " search input is required"));
            return false;
        }
        if (StringUtils.isBlank(input.getMemoryContainerId())) {
            actionListener.onFailure(new IllegalArgumentException("Memory container ID is required"));
            return false;
        }
        return true;
    }

    /**
     * Validates memory container configuration for search.
     * Returns false and calls actionListener.onFailure() if validation fails.
     */
    public static boolean validateContainerConfig(
        MLMemoryContainer container,
        User user,
        MemoryContainerHelper memoryContainerHelper,
        String searchType,
        ActionListener<SearchResponse> actionListener
    ) {
        if (!memoryContainerHelper.checkMemoryContainerAccess(user, container)) {
            actionListener
                .onFailure(
                    new OpenSearchStatusException(
                        "User doesn't have permissions to search memories in this container",
                        RestStatus.FORBIDDEN
                    )
                );
            return false;
        }

        MemoryConfiguration memoryConfig = container.getConfiguration();

        if (memoryConfig == null || memoryConfig.getEmbeddingModelType() == null) {
            actionListener
                .onFailure(
                    new OpenSearchStatusException(
                        "This memory container does not have an embedding model configured. "
                            + searchType
                            + " search requires a memory container with an embedding model (TEXT_EMBEDDING or SPARSE_ENCODING). "
                            + "Please update the memory container configuration to add an embedding model.",
                        RestStatus.BAD_REQUEST
                    )
                );
            return false;
        }

        if (StringUtils.isBlank(memoryConfig.getEmbeddingModelId())) {
            actionListener
                .onFailure(
                    new OpenSearchStatusException(
                        "This memory container does not have an embedding model ID configured. "
                            + "Please update the memory container configuration to add an embedding model ID.",
                        RestStatus.BAD_REQUEST
                    )
                );
            return false;
        }

        if (memoryConfig.getStrategies() == null || memoryConfig.getStrategies().isEmpty()) {
            actionListener
                .onFailure(
                    new OpenSearchStatusException(
                        "This memory container does not have any memory strategies configured. "
                            + searchType
                            + " search requires long-term memories which are created through memory strategies. "
                            + "Please update the memory container configuration to add at least one strategy.",
                        RestStatus.BAD_REQUEST
                    )
                );
            return false;
        }

        return true;
    }

    /**
     * Determines the owner ID for filtering based on user admin status.
     */
    public static String resolveOwnerId(User user, MemoryContainerHelper memoryContainerHelper) {
        return (user != null && !memoryContainerHelper.isAdminUser(user)) ? user.getName() : null;
    }
}
