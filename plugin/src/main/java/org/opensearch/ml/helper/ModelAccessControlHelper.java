/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.helper;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_INDEX;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MODEL_ACCESS_CONTROL_ENABLED;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.apache.lucene.search.join.ScoreMode;
import org.opensearch.action.get.GetRequest;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.util.CollectionUtils;
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
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.exception.MLValidationException;
import org.opensearch.ml.utils.MLNodeUtils;
import org.opensearch.search.builder.SearchSourceBuilder;

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

    private static final List<Class<?>> SUPPORTED_QUERY_TYPES = List
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

    public void validateModelGroupAccess(User user, String modelGroupId, Client client, ActionListener<Boolean> listener) {
        if (modelGroupId == null || isAdmin(user) || !isSecurityEnabledAndModelAccessControlEnabled(user)) {
            listener.onResponse(true);
            return;
        }

        List<String> userBackendRoles = user.getBackendRoles();
        GetRequest getModelGroupRequest = new GetRequest(ML_MODEL_GROUP_INDEX).id(modelGroupId);

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<Boolean> wrappedListener = ActionListener.runBefore(listener, () -> context.restore());
            client.get(getModelGroupRequest, ActionListener.wrap(r -> {
                if (r != null && r.isExists()) {
                    try (
                        XContentParser parser = MLNodeUtils
                            .createXContentParserFromRegistry(NamedXContentRegistry.EMPTY, r.getSourceAsBytesRef())
                    ) {
                        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                        MLModelGroup mlModelGroup = MLModelGroup.parse(parser);
                        AccessMode modelAccessMode = AccessMode.from(mlModelGroup.getAccess());
                        if (mlModelGroup.getOwner() == null) {
                            // previous security plugin not enabled, model defaults to public.
                            wrappedListener.onResponse(true);
                        } else if (AccessMode.RESTRICTED == modelAccessMode) {
                            if (mlModelGroup.getBackendRoles() == null || mlModelGroup.getBackendRoles().size() == 0) {
                                throw new IllegalStateException("Backend roles shouldn't be null");
                            } else {
                                wrappedListener
                                    .onResponse(
                                        Optional
                                            .ofNullable(userBackendRoles)
                                            .orElse(List.of())
                                            .stream()
                                            .anyMatch(mlModelGroup.getBackendRoles()::contains)
                                    );
                            }
                        } else if (AccessMode.PUBLIC == modelAccessMode) {
                            wrappedListener.onResponse(true);
                        } else if (AccessMode.PRIVATE == modelAccessMode) {
                            if (isOwner(mlModelGroup.getOwner(), user))
                                wrappedListener.onResponse(true);
                            else
                                wrappedListener.onResponse(false);
                        }
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

    public SearchSourceBuilder createSearchSourceBuilder(User user) {
        return addUserBackendRolesFilter(user, new SearchSourceBuilder());
    }
}
