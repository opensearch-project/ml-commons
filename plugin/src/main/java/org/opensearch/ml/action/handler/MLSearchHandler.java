/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.handler;

import static org.opensearch.core.rest.RestStatus.BAD_REQUEST;
import static org.opensearch.core.rest.RestStatus.INTERNAL_SERVER_ERROR;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.util.CollectionUtils;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.ExistsQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.TermsQueryBuilder;
import org.opensearch.indices.InvalidIndexNameException;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLModelGroup;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.search.SearchHits;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.fetch.subphase.FetchSourceContext;

import lombok.extern.log4j.Log4j2;

/**
 * Handle general get and search request in ml common.
 */
@Log4j2
public class MLSearchHandler {
    private final Client client;
    private NamedXContentRegistry xContentRegistry;

    private ModelAccessControlHelper modelAccessControlHelper;

    private ClusterService clusterService;

    public MLSearchHandler(
        Client client,
        NamedXContentRegistry xContentRegistry,
        ModelAccessControlHelper modelAccessControlHelper,
        ClusterService clusterService
    ) {
        this.modelAccessControlHelper = modelAccessControlHelper;
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.clusterService = clusterService;
    }

    /**
     * Fetch all the models from the model group index, and then create a combined query to model version index.
     * @param request
     * @param actionListener
     */
    public void search(SearchRequest request, ActionListener<SearchResponse> actionListener) {
        User user = RestActionUtils.getUserContext(client);
        ActionListener<SearchResponse> listener = wrapRestActionListener(actionListener, "Fail to search model version");
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<SearchResponse> wrappedListener = ActionListener.runBefore(listener, () -> context.restore());
            List<String> excludes = Optional
                .ofNullable(request.source())
                .map(SearchSourceBuilder::fetchSource)
                .map(FetchSourceContext::excludes)
                .map(x -> Arrays.stream(x).collect(Collectors.toList()))
                .orElse(new ArrayList<>());
            excludes.add(MLModel.CONNECTOR_FIELD + "." + HttpConnector.CREDENTIAL_FIELD);
            FetchSourceContext rebuiltFetchSourceContext = new FetchSourceContext(
                Optional
                    .ofNullable(request.source())
                    .map(SearchSourceBuilder::fetchSource)
                    .map(FetchSourceContext::fetchSource)
                    .orElse(true),
                Optional.ofNullable(request.source()).map(SearchSourceBuilder::fetchSource).map(FetchSourceContext::includes).orElse(null),
                excludes.toArray(new String[0])
            );

            // Check if the original query is not null before adding it to the must clause
            BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
            if (request.source().query() != null) {
                queryBuilder.must(request.source().query());
            }

            // Create a BoolQueryBuilder for the should clauses
            BoolQueryBuilder shouldQuery = QueryBuilders.boolQuery();

            // Add a should clause to include documents where IS_HIDDEN_FIELD is false
            shouldQuery.should(QueryBuilders.termQuery(MLModel.IS_HIDDEN_FIELD, false));

            // Add a should clause to include documents where IS_HIDDEN_FIELD does not exist or is null
            shouldQuery.should(QueryBuilders.boolQuery().mustNot(QueryBuilders.existsQuery(MLModel.IS_HIDDEN_FIELD)));

            // Set minimum should match to 1 to ensure at least one of the should conditions is met
            shouldQuery.minimumShouldMatch(1);

            // Add the shouldQuery to the main queryBuilder
            queryBuilder.filter(shouldQuery);

            request.source().query(queryBuilder);
            request.source().fetchSource(rebuiltFetchSourceContext);
            if (modelAccessControlHelper.skipModelAccessControl(user)) {
                client.search(request, wrappedListener);
            } else if (!clusterService.state().metadata().hasIndex(CommonValue.ML_MODEL_GROUP_INDEX)) {
                client.search(request, wrappedListener);
            } else {
                SearchSourceBuilder sourceBuilder = modelAccessControlHelper.createSearchSourceBuilder(user);
                SearchRequest modelGroupSearchRequest = new SearchRequest();
                sourceBuilder.fetchSource(new String[] { MLModelGroup.MODEL_GROUP_ID_FIELD, }, null);
                sourceBuilder.size(10000);
                modelGroupSearchRequest.source(sourceBuilder);
                modelGroupSearchRequest.indices(CommonValue.ML_MODEL_GROUP_INDEX);
                ActionListener<SearchResponse> modelGroupSearchActionListener = ActionListener.wrap(r -> {
                    if (Optional
                        .ofNullable(r)
                        .map(SearchResponse::getHits)
                        .map(SearchHits::getTotalHits)
                        .map(x -> x.value)
                        .orElse(0L) > 0) {
                        List<String> modelGroupIds = new ArrayList<>();
                        Arrays.stream(r.getHits().getHits()).forEach(hit -> { modelGroupIds.add(hit.getId()); });

                        request.source().query(rewriteQueryBuilder(request.source().query(), modelGroupIds));
                        client.search(request, wrappedListener);
                    } else {
                        log.debug("No model group found");
                        request.source().query(rewriteQueryBuilder(request.source().query(), null));
                        client.search(request, wrappedListener);
                    }
                }, e -> {
                    log.error("Fail to search model groups!", e);
                    wrappedListener.onFailure(e);
                });
                client.search(modelGroupSearchRequest, modelGroupSearchActionListener);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            actionListener.onFailure(e);
        }
    }

    private QueryBuilder rewriteQueryBuilder(QueryBuilder queryBuilder, List<String> modelGroupIds) {
        ExistsQueryBuilder existsQueryBuilder = new ExistsQueryBuilder(MLModelGroup.MODEL_GROUP_ID_FIELD);
        BoolQueryBuilder modelGroupIdMustNotExistBoolQuery = new BoolQueryBuilder();
        modelGroupIdMustNotExistBoolQuery.mustNot(existsQueryBuilder);

        BoolQueryBuilder accessControlledBoolQuery = new BoolQueryBuilder();
        if (!CollectionUtils.isEmpty(modelGroupIds)) {
            TermsQueryBuilder modelGroupIdTermsQuery = new TermsQueryBuilder(MLModelGroup.MODEL_GROUP_ID_FIELD, modelGroupIds);
            accessControlledBoolQuery.should(modelGroupIdTermsQuery);
        }
        accessControlledBoolQuery.should(modelGroupIdMustNotExistBoolQuery);
        if (queryBuilder == null) {
            return accessControlledBoolQuery;
        } else if (queryBuilder instanceof BoolQueryBuilder) {
            ((BoolQueryBuilder) queryBuilder).must(accessControlledBoolQuery);
            return queryBuilder;
        } else {
            BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
            boolQueryBuilder.must(queryBuilder);
            boolQueryBuilder.must(modelGroupIdMustNotExistBoolQuery);
            return boolQueryBuilder;
        }
    }

    /**
     * Wrap action listener to avoid return verbose error message and wrong 500 error to user.
     * Suggestion for exception handling in ML common:
     * 1. If the error is caused by wrong input, throw IllegalArgumentException exception.
     * 2. For other errors, please use MLException or its subclass, or use
     *    OpenSearchStatusException.
     *
     * TODO: tune this function for wrapped exception, return root exception error message
     *
     * @param actionListener action listener
     * @param generalErrorMessage general error message
     * @param <T> action listener response type
     * @return wrapped action listener
     */
    public static <T> ActionListener<T> wrapRestActionListener(ActionListener<T> actionListener, String generalErrorMessage) {
        return ActionListener.<T>wrap(r -> { actionListener.onResponse(r); }, e -> {
            log.error("Wrap exception before sending back to user", e);
            Throwable cause = ExceptionUtils.getRootCause(e);
            if (isProperExceptionToReturn(e)) {
                actionListener.onFailure(e);
            } else if (isProperExceptionToReturn(cause)) {
                actionListener.onFailure((Exception) cause);
            } else {
                RestStatus status = isBadRequest(e) ? BAD_REQUEST : INTERNAL_SERVER_ERROR;
                String errorMessage = generalErrorMessage;
                if (isBadRequest(e) || e instanceof MLException) {
                    errorMessage = e.getMessage();
                } else if (isBadRequest(cause) || cause instanceof MLException) {
                    errorMessage = cause.getMessage();
                }
                actionListener.onFailure(new OpenSearchStatusException(errorMessage, status));
            }
        });
    }

    public static boolean isProperExceptionToReturn(Throwable e) {
        return e instanceof OpenSearchStatusException || e instanceof IndexNotFoundException || e instanceof InvalidIndexNameException;
    }

    public static boolean isBadRequest(Throwable e) {
        return e instanceof IllegalArgumentException || e instanceof MLResourceNotFoundException;
    }
}
