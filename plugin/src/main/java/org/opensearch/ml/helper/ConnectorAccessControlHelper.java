/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.helper;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_CONNECTOR_INDEX;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED;
import static org.opensearch.ml.utils.RestActionUtils.getFetchSourceContext;

import org.apache.lucene.search.join.ScoreMode;
import org.opensearch.ExceptionsHelper;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.client.Client;
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
import org.opensearch.index.query.NestedQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.connector.AbstractConnector;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.utils.MLNodeUtils;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.fetch.subphase.FetchSourceContext;

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

    public boolean hasPermission(User user, Connector connector) {
        return accessControlNotEnabled(user)
            || isAdmin(user)
            || previouslyPublicConnector(connector)
            || isPublicConnector(connector)
            || (isPrivateConnector(connector) && isOwner(user, connector.getOwner()))
            || (isRestrictedConnector(connector) && isUserHasBackendRole(user, connector));
    }

    public void validateConnectorAccess(Client client, String connectorId, ActionListener<Boolean> listener) {
        User user = RestActionUtils.getUserContext(client);
        if (isAdmin(user) || accessControlNotEnabled(user)) {
            listener.onResponse(true);
            return;
        }
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<Boolean> wrappedListener = ActionListener.runBefore(listener, context::restore);
            getConnector(client, connectorId, ActionListener.wrap(connector -> {
                boolean hasPermission = hasPermission(user, connector);
                wrappedListener.onResponse(hasPermission);
            }, wrappedListener::onFailure));
        } catch (Exception e) {
            log.error("Failed to validate Access for connector:{}", connectorId, e);
            listener.onFailure(e);
        }
    }

    public void validateConnectorAccess(
        SdkClient sdkClient,
        Client client,
        String connectorId,
        String tenantId,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        ActionListener<Boolean> listener
    ) {

        User user = RestActionUtils.getUserContext(client);
        if (!mlFeatureEnabledSetting.isMultiTenancyEnabled()) {
            if (isAdmin(user) || accessControlNotEnabled(user)) {
                listener.onResponse(true);
                return;
            }
        }
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<Boolean> wrappedListener = ActionListener.runBefore(listener, context::restore);
            FetchSourceContext fetchSourceContext = getFetchSourceContext(true);
            GetDataObjectRequest getDataObjectRequest = GetDataObjectRequest
                .builder()
                .index(ML_CONNECTOR_INDEX)
                .tenantId(tenantId)
                .id(connectorId)
                .fetchSourceContext(fetchSourceContext)
                .build();
            getConnector(sdkClient, client, context, getDataObjectRequest, connectorId, ActionListener.wrap(connector -> {
                if (TenantAwareHelper.validateTenantResource(mlFeatureEnabledSetting, tenantId, connector.getTenantId(), listener)) {
                    boolean hasPermission = hasPermission(user, connector);
                    wrappedListener.onResponse(hasPermission);
                }
            }, wrappedListener::onFailure));
        } catch (Exception e) {
            log.error("Failed to validate Access for connector:{}", connectorId, e);
            listener.onFailure(e);
        }
    }

    public boolean validateConnectorAccess(Client client, Connector connector) {
        User user = RestActionUtils.getUserContext(client);
        if (isAdmin(user) || accessControlNotEnabled(user)) {
            return true;
        }
        return hasPermission(user, connector);
    }

    // TODO will remove this method in favor of other getConnector method. This method is still being used in update model/update connect.
    // I'll remove this method when I'll refactor update methods.
    public void getConnector(Client client, String connectorId, ActionListener<Connector> listener) {
        GetRequest getRequest = new GetRequest().index(CommonValue.ML_CONNECTOR_INDEX).id(connectorId);
        client.get(getRequest, ActionListener.wrap(r -> {
            if (r != null && r.isExists()) {
                try (
                    XContentParser parser = MLNodeUtils
                        .createXContentParserFromRegistry(NamedXContentRegistry.EMPTY, r.getSourceAsBytesRef())
                ) {
                    ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                    Connector connector = Connector.createConnector(parser);
                    listener.onResponse(connector);
                } catch (Exception e) {
                    log.error("Failed to parse connector:{}", connectorId);
                    listener.onFailure(e);
                }
            } else {
                listener.onFailure(new OpenSearchStatusException("Failed to find connector:" + connectorId, RestStatus.NOT_FOUND));
            }
        }, e -> {
            log.error("Failed to get connector", e);
            listener.onFailure(new OpenSearchStatusException("Failed to get connector:" + connectorId, RestStatus.NOT_FOUND));
        }));
    }

    /**
     * Gets a connector with the provided clients.
     * @param sdkClient The SDKClient
     * @param client The OpenSearch client for thread pool management
     * @param context The Stored Context.  Executing this method will restore this context.
     * @param getDataObjectRequest The get request
     * @param connectorId The connector Id
     * @param listener the action listener to complete with the GetResponse or Exception
     */
    public void getConnector(
        SdkClient sdkClient,
        Client client,
        ThreadContext.StoredContext context,
        GetDataObjectRequest getDataObjectRequest,
        String connectorId,
        ActionListener<Connector> listener
    ) {

        sdkClient.getDataObjectAsync(getDataObjectRequest).whenComplete((r, throwable) -> {
            context.restore();
            log.debug("Completed Get Connector Request, id:{}", connectorId);
            if (throwable != null) {
                Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                if (ExceptionsHelper.unwrap(throwable, IndexNotFoundException.class) != null) {
                    log.error("Failed to get connector index", cause);
                    listener.onFailure(new OpenSearchStatusException("Failed to find connector", RestStatus.NOT_FOUND));
                } else {
                    log.error("Failed to get ML connector {}", connectorId, cause);
                    listener.onFailure(cause);
                }
            } else {
                try {
                    GetResponse gr = r.getResponse();
                    if (gr != null && gr.isExists()) {
                        try (
                            XContentParser parser = jsonXContent
                                .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, gr.getSourceAsString())
                        ) {
                            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                            Connector mlConnector = Connector.createConnector(parser);
                            mlConnector.removeCredential();
                            listener.onResponse(mlConnector);
                        } catch (Exception e) {
                            log.error("Failed to parse ml connector {}", r.id(), e);
                            listener.onFailure(e);
                        }
                    } else {
                        listener
                            .onFailure(
                                new OpenSearchStatusException(
                                    "Failed to find connector with the provided connector id: " + connectorId,
                                    RestStatus.NOT_FOUND
                                )
                            );
                    }
                } catch (Exception e) {
                    listener.onFailure(e);
                }
            }
        });

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

    private boolean isOwner(User owner, User user) {
        if (user == null || owner == null) {
            return false;
        }
        return owner.getName().equals(user.getName());
    }

    private boolean isUserHasBackendRole(User user, Connector connector) {
        AccessMode modelAccessMode = connector.getAccess();
        return AccessMode.RESTRICTED == modelAccessMode
            && (user.getBackendRoles() != null
                && connector.getBackendRoles() != null
                && connector.getBackendRoles().stream().anyMatch(x -> user.getBackendRoles().contains(x)));
    }

    private boolean previouslyPublicConnector(Connector connector) {
        return connector.getOwner() == null;
    }

    private boolean isPublicConnector(Connector connector) {
        return AccessMode.PUBLIC == connector.getAccess();
    }

    private boolean isPrivateConnector(Connector connector) {
        return AccessMode.PRIVATE == connector.getAccess();
    }

    private boolean isRestrictedConnector(Connector connector) {
        return AccessMode.RESTRICTED == connector.getAccess();
    }

    public SearchSourceBuilder addUserBackendRolesFilter(User user, SearchSourceBuilder searchSourceBuilder) {
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        boolQueryBuilder.should(QueryBuilders.termQuery(AbstractConnector.ACCESS_FIELD, AccessMode.PUBLIC.getValue()));
        boolQueryBuilder.should(QueryBuilders.termsQuery(AbstractConnector.BACKEND_ROLES_FIELD + ".keyword", user.getBackendRoles()));

        BoolQueryBuilder privateBoolQuery = new BoolQueryBuilder();
        String ownerName = "owner.name.keyword";
        TermQueryBuilder ownerNameTermQuery = QueryBuilders.termQuery(ownerName, user.getName());
        NestedQueryBuilder nestedQueryBuilder = new NestedQueryBuilder(AbstractConnector.OWNER_FIELD, ownerNameTermQuery, ScoreMode.None);
        privateBoolQuery.must(nestedQueryBuilder);
        privateBoolQuery.must(QueryBuilders.termQuery(AbstractConnector.ACCESS_FIELD, AccessMode.PRIVATE.getValue()));
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
