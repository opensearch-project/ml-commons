/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.helper;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_MEMORY_CONTAINER_INDEX;

import org.opensearch.ExceptionsHelper;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryStorageConfig;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.transport.client.Client;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

/**
 * Helper class for memory container operations shared across transport actions
 */
@Log4j2
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MemoryContainerHelper {

    Client client;
    SdkClient sdkClient;
    NamedXContentRegistry xContentRegistry;

    @Inject
    public MemoryContainerHelper(Client client, SdkClient sdkClient, NamedXContentRegistry xContentRegistry) {
        this.client = client;
        this.sdkClient = sdkClient;
        this.xContentRegistry = xContentRegistry;
    }

    /**
     * Get memory container by ID
     * 
     * @param memoryContainerId the container ID
     * @param listener action listener for the result
     */
    public void getMemoryContainer(String memoryContainerId, ActionListener<MLMemoryContainer> listener) {
        getMemoryContainer(memoryContainerId, null, listener);
    }

    /**
     * Get memory container by ID with tenant support
     * 
     * @param memoryContainerId the container ID
     * @param tenantId the tenant ID (optional)
     * @param listener action listener for the result
     */
    public void getMemoryContainer(String memoryContainerId, String tenantId, ActionListener<MLMemoryContainer> listener) {
        FetchSourceContext fetchSourceContext = new FetchSourceContext(true, Strings.EMPTY_ARRAY, Strings.EMPTY_ARRAY);
        GetDataObjectRequest.Builder requestBuilder = GetDataObjectRequest
            .builder()
            .index(ML_MEMORY_CONTAINER_INDEX)
            .id(memoryContainerId)
            .fetchSourceContext(fetchSourceContext);

        if (tenantId != null) {
            requestBuilder.tenantId(tenantId);
        }

        GetDataObjectRequest getDataObjectRequest = requestBuilder.build();

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<MLMemoryContainer> wrappedListener = ActionListener.runBefore(listener, context::restore);

            sdkClient.getDataObjectAsync(getDataObjectRequest).whenComplete((r, throwable) -> {
                if (throwable != null) {
                    Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                    if (ExceptionsHelper.unwrap(cause, IndexNotFoundException.class) != null) {
                        wrappedListener.onFailure(new OpenSearchStatusException("Memory container not found", RestStatus.NOT_FOUND));
                    } else {
                        wrappedListener.onFailure(cause);
                    }
                } else {
                    try {
                        if (r.getResponse() != null && r.getResponse().isExists()) {
                            try (
                                XContentParser parser = jsonXContent
                                    .createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, r.getResponse().getSourceAsString())
                            ) {
                                ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                                MLMemoryContainer container = MLMemoryContainer.parse(parser);
                                wrappedListener.onResponse(container);
                            }
                        } else {
                            wrappedListener.onFailure(new OpenSearchStatusException("Memory container not found", RestStatus.NOT_FOUND));
                        }
                    } catch (Exception e) {
                        wrappedListener.onFailure(e);
                    }
                }
            });
        }
    }

    /**
     * Check if user has access to memory container
     * 
     * @param user the user to check
     * @param mlMemoryContainer the container to check access for
     * @return true if user has access, false otherwise
     */
    public boolean checkMemoryContainerAccess(User user, MLMemoryContainer mlMemoryContainer) {
        // If security is disabled (user is null), allow access
        if (user == null) {
            return true;
        }

        // If user is admin (has all_access role), allow access
        if (user.getRoles() != null && user.getRoles().contains("all_access")) {
            return true;
        }

        // Check if user is the owner
        User owner = mlMemoryContainer.getOwner();
        if (owner != null && owner.getName() != null && owner.getName().equals(user.getName())) {
            return true;
        }

        // Check if user has matching backend roles
        if (owner != null && owner.getBackendRoles() != null && user.getBackendRoles() != null) {
            return owner.getBackendRoles().stream().anyMatch(role -> user.getBackendRoles().contains(role));
        }

        return false;
    }

    /**
     * Get memory index name from container
     * 
     * @param container the memory container
     * @return the memory index name or null if not configured
     */
    public String getMemoryIndexName(MLMemoryContainer container) {
        MemoryStorageConfig config = container.getMemoryStorageConfig();
        if (config != null && config.getMemoryIndexName() != null) {
            return config.getMemoryIndexName();
        }
        return null;
    }

    /**
     * Validate container has memory index configured
     * 
     * @param container the memory container
     * @param actionName the action being performed (for error messages)
     * @param listener action listener to notify on failure
     * @return true if valid, false if validation failed (listener will be notified)
     */
    public boolean validateMemoryIndexExists(MLMemoryContainer container, String actionName, ActionListener<?> listener) {
        String indexName = getMemoryIndexName(container);
        if (indexName == null || indexName.isEmpty()) {
            listener
                .onFailure(
                    new OpenSearchStatusException(
                        "Memory container does not have a memory index configured for " + actionName,
                        RestStatus.BAD_REQUEST
                    )
                );
            return false;
        }
        return true;
    }
}
