/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import lombok.extern.log4j.Log4j2;
import org.apache.lucene.search.join.ScoreMode;
import org.opensearch.action.ActionListener;
import org.opensearch.action.get.GetRequest;
import org.opensearch.client.Client;
import org.opensearch.common.util.CollectionUtils;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.NestedQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.ml.common.MLModelGroup;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.exception.MLValidationException;
import org.opensearch.search.builder.SearchSourceBuilder;

import java.util.List;
import java.util.stream.Collectors;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_INDEX;

@Log4j2
public class SecurityUtils {

    public static void validateModelGroupAccess(User user, String modelGroupId, Client client, ActionListener<Boolean> listener) {
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
                            listener.onResponse(true);
                        } else if (MLModelGroup.PUBLIC.equals(mlModelGroup.getAccess())) {
                            listener.onResponse(true);
                        } else if (MLModelGroup.PRIVATE.equals(mlModelGroup.getAccess())) {
                            if (isOwner(mlModelGroup.getOwner(), user)) {
                                listener.onResponse(true);
                                return;
                            }
                            listener.onResponse(false);
                        } else {
                            listener
                                .onResponse(
                                    userBackendRoles
                                        .stream()
                                        .anyMatch(mlModelGroup.getBackendRoles().stream().collect(Collectors.toSet())::contains)
                                );
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
        boolQueryBuilder.should(QueryBuilders.termsQuery("backend_roles.keyword", user.getBackendRoles()));

        BoolQueryBuilder privateBoolQuery = new BoolQueryBuilder();
        String path = "owner";
        String ownerName = "owner.name.keyword";
        TermQueryBuilder ownerNameTermQuery = QueryBuilders.termQuery(ownerName, user.getName());
        NestedQueryBuilder nestedQueryBuilder = new NestedQueryBuilder(path, ownerNameTermQuery, ScoreMode.None);
        privateBoolQuery.must(nestedQueryBuilder);
        privateBoolQuery.must(QueryBuilders.termQuery(MLModelGroup.ACCESS, MLModelGroup.PRIVATE));
        boolQueryBuilder.should(privateBoolQuery);
        QueryBuilder query = searchSourceBuilder.query();
        if (query == null) {
            searchSourceBuilder.query(boolQueryBuilder);
        } else if (query instanceof BoolQueryBuilder) {
            ((BoolQueryBuilder) query).filter(boolQueryBuilder);
        } else {
            throw new MLValidationException("Search API does not support queries other than BoolQuery");
        }
        return searchSourceBuilder;
    }

}
