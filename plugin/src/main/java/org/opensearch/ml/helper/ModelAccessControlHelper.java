/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.helper;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_INDEX;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MODEL_ACCESS_CONTROL_ENABLED;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.lucene.search.join.ScoreMode;
import org.opensearch.ExceptionsHelper;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.util.CollectionUtils;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.ExistsQueryBuilder;
import org.opensearch.index.query.IdsQueryBuilder;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.index.query.MatchPhraseQueryBuilder;
import org.opensearch.index.query.MatchQueryBuilder;
import org.opensearch.index.query.NestedQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.RangeQueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.index.query.TermsQueryBuilder;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.MLModelGroup;
import org.opensearch.ml.common.ResourceSharingClientAccessor;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.exception.MLValidationException;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.utils.MLNodeUtils;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.SearchDataObjectRequest;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.security.spi.resources.client.ResourceSharingClient;
import org.opensearch.transport.client.Client;

import com.google.common.collect.ImmutableList;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class ModelAccessControlHelper {

    private volatile Boolean modelAccessControlEnabled;

    public ModelAccessControlHelper(ClusterService clusterService, Settings settings) {
        modelAccessControlEnabled = ML_COMMONS_MODEL_ACCESS_CONTROL_ENABLED.get(settings);
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_MODEL_ACCESS_CONTROL_ENABLED, it -> modelAccessControlEnabled = it);
    }

    private static final List<Class<?>> SUPPORTED_QUERY_TYPES = ImmutableList
        .of(
            IdsQueryBuilder.class,
            MatchQueryBuilder.class,
            MatchAllQueryBuilder.class,
            MatchPhraseQueryBuilder.class,
            TermQueryBuilder.class,
            TermsQueryBuilder.class,
            ExistsQueryBuilder.class,
            RangeQueryBuilder.class
        );

    // TODO Eventually remove this when all usages of it have been migrated to the SdkClient version
    public void validateModelGroupAccess(User user, String modelGroupId, String action, Client client, ActionListener<Boolean> listener) {
        if (modelGroupId == null) {
            listener.onResponse(true);
            return;
        }
        if (ResourceSharingClientAccessor.getInstance().getResourceSharingClient() != null) {
            ResourceSharingClient resourceSharingClient = ResourceSharingClientAccessor.getInstance().getResourceSharingClient();
            resourceSharingClient.verifyAccess(modelGroupId, ML_MODEL_GROUP_INDEX, action, ActionListener.wrap(isAuthorized -> {
                if (!isAuthorized) {
                    listener
                        .onFailure(
                            new OpenSearchStatusException(
                                "User " + user.getName() + " is not authorized to delete ml-model-group id: " + modelGroupId,
                                RestStatus.FORBIDDEN
                            )
                        );
                    return;
                }
                listener.onResponse(true);
            }, listener::onFailure));
            return;
        }
        if (isAdmin(user) || !isSecurityEnabledAndModelAccessControlEnabled(user)) {
            listener.onResponse(true);
            return;
        }

        GetRequest getModelGroupRequest = new GetRequest(ML_MODEL_GROUP_INDEX).id(modelGroupId);

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<Boolean> wrappedListener = ActionListener.runBefore(listener, context::restore);
            client.get(getModelGroupRequest, ActionListener.wrap(r -> {
                if (r != null && r.isExists()) {
                    try (
                        XContentParser parser = MLNodeUtils
                            .createXContentParserFromRegistry(NamedXContentRegistry.EMPTY, r.getSourceAsBytesRef())
                    ) {
                        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                        MLModelGroup mlModelGroup = MLModelGroup.parse(parser);
                        checkModelGroupPermission(mlModelGroup, user, wrappedListener);
                    } catch (Exception e) {
                        log.error("Failed to parse ml model group");
                        wrappedListener.onFailure(e);
                    }
                } else {
                    wrappedListener.onFailure(new MLResourceNotFoundException("Fail to find model group"));
                }
            }, e -> {
                if (e instanceof IndexNotFoundException) {
                    wrappedListener.onFailure(new MLResourceNotFoundException("Fail to find model group"));
                } else {
                    log.error("Fail to get model group", e);
                    wrappedListener.onFailure(new MLValidationException("Fail to get model group"));
                }
            }));
        } catch (Exception e) {
            log.error("Failed to validate Access", e);
            listener.onFailure(e);
        }
    }

    public void validateModelGroupAccess(
        User user,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        String tenantId,
        String modelGroupId,
        String action,
        Client client,
        SdkClient sdkClient,
        ActionListener<Boolean> listener
    ) {
        if (modelGroupId == null) {
            listener.onResponse(true);
            return;
        }
        if (ResourceSharingClientAccessor.getInstance().getResourceSharingClient() != null) {
            ResourceSharingClient resourceSharingClient = ResourceSharingClientAccessor.getInstance().getResourceSharingClient();
            resourceSharingClient.verifyAccess(modelGroupId, ML_MODEL_GROUP_INDEX, action, ActionListener.wrap(isAuthorized -> {
                if (!isAuthorized) {
                    listener
                        .onFailure(
                            new OpenSearchStatusException(
                                "User " + user.getName() + " is not authorized to delete ml-model-group id: " + modelGroupId,
                                RestStatus.FORBIDDEN
                            )
                        );
                    return;
                }
                listener.onResponse(true);
            }, listener::onFailure));
            return;
        }

        if (mlFeatureEnabledSetting.isMultiTenancyEnabled()) {
            listener.onResponse(true);  // Multi-tenancy handles access control
            return;
        }

        if (isAdmin(user) || !isSecurityEnabledAndModelAccessControlEnabled(user)) {
            listener.onResponse(true);  // Admin or security disabled
            return;
        }
        GetDataObjectRequest getModelGroupRequest = GetDataObjectRequest
            .builder()
            .index(ML_MODEL_GROUP_INDEX)
            .id(modelGroupId)
            .tenantId(tenantId)
            .build();
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<Boolean> wrappedListener = ActionListener.runBefore(listener, context::restore);
            sdkClient.getDataObjectAsync(getModelGroupRequest).whenComplete((r, throwable) -> {
                if (throwable == null) {
                    try {
                        GetResponse gr = r.getResponse();
                        if (gr != null && gr.isExists()) {
                            try (
                                XContentParser parser = jsonXContent
                                    .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, gr.getSourceAsString())
                            ) {
                                ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                                MLModelGroup mlModelGroup = MLModelGroup.parse(parser);
                                if (TenantAwareHelper
                                    .validateTenantResource(mlFeatureEnabledSetting, tenantId, mlModelGroup.getTenantId(), listener)) {
                                    if (isAdmin(user) || !isSecurityEnabledAndModelAccessControlEnabled(user)) {
                                        listener.onResponse(true);
                                        return;
                                    }
                                    checkModelGroupPermission(mlModelGroup, user, wrappedListener);
                                }
                            } catch (Exception e) {
                                log.error("Failed to parse ml model group");
                                wrappedListener.onFailure(e);
                            }
                        } else {
                            wrappedListener.onFailure(new MLResourceNotFoundException("Fail to find model group"));
                        }
                    } catch (Exception e) {
                        listener.onFailure(e);
                    }
                } else {
                    Exception e = SdkClientUtils.unwrapAndConvertToException(throwable);
                    if (ExceptionsHelper.unwrap(e, IndexNotFoundException.class) != null) {
                        wrappedListener.onFailure(new MLResourceNotFoundException("Fail to find model group"));
                    } else {
                        log.error("Fail to get model group", e);
                        wrappedListener.onFailure(new MLValidationException("Fail to get model group"));
                    }
                }
            });
        } catch (Exception e) {
            log.error("Failed to validate Access", e);
            listener.onFailure(e);
        }
    }

    public void checkModelGroupPermission(MLModelGroup mlModelGroup, User user, ActionListener<Boolean> wrappedListener) {
        AccessMode modelAccessMode = AccessMode.from(mlModelGroup.getAccess());
        if (mlModelGroup.getOwner() == null) {
            // previous security plugin not enabled, model defaults to public.
            wrappedListener.onResponse(true);
        } else {
            switch (modelAccessMode) {
                case RESTRICTED:
                    if (mlModelGroup.getBackendRoles() == null || mlModelGroup.getBackendRoles().isEmpty()) {
                        throw new IllegalStateException("Backend roles shouldn't be null");
                    } else {
                        wrappedListener
                            .onResponse(
                                Optional
                                    .ofNullable(user.getBackendRoles())
                                    .orElse(Collections.emptyList())
                                    .stream()
                                    .anyMatch(mlModelGroup.getBackendRoles()::contains)
                            );
                    }
                    break;
                case PRIVATE:
                    wrappedListener.onResponse(isOwner(mlModelGroup.getOwner(), user));
                    break;
                default: // PUBLIC
                    wrappedListener.onResponse(true);
            }
        }
    }

    public boolean skipModelAccessControl(User user) {
        // Case 1: user == null when 1. Security is disabled. 2. When user is super-admin
        // Case 2: If Security is enabled and filter is disabled, proceed with search as
        // user is already authenticated to hit this API.
        // case 3: user is admin which means we don't have to check backend role filtering
        return user == null || !modelAccessControlEnabled || isAdmin(user);
    }

    public boolean isSecurityEnabledAndModelAccessControlEnabled(User user) {
        return user != null && modelAccessControlEnabled;
    }

    public boolean isAdmin(User user) {
        if (user == null) {
            return false;
        }
        if (CollectionUtils.isEmpty(user.getRoles())) {
            return false;
        }
        return user.getRoles().contains("all_access");
    }

    public boolean isOwner(User owner, User user) {
        if (user == null || owner == null) {
            return false;
        }
        return owner.getName().equals(user.getName());
    }

    public boolean isUserHasBackendRole(User user, MLModelGroup mlModelGroup) {
        AccessMode modelAccessMode = AccessMode.from(mlModelGroup.getAccess());
        if (AccessMode.PUBLIC == modelAccessMode)
            return true;
        if (AccessMode.PRIVATE == modelAccessMode)
            return false;
        return user.getBackendRoles() != null
            && mlModelGroup.getBackendRoles() != null
            && mlModelGroup.getBackendRoles().stream().anyMatch(x -> user.getBackendRoles().contains(x));
    }

    public boolean isOwnerStillHasPermission(User user, MLModelGroup mlModelGroup) {
        // when security plugin is disabled, or model access control not enabled, the model is a public model and anyone has permission to
        // it.
        if (!isSecurityEnabledAndModelAccessControlEnabled(user))
            return true;
        AccessMode access = AccessMode.from(mlModelGroup.getAccess());
        if (AccessMode.PUBLIC == access) {
            return true;
        } else if (AccessMode.PRIVATE == access) {
            return isOwner(user, mlModelGroup.getOwner());
        } else if (AccessMode.RESTRICTED == access) {
            if (CollectionUtils.isEmpty(mlModelGroup.getBackendRoles())) {
                throw new IllegalStateException("Backend roles should not be null");
            }
            return user.getBackendRoles() != null
                && new HashSet<>(mlModelGroup.getBackendRoles()).stream().anyMatch(x -> user.getBackendRoles().contains(x));
        }
        throw new IllegalStateException("Access shouldn't be null");
    }

    public boolean isModelAccessControlEnabled() {
        return modelAccessControlEnabled;
    }

    public SearchSourceBuilder addUserBackendRolesFilter(User user, SearchSourceBuilder searchSourceBuilder) {
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        boolQueryBuilder.should(QueryBuilders.termQuery(MLModelGroup.ACCESS, AccessMode.PUBLIC.getValue()));
        boolQueryBuilder.should(QueryBuilders.termsQuery(MLModelGroup.BACKEND_ROLES_FIELD + ".keyword", user.getBackendRoles()));

        BoolQueryBuilder privateBoolQuery = new BoolQueryBuilder();
        String ownerName = "owner.name.keyword";
        TermQueryBuilder ownerNameTermQuery = QueryBuilders.termQuery(ownerName, user.getName());
        NestedQueryBuilder nestedQueryBuilder = new NestedQueryBuilder(MLModelGroup.OWNER, ownerNameTermQuery, ScoreMode.None);
        privateBoolQuery.must(nestedQueryBuilder);
        privateBoolQuery.must(QueryBuilders.termQuery(MLModelGroup.ACCESS, AccessMode.PRIVATE.getValue()));
        boolQueryBuilder.should(privateBoolQuery);
        QueryBuilder query = searchSourceBuilder.query();
        if (query == null) {
            searchSourceBuilder.query(boolQueryBuilder);
        } else if (query instanceof BoolQueryBuilder) {
            ((BoolQueryBuilder) query).filter(boolQueryBuilder);
        } else {
            BoolQueryBuilder rewriteQuery = new BoolQueryBuilder();
            rewriteQuery.must(query);
            rewriteQuery.filter(boolQueryBuilder);
            searchSourceBuilder.query(rewriteQuery);
        }
        return searchSourceBuilder;
    }

    public QueryBuilder mergeWithAccessFilter(QueryBuilder existing, Set<String> ids) {
        QueryBuilder accessFilter = (ids == null || ids.isEmpty())
            ? QueryBuilders.boolQuery().mustNot(QueryBuilders.matchAllQuery()) // deny-all
            : QueryBuilders.idsQuery().addIds(ids.toArray(new String[0])); // use termsQuery(field, ids) if not _id

        if (existing == null)
            return QueryBuilders.boolQuery().filter(accessFilter);
        if (existing instanceof BoolQueryBuilder) {
            ((BoolQueryBuilder) existing).filter(accessFilter);
            return existing;
        }
        return QueryBuilders.boolQuery().must(existing).filter(accessFilter);
    }

    public void addAccessibleModelGroupsFilterAndSearch(
        String tenantId,
        SearchRequest request,
        SdkClient sdkClient,
        Consumer<Set<String>> onSuccess,
        ActionListener<SearchResponse> wrappedListener
    ) {
        ResourceSharingClient rsc = ResourceSharingClientAccessor.getInstance().getResourceSharingClient();
        // filter by accessible model-groups
        rsc.getAccessibleResourceIds(ML_MODEL_GROUP_INDEX, ActionListener.wrap(onSuccess::accept, e -> {
            // Fail-safe: deny-all and still return a response
            SearchSourceBuilder reqSrc = request.source() != null ? request.source() : new SearchSourceBuilder();
            reqSrc.query(mergeWithAccessFilter(reqSrc.query(), Collections.emptySet()));
            request.source(reqSrc);

            SearchDataObjectRequest finalSearch = SearchDataObjectRequest
                .builder()
                .indices(request.indices())
                .searchSourceBuilder(request.source())
                .tenantId(tenantId)
                .build();

            sdkClient.searchDataObjectAsync(finalSearch).whenComplete(SdkClientUtils.wrapSearchCompletion(wrappedListener));
        }));
    }

}
