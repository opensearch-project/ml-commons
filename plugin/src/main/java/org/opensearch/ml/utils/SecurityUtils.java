/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_INDEX;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.log4j.Log4j2;

import org.apache.commons.lang3.StringUtils;
import org.opensearch.action.ActionListener;
import org.opensearch.action.get.GetRequest;
import org.opensearch.client.Client;
import org.opensearch.common.util.CollectionUtils;
import org.opensearch.commons.authuser.User;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.TermsQueryBuilder;
import org.opensearch.ml.common.MLModelGroup;
import org.opensearch.ml.common.exception.MLValidationException;
import org.opensearch.search.builder.SearchSourceBuilder;

import com.google.common.collect.ImmutableList;

@Log4j2
public class SecurityUtils {

    public static void validateModelGroupAccess(User user, String modelGroupId, Client client, ActionListener<Boolean> listener) {
        if (modelGroupId == null || isAdmin(user)) {
            listener.onResponse(Boolean.TRUE);
            return;
        }

        List<String> userBackendRoles = user.getBackendRoles();

        GetRequest getModelGroupRequest = new GetRequest(ML_MODEL_GROUP_INDEX).id(modelGroupId);
        try {
            client.get(getModelGroupRequest, ActionListener.wrap(r -> {
                if (r != null && r.isExists()) {
                    List<String> modelGroupBackendRoles = new ArrayList<>();
                    String access = null;
                    Map<String, Object> owner = null;
                    Map<String, Object> source = r.getSourceAsMap();
                    if (source.containsKey(MLModelGroup.ACCESS)) {
                        access = (String) source.get(MLModelGroup.ACCESS);
                    }
                    if (source.containsKey(MLModelGroup.BACKEND_ROLES_FIELD) && source.get(MLModelGroup.BACKEND_ROLES_FIELD) != null) {
                        modelGroupBackendRoles = (List<String>) source.get(MLModelGroup.BACKEND_ROLES_FIELD);
                    }
                    if (source.containsKey(MLModelGroup.OWNER)) {
                        owner = (Map) source.get(MLModelGroup.OWNER);
                    }
                    if (access != null && StringUtils.equals(access, MLModelGroup.PUBLIC)) {
                        listener.onResponse(Boolean.TRUE);
                    } else if (access != null && StringUtils.equals(access, MLModelGroup.PRIVATE)) {
                        if (isOwner(owner, user)) {
                            listener.onResponse(Boolean.TRUE);
                            return;
                        }
                        listener.onResponse(Boolean.FALSE);
                    } else {
                        listener
                            .onResponse(
                                userBackendRoles.stream().anyMatch(modelGroupBackendRoles.stream().collect(Collectors.toSet())::contains)
                            );
                    }
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

    public static boolean isOwner(Map<String, Object> owner, User user) {
        if (user == null || owner == null || !owner.containsKey("name")) {
            return false;
        }
        return StringUtils.equals((String) owner.get("name"), user.getName());
    }

    public static SearchSourceBuilder addUserBackendRolesFilter(User user, SearchSourceBuilder searchSourceBuilder) {
        if (user == null) {
            return searchSourceBuilder;
        }
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        String ownerBackendRoleFieldName = "backend_roles.keyword";
        List<String> backendRoles = user.getBackendRoles() != null ? user.getBackendRoles() : ImmutableList.of();
        TermsQueryBuilder userRolesFilterQuery = QueryBuilders.termsQuery(ownerBackendRoleFieldName, backendRoles);
        boolQueryBuilder.must(userRolesFilterQuery);
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
