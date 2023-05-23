/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_INDEX;

import java.util.List;

import com.google.common.collect.ImmutableList;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.util.Strings;
import org.opensearch.action.ActionListener;
import org.opensearch.action.get.GetRequest;
import org.opensearch.client.Client;
import org.opensearch.common.util.CollectionUtils;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.ExistsQueryBuilder;
import org.opensearch.index.query.IdsQueryBuilder;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.index.query.MatchPhraseQueryBuilder;
import org.opensearch.index.query.MatchQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.RangeQueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.index.query.TermsQueryBuilder;
import org.opensearch.ml.common.MLModelGroup;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.exception.MLValidationException;
import org.opensearch.search.builder.SearchSourceBuilder;


@Log4j2
public class SecurityUtils {

    public static final List<Class<?>> SUPPORTED_QUERY_TYPES = ImmutableList.of(
        IdsQueryBuilder.class,
        MatchQueryBuilder.class,
        MatchAllQueryBuilder.class,
        MatchPhraseQueryBuilder.class,
        TermQueryBuilder.class,
        TermsQueryBuilder.class,
        ExistsQueryBuilder.class,
        RangeQueryBuilder.class
    );

    public static void validateModelGroupAccess(User user, String modelGroupId, Client client, boolean isModelAccessControlEnabled, ActionListener<Boolean> listener) {
        if (!isModelAccessControlEnabled) listener.onResponse(true);
        else validateModelGroupAccess(user, modelGroupId, client, listener);
    }

    private static void validateModelGroupAccess(User user, String modelGroupId, Client client, ActionListener<Boolean> listener) {
        if (modelGroupId == null || isAdmin(user) || user == null) {
            listener.onResponse(true);
            return;
        }

        List<String> userBackendRoles = user.getBackendRoles();
        GetRequest getModelGroupRequest = new GetRequest(ML_MODEL_GROUP_INDEX).id(modelGroupId);
        try {
            client.get(getModelGroupRequest, ActionListener.wrap(r -> {
                if (r != null && r.isExists()) {
                    try (
                        XContentParser parser = MLNodeUtils
                            .createXContentParserFromRegistry(NamedXContentRegistry.EMPTY, r.getSourceAsBytesRef())) {
                        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                        MLModelGroup mlModelGroup = MLModelGroup.parse(parser);
                        if (mlModelGroup.getOwner() == null) {
                            // previous security plugin not enabled, model defaults to public.
                            listener.onResponse(true);
                        } else if (!Strings.isBlank(mlModelGroup.getAccess())) {
                            // owner is not null and access is not blank means this model group's backend_roles should be null.
                            if (mlModelGroup.getBackendRoles() != null && mlModelGroup.getBackendRoles().size() > 0) {
                                throw new IllegalStateException("Backend roles should be null");
                            } else if (mlModelGroup.getAccess().equals(MLModelGroup.PUBLIC)) {
                                listener.onResponse(true);
                            } else if (mlModelGroup.getAccess().equals(MLModelGroup.PRIVATE)) {
                                if (isOwner(mlModelGroup.getOwner(), user)) {
                                    listener.onResponse(true);
                                    return;
                                }
                                listener.onResponse(false);
                            }
                        } else {
                            // owner is not null access is null means this model is restricted, we should check backend roles.
                            List<String> backendRoles = mlModelGroup.getBackendRoles();
                            if (backendRoles == null || backendRoles.isEmpty()) {
                                throw new IllegalStateException("Backend roles should not be null");
                            } else {
                                listener
                                    .onResponse(
                                        userBackendRoles
                                            .stream()
                                            .anyMatch(backendRoles::contains)
                                    );
                            }
                        }
                    } catch (Exception e) {
                        log.error("Failed to parse ml model group");
                        listener.onFailure(e);
                    }
                } else {
                    listener.onFailure(new MLResourceNotFoundException("Fail to find model group"));
                }
            }, e -> {
                log.error("Failed to validate Access", e);
                listener.onFailure(new MLValidationException("Failed to validate Access"));
            }));
        } catch (Exception e) {
            log.error("Failed to validate Access", e);
            listener.onFailure(e);
        }
    }

    public static boolean isAdmin(User user) {
        if (user == null) {
            return false;
        }
        if (CollectionUtils.isEmpty(user.getRoles())) {
            return false;
        }
        return user.getRoles().contains("all_access");
    }

    public static boolean isOwner(User owner, User user) {
        if (user == null || owner == null) {
            return false;
        }
        return owner.getName().equals(user.getName());
    }

    public static SearchSourceBuilder addUserBackendRolesFilter(User user, SearchSourceBuilder searchSourceBuilder) {
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        boolQueryBuilder.should(QueryBuilders.termQuery(MLModelGroup.ACCESS, MLModelGroup.PUBLIC));
        boolQueryBuilder.should(QueryBuilders.termsQuery(MLModelGroup.BACKEND_ROLES_FIELD + ".keyword", user.getBackendRoles()));

        BoolQueryBuilder privateBoolQuery = new BoolQueryBuilder();
        String ownerName = "owner.name.keyword";
        TermQueryBuilder ownerNameTermQuery = QueryBuilders.termQuery(ownerName, user.getName());
        privateBoolQuery.must(ownerNameTermQuery);
        privateBoolQuery.must(QueryBuilders.termQuery(MLModelGroup.ACCESS, MLModelGroup.PRIVATE));
        boolQueryBuilder.should(privateBoolQuery);
        QueryBuilder query = searchSourceBuilder.query();
        if (query == null) {
            searchSourceBuilder.query(boolQueryBuilder);
        } else if (query instanceof BoolQueryBuilder) {
            ((BoolQueryBuilder) query).filter(boolQueryBuilder);
        } else if (SUPPORTED_QUERY_TYPES.stream().anyMatch(x -> x.isAssignableFrom(query.getClass()))) {
            BoolQueryBuilder rewriteQuery = new BoolQueryBuilder();
            rewriteQuery.must(query);
            rewriteQuery.filter(boolQueryBuilder);
            searchSourceBuilder.query(rewriteQuery);
        } else {
            throw new MLValidationException("Search API only supports [bool, ids, match, match_all, term, terms, exists, range] query type");
        }
        return searchSourceBuilder;
    }

    public static SearchSourceBuilder createSearchSourceBuilder(User user) {
        return addUserBackendRolesFilter(user, new SearchSourceBuilder());
    }

}
