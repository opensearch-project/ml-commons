/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.helper;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.apache.lucene.search.join.ScoreMode;
import org.opensearch.action.ActionListener;
import org.opensearch.action.get.GetRequest;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.CollectionUtils;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.NestedQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.connector.template.DetachedConnector;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.exception.MLValidationException;
import org.opensearch.ml.utils.MLNodeUtils;
import org.opensearch.search.builder.SearchSourceBuilder;

import com.google.common.collect.ImmutableList;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class ConnectorAccessControlHelper {

    private volatile Boolean connectorAccessControlEnabled;

    public ConnectorAccessControlHelper(ClusterService clusterService, Settings settings) {
        connectorAccessControlEnabled = ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED.get(settings);
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED, it -> connectorAccessControlEnabled = it);
    }

    public boolean hasPermission(User user, DetachedConnector connector) {
        return accessControlNotEnabled(user)
            || isAdmin(user)
            || (isOwner(user, connector.getOwner()) && isOwnerStillHasPermission(user, connector))
            || isUserHasBackendRole(user, connector);
    }

    public void validateConnectorAccess(User user, Client client, String connectorId, ActionListener<Boolean> listener) {
        if (isAdmin(user) || accessControlNotEnabled(user)) {
            listener.onResponse(true);
            return;
        }

        List<String> userBackendRoles = user.getBackendRoles();
        GetRequest getRequest = new GetRequest().index(CommonValue.ML_CONNECTOR_INDEX).id(connectorId);
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<Boolean> wrappedListener = ActionListener.runBefore(listener, context::restore);
            client.get(getRequest, ActionListener.wrap(r -> {
                if (r != null && r.isExists()) {
                    try (
                        XContentParser parser = MLNodeUtils
                            .createXContentParserFromRegistry(NamedXContentRegistry.EMPTY, r.getSourceAsBytesRef())
                    ) {
                        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                        DetachedConnector detachedConnector = DetachedConnector.parse(parser);
                        AccessMode modelAccessMode = AccessMode.from(detachedConnector.getAccess());
                        if (detachedConnector.getOwner() == null) {
                            // previous security plugin not enabled, model defaults to public.
                            wrappedListener.onResponse(true);
                        } else if (AccessMode.RESTRICTED == modelAccessMode) {
                            if (detachedConnector.getBackendRoles() == null || detachedConnector.getBackendRoles().size() == 0) {
                                throw new IllegalStateException("Backend roles shouldn't be null");
                            } else {
                                wrappedListener
                                    .onResponse(
                                        Optional
                                            .ofNullable(userBackendRoles)
                                            .orElse(ImmutableList.of())
                                            .stream()
                                            .anyMatch(detachedConnector.getBackendRoles()::contains)
                                    );
                            }
                        } else if (AccessMode.PUBLIC == modelAccessMode) {
                            wrappedListener.onResponse(true);
                        } else if (AccessMode.PRIVATE == modelAccessMode) {
                            if (isOwner(detachedConnector.getOwner(), user))
                                wrappedListener.onResponse(true);
                            else
                                wrappedListener.onResponse(false);
                        }
                    } catch (Exception e) {
                        log.error("Failed to parse connector");
                        wrappedListener.onFailure(e);
                    }
                } else {
                    wrappedListener.onFailure(new MLResourceNotFoundException("Fail to find connector"));
                }
            }, e -> {
                log.error("Fail to get connector", e);
                wrappedListener.onFailure(new MLValidationException("Fail to get connector"));
            }));
        } catch (Exception e) {
            log.error("Failed to validate Access", e);
            listener.onFailure(e);
        }

    }

    public boolean skipConnectorAccessControl(User user) {
        // Case 1: user == null when 1. Security is disabled. 2. When user is super-admin
        // Case 2: If Security is enabled and filter is disabled, proceed with search as
        // user is already authenticated to hit this API.
        // case 3: user is admin which means we don't have to check backend role filtering
        return user == null || !connectorAccessControlEnabled || isAdmin(user);
    }

    public boolean accessControlNotEnabled(User user) {
        return user == null || !connectorAccessControlEnabled;
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

    public boolean isUserHasBackendRole(User user, DetachedConnector detachedConnector) {
        AccessMode modelAccessMode = AccessMode.from(detachedConnector.getAccess());
        if (AccessMode.PUBLIC == modelAccessMode)
            return true;
        if (AccessMode.PRIVATE == modelAccessMode)
            return false;
        return user.getBackendRoles() != null
            && detachedConnector.getBackendRoles() != null
            && detachedConnector.getBackendRoles().stream().anyMatch(x -> user.getBackendRoles().contains(x));
    }

    public boolean isOwnerStillHasPermission(User user, DetachedConnector connector) {
        // when security plugin is disabled, or model access control not enabled, the model is a public model and anyone has permission to
        // it.
        if (accessControlNotEnabled(user))
            return true;
        AccessMode access = AccessMode.from(connector.getAccess());
        if (AccessMode.PUBLIC == access) {
            return true;
        } else if (AccessMode.PRIVATE == access) {
            return isOwner(user, connector.getOwner());
        } else if (AccessMode.RESTRICTED == access) {
            if (CollectionUtils.isEmpty(connector.getBackendRoles())) {
                throw new IllegalStateException("Backend roles should not be null");
            }
            return user.getBackendRoles() != null
                && new HashSet<>(connector.getBackendRoles()).stream().anyMatch(x -> user.getBackendRoles().contains(x));
        }
        throw new IllegalStateException("Access shouldn't be null");
    }

    public boolean getConnectorAccessControlEnabled() {
        return connectorAccessControlEnabled;
    }

    public SearchSourceBuilder addUserBackendRolesFilter(User user, SearchSourceBuilder searchSourceBuilder) {
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        boolQueryBuilder.should(QueryBuilders.termQuery(DetachedConnector.ACCESS_FIELD, AccessMode.PUBLIC.getValue()));
        boolQueryBuilder.should(QueryBuilders.termsQuery(DetachedConnector.BACKEND_ROLES_FIELD + ".keyword", user.getBackendRoles()));

        BoolQueryBuilder privateBoolQuery = new BoolQueryBuilder();
        String ownerName = "owner.name.keyword";
        TermQueryBuilder ownerNameTermQuery = QueryBuilders.termQuery(ownerName, user.getName());
        NestedQueryBuilder nestedQueryBuilder = new NestedQueryBuilder(DetachedConnector.OWNER_FIELD, ownerNameTermQuery, ScoreMode.None);
        privateBoolQuery.must(nestedQueryBuilder);
        privateBoolQuery.must(QueryBuilders.termQuery(DetachedConnector.ACCESS_FIELD, AccessMode.PRIVATE.getValue()));
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
}
