/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.contextmanagement;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;

import java.time.Instant;
import java.util.List;

import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.ml.common.contextmanager.ContextManagementTemplate;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

/**
 * Service for managing context management templates in OpenSearch.
 * Provides CRUD operations for storing and retrieving context management configurations.
 */
@Log4j2
public class ContextManagementTemplateService {

    private static final int DEFAULT_MAX_TEMPLATES = 1000;
    private static final String INVALID_CONTEXT_MANAGEMENT_TEMPLATE_EXCEPTION_MESSAGE =
        "Invalid context management name: must not contain spaces or capital letters, and must be less than 50 characters.";
    private final MLIndicesHandler mlIndicesHandler;
    private final Client client;
    private final ClusterService clusterService;
    private final ContextManagementIndexUtils indexUtils;

    @Inject
    public ContextManagementTemplateService(MLIndicesHandler mlIndicesHandler, Client client, ClusterService clusterService) {
        this.mlIndicesHandler = mlIndicesHandler;
        this.client = client;
        this.clusterService = clusterService;
        this.indexUtils = new ContextManagementIndexUtils(client, clusterService);
    }

    /**
     * Save a context management template to OpenSearch
     * @param templateName The name of the template
     * @param template The template to save
     * @param listener ActionListener for the response
     */
    public void saveTemplate(String templateName, ContextManagementTemplate template, ActionListener<Boolean> listener) {
        try {
            // Validate template
            if (!template.isValid()) {

                listener.onFailure(new IllegalArgumentException(INVALID_CONTEXT_MANAGEMENT_TEMPLATE_EXCEPTION_MESSAGE));
                return;
            }

            User user = RestActionUtils.getUserContext(client);
            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                ActionListener<Boolean> wrappedListener = ActionListener.runBefore(listener, context::restore);

                // Set timestamps
                Instant now = Instant.now();
                if (template.getCreatedTime() == null) {
                    template.setCreatedTime(now);
                }
                template.setLastModified(now);

                // Set created by if not already set
                if (template.getCreatedBy() == null && user != null) {
                    template.setCreatedBy(user.getName());
                }

                // Ensure index exists first
                indexUtils.createIndexIfNotExists(ActionListener.wrap(indexCreated -> {
                    // Check if template with same name already exists
                    validateUniqueTemplateName(template.getName(), ActionListener.wrap(exists -> {
                        if (exists) {
                            wrappedListener
                                .onFailure(
                                    new IllegalArgumentException(
                                        "A context management with name '" + template.getName() + "' already exists"
                                    )
                                );
                            return;
                        }

                        // Create the index request with proper JSON serialization
                        IndexRequest indexRequest = new IndexRequest(ContextManagementIndexUtils.getIndexName())
                            .id(template.getName())
                            .source(template.toXContent(jsonXContent.contentBuilder(), ToXContentObject.EMPTY_PARAMS))
                            .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

                        // Execute the index operation
                        client.index(indexRequest, ActionListener.wrap(indexResponse -> {
                            log.info("Context management saved successfully: {}", template.getName());
                            wrappedListener.onResponse(true);
                        }, exception -> {
                            log.error("Failed to save context management: {}", template.getName(), exception);
                            wrappedListener.onFailure(exception);
                        }));
                    }, wrappedListener::onFailure));
                }, wrappedListener::onFailure));
            }
        } catch (Exception e) {
            log.error("Error saving context management", e);
            listener.onFailure(e);
        }
    }

    /**
     * Get a context management template by name
     * @param templateName The name of the template to retrieve
     * @param listener ActionListener for the response
     */
    public void getTemplate(String templateName, ActionListener<ContextManagementTemplate> listener) {
        try {
            if (templateName == null || templateName.trim().isEmpty()) {
                listener.onFailure(new IllegalArgumentException("context management name cannot be null, empty, or whitespace"));
                return;
            }

            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                ActionListener<ContextManagementTemplate> wrappedListener = ActionListener.runBefore(listener, context::restore);

                GetRequest getRequest = new GetRequest(ContextManagementIndexUtils.getIndexName(), templateName);

                client.get(getRequest, ActionListener.wrap(getResponse -> {
                    if (!getResponse.isExists()) {
                        wrappedListener.onFailure(new IllegalArgumentException("Context management not found: " + templateName));
                        return;
                    }

                    try {
                        XContentParser parser = createXContentParserFromRegistry(
                            NamedXContentRegistry.EMPTY,
                            LoggingDeprecationHandler.INSTANCE,
                            getResponse.getSourceAsBytesRef()
                        );
                        ContextManagementTemplate template = ContextManagementTemplate.parse(parser);
                        wrappedListener.onResponse(template);
                    } catch (Exception e) {
                        log.error("Failed to parse context management: {}", templateName, e);
                        wrappedListener.onFailure(e);
                    }
                }, exception -> {
                    if (exception instanceof IndexNotFoundException) {
                        wrappedListener.onFailure(new IllegalArgumentException("Context management not found: " + templateName));
                    } else {
                        log.error("Failed to get context management: {}", templateName, exception);
                        wrappedListener.onFailure(exception);
                    }
                }));
            }
        } catch (Exception e) {
            log.error("Error getting context management", e);
            listener.onFailure(e);
        }
    }

    /**
     * List all context management templates
     * @param listener ActionListener for the response
     */
    public void listTemplates(ActionListener<List<ContextManagementTemplate>> listener) {
        listTemplates(0, DEFAULT_MAX_TEMPLATES, listener);
    }

    /**
     * List context management templates with pagination
     * @param from Starting index for pagination
     * @param size Number of templates to return
     * @param listener ActionListener for the response
     */
    public void listTemplates(int from, int size, ActionListener<List<ContextManagementTemplate>> listener) {
        try {
            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                ActionListener<List<ContextManagementTemplate>> wrappedListener = ActionListener.runBefore(listener, context::restore);

                SearchRequest searchRequest = new SearchRequest(ContextManagementIndexUtils.getIndexName());
                SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(new MatchAllQueryBuilder()).from(from).size(size);
                searchRequest.source(searchSourceBuilder);

                client.search(searchRequest, ActionListener.wrap(searchResponse -> {
                    try {
                        List<ContextManagementTemplate> templates = new java.util.ArrayList<>();
                        for (SearchHit hit : searchResponse.getHits().getHits()) {
                            XContentParser parser = createXContentParserFromRegistry(
                                NamedXContentRegistry.EMPTY,
                                LoggingDeprecationHandler.INSTANCE,
                                hit.getSourceRef()
                            );
                            ContextManagementTemplate template = ContextManagementTemplate.parse(parser);
                            templates.add(template);
                        }
                        wrappedListener.onResponse(templates);
                    } catch (Exception e) {
                        log.error("Failed to parse context management", e);
                        wrappedListener.onFailure(e);
                    }
                }, exception -> {
                    if (exception instanceof IndexNotFoundException) {
                        // Return empty list if index doesn't exist
                        wrappedListener.onResponse(new java.util.ArrayList<>());
                    } else {
                        log.error("Failed to list context management", exception);
                        wrappedListener.onFailure(exception);
                    }
                }));
            }
        } catch (Exception e) {
            log.error("Error listing context management", e);
            listener.onFailure(e);
        }
    }

    /**
     * Delete a context management template by name
     * @param templateName The name of the template to delete
     * @param listener ActionListener for the response
     */
    public void deleteTemplate(String templateName, ActionListener<Boolean> listener) {
        try {
            if (templateName == null || templateName.trim().isEmpty()) {
                listener.onFailure(new IllegalArgumentException("context management name cannot be null, empty, or whitespace"));
                return;
            }

            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                ActionListener<Boolean> wrappedListener = ActionListener.runBefore(listener, context::restore);

                DeleteRequest deleteRequest = new DeleteRequest(ContextManagementIndexUtils.getIndexName(), templateName)
                    .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

                client.delete(deleteRequest, ActionListener.wrap(deleteResponse -> {
                    boolean deleted = deleteResponse.getResult() == DeleteResponse.Result.DELETED;
                    if (deleted) {
                        log.info("Context management deleted successfully: {}", templateName);
                    } else {
                        log.warn("Context management not found for deletion: {}", templateName);
                    }
                    wrappedListener.onResponse(deleted);
                }, exception -> {
                    if (exception instanceof IndexNotFoundException) {
                        wrappedListener.onResponse(false);
                    } else {
                        log.error("Failed to delete context management: {}", templateName, exception);
                        wrappedListener.onFailure(exception);
                    }
                }));
            }
        } catch (Exception e) {
            log.error("Error deleting context management", e);
            listener.onFailure(e);
        }
    }

    /**
     * Validate that a template name is unique
     * @param templateName The template name to check
     * @param listener ActionListener for the response (true if exists, false if unique)
     */
    private void validateUniqueTemplateName(String templateName, ActionListener<Boolean> listener) {
        try {
            SearchRequest searchRequest = new SearchRequest(ContextManagementIndexUtils.getIndexName());
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(new TermQueryBuilder("_id", templateName)).size(1);
            searchRequest.source(searchSourceBuilder);

            client.search(searchRequest, ActionListener.wrap(searchResponse -> {
                boolean exists = searchResponse.getHits().getTotalHits() != null && searchResponse.getHits().getTotalHits().value() > 0;
                listener.onResponse(exists);
            }, exception -> {
                if (exception instanceof IndexNotFoundException) {
                    // Index doesn't exist, so template name is unique
                    listener.onResponse(false);
                } else {
                    listener.onFailure(exception);
                }
            }));
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    /**
     * Update a context management template
     * @param templateName The name of the template to update
     * @param template The updated template
     * @param listener ActionListener for the response
     */
    public void updateTemplate(String templateName, ContextManagementTemplate template, ActionListener<UpdateResponse> listener) {
        try {
            if (templateName == null || templateName.trim().isEmpty()) {
                listener.onFailure(new IllegalArgumentException("context management name cannot be null, empty, or whitespace"));
                return;
            }

            // Validate template
            if (!template.isValid()) {
                listener.onFailure(new IllegalArgumentException(INVALID_CONTEXT_MANAGEMENT_TEMPLATE_EXCEPTION_MESSAGE));
                return;
            }

            User user = RestActionUtils.getUserContext(client);
            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                ActionListener<UpdateResponse> wrappedListener = ActionListener.runBefore(listener, context::restore);

                // Set last modified timestamp
                template.setLastModified(Instant.now());

                // Create the update request
                UpdateRequest updateRequest = new UpdateRequest(ContextManagementIndexUtils.getIndexName(), templateName)
                    .doc(template.toXContent(jsonXContent.contentBuilder(), ToXContentObject.EMPTY_PARAMS))
                    .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

                // Execute the update operation
                client.update(updateRequest, ActionListener.wrap(updateResponse -> {
                    log.info("Context management updated successfully: {}", templateName);
                    wrappedListener.onResponse(updateResponse);
                }, exception -> {
                    if (exception instanceof IndexNotFoundException) {
                        wrappedListener.onFailure(new IllegalArgumentException("Context management not found: " + templateName));
                    } else {
                        log.error("Failed to update context management: {}", templateName, exception);
                        wrappedListener.onFailure(exception);
                    }
                }));
            }
        } catch (Exception e) {
            log.error("Error updating context management: {}", templateName, e);
            listener.onFailure(e);
        }
    }

    /**
     * Create XContentParser from registry - utility method
     */
    private XContentParser createXContentParserFromRegistry(
        NamedXContentRegistry xContentRegistry,
        LoggingDeprecationHandler deprecationHandler,
        org.opensearch.core.common.bytes.BytesReference bytesReference
    ) throws java.io.IOException {
        return MediaTypeRegistry.JSON.xContent().createParser(xContentRegistry, deprecationHandler, bytesReference.streamInput());
    }
}
