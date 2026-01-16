/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import static org.opensearch.ml.common.MLModel.MODEL_CONTENT_FIELD;
import static org.opensearch.ml.common.MLModel.OLD_MODEL_CONTENT_FIELD;

import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.ExceptionsHelper;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.Nullable;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.connector.ConnectorAction.ActionType;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestRequest;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.search.internal.InternalSearchResponse;
import org.opensearch.transport.client.Client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class RestActionUtils {

    private static final Logger logger = LogManager.getLogger(RestActionUtils.class);

    public static final String SECURITY_AUTHCZ_ADMIN_DN = "plugins.security.authcz.admin_dn";

    public static final String PARAMETER_ALGORITHM = "algorithm";
    public static final String PARAMETER_ASYNC = "async";
    public static final String PARAMETER_RETURN_CONTENT = "return_content";
    public static final String PARAMETER_MODEL_ID = "model_id";
    public static final String PARAMETER_AGENT_ID = "agent_id";
    public static final String PARAMETER_TASK_ID = "task_id";
    public static final String PARAMETER_CONNECTOR_ID = "connector_id";
    public static final String PARAMETER_INDEX_ID = "index_id";
    public static final String PARAMETER_DEPLOY_MODEL = "deploy";
    public static final String PARAMETER_VERSION = "version";
    public static final String PARAMETER_MODEL_GROUP_ID = "model_group_id";
    public static final String PARAMETER_CONFIG_ID = "config_id";
    public static final String OPENSEARCH_DASHBOARDS_USER_AGENT = "OpenSearch Dashboards";
    public static final String[] UI_METADATA_EXCLUDE = new String[] { "ui_metadata" };

    public static final String PARAMETER_TOOL_NAME = "tool_name";
    public static final String PARAMETER_TEMPLATE_NAME = "template_name";

    public static final String OPENDISTRO_SECURITY_CONFIG_PREFIX = "_opendistro_security_";

    public static final String OPENDISTRO_SECURITY_USER = OPENDISTRO_SECURITY_CONFIG_PREFIX + "user";

    static final Set<LdapName> adminDn = new HashSet<>();
    static final Set<String> adminUsernames = new HashSet<String>();
    static final ObjectMapper objectMapper = new ObjectMapper();

    public static String getAlgorithm(RestRequest request) {
        String algorithm = request.param(PARAMETER_ALGORITHM);
        if (Strings.isNullOrEmpty(algorithm)) {
            throw new IllegalArgumentException("Request should contain algorithm!");
        }
        return algorithm.toUpperCase(Locale.ROOT);
    }

    public static boolean isAsync(RestRequest request) {
        return request.paramAsBoolean(PARAMETER_ASYNC, false);
    }

    public static boolean returnContent(RestRequest request) {
        return request.paramAsBoolean(PARAMETER_RETURN_CONTENT, false);
    }

    /**
     * Get the Model or Task id from a RestRequest
     *
     * @param request RestRequest
     * @param idName  ID name for example "model_id"
     * @return id for model or task
     */
    public static String getParameterId(RestRequest request, String idName) {
        String id = request.param(idName);
        if (Strings.isNullOrEmpty(id)) {
            throw new IllegalArgumentException("Request should contain " + idName);
        }
        return id;
    }

    /**
     * Checks to see if the request came from OpenSearch Dashboards, if so we want to return the UI Metadata from the document.
     * If the request came from the client then we exclude the UI Metadata from the search result.
     *
     * @param request rest request
     * @param searchSourceBuilder instance of searchSourceBuilder to fetch includes and excludes
     * @return instance of {@link org.opensearch.search.fetch.subphase.FetchSourceContext}
     */
    public static FetchSourceContext getSourceContext(RestRequest request, SearchSourceBuilder searchSourceBuilder) {
        String userAgent = coalesceToEmpty(request.header("User-Agent"));
        if (searchSourceBuilder.fetchSource() != null) {
            final String[] includes = searchSourceBuilder.fetchSource().includes();
            final String[] excludes = searchSourceBuilder.fetchSource().excludes();
            if (!ArrayUtils.contains(includes, MODEL_CONTENT_FIELD)) {
                ArrayUtils.add(excludes, MODEL_CONTENT_FIELD);
            }
            if (!ArrayUtils.contains(includes, OLD_MODEL_CONTENT_FIELD)) {
                ArrayUtils.add(excludes, OLD_MODEL_CONTENT_FIELD);
            }
            String[] metadataExcludes = new String[excludes.length + 1];
            if (!userAgent.contains(OPENSEARCH_DASHBOARDS_USER_AGENT)) {
                if (excludes.length == 0) {
                    return new FetchSourceContext(true, includes, UI_METADATA_EXCLUDE);
                } else {
                    System.arraycopy(excludes, 0, metadataExcludes, 0, excludes.length);
                    metadataExcludes[metadataExcludes.length - 1] = "ui_metadata";
                    return new FetchSourceContext(true, includes, metadataExcludes);
                }
            } else {
                return new FetchSourceContext(true, includes, excludes);
            }
        } else {
            // When user does not set the _source field in search model api request, searchSourceBuilder.fetchSource becomes null
            String[] excludes = new String[] { OLD_MODEL_CONTENT_FIELD, MODEL_CONTENT_FIELD };
            if (!userAgent.contains(OPENSEARCH_DASHBOARDS_USER_AGENT)) {
                return new FetchSourceContext(true, Strings.EMPTY_ARRAY, ArrayUtils.add(excludes, "ui_metadata"));
            } else {
                return new FetchSourceContext(true, Strings.EMPTY_ARRAY, excludes);
            }
        }
    }

    /**
     * Return FetchSourceContext
     * @param returnModelContent if the model content should be returned
     */
    public static FetchSourceContext getFetchSourceContext(boolean returnModelContent) {
        if (!returnModelContent) {
            return new FetchSourceContext(true, Strings.EMPTY_ARRAY, new String[] { OLD_MODEL_CONTENT_FIELD, MODEL_CONTENT_FIELD });
        }
        return new FetchSourceContext(true, Strings.EMPTY_ARRAY, Strings.EMPTY_ARRAY);
    }

    /**
     * Return all nodes in the cluster
     * @param clusterService the cluster service
     */
    public static String[] getAllNodes(ClusterService clusterService) {
        Iterator<DiscoveryNode> iterator = clusterService.state().nodes().iterator();
        List<String> nodeIds = new ArrayList<>();
        while (iterator.hasNext()) {
            nodeIds.add(iterator.next().getId());
        }
        return nodeIds.toArray(new String[0]);
    }

    /**
     *
     * @param channel RestChannel
     * @param status RestStatus enums
     * @param errorMessage Error messages
     * @param exception Reported Exception
     */
    public static void onFailure(RestChannel channel, RestStatus status, String errorMessage, Exception exception) {
        BytesRestResponse bytesRestResponse;
        try {
            bytesRestResponse = new BytesRestResponse(channel, exception);
        } catch (Exception e) {
            bytesRestResponse = new BytesRestResponse(status, errorMessage);
        }
        channel.sendResponse(bytesRestResponse);
    }

    @VisibleForTesting
    public static Optional<String[]> splitCommaSeparatedParam(RestRequest request, String paramName) {
        return Optional.ofNullable(request.param(paramName)).map(s -> s.split(","));
    }

    private static String coalesceToEmpty(@Nullable String s) {
        return s == null ? "" : s;
    }

    public static Optional<String> getStringParam(RestRequest request, String paramName) {
        return Optional.ofNullable(request.param(paramName));
    }

    /**
     * Generates a user string formed by the username, backend roles, roles and requested tenants separated by '|'
     * (e.g., john||own_index,testrole|__user__, no backend role so you see two verticle line after john.).
     * This is the user string format used internally in the OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT and may be
     * parsed using User.parse(string).
     * @param client Client containing user info. A public API request will fill in the user info in the thread context.
     * @return parsed user object
     */
    public static User getUserContext(Client client) {
        String userStr = client.threadPool().getThreadContext().getTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT);
        logger.debug("Current user is " + userStr);
        return User.parse(userStr);
    }

    // TODO: Integration test needs to be added (MUST)
    @SuppressWarnings("removal")
    public static boolean isSuperAdminUser(ClusterService clusterService, Client client) {

        final List<String> adminDnsA = clusterService.getSettings().getAsList(SECURITY_AUTHCZ_ADMIN_DN, Collections.emptyList());

        for (String dn : adminDnsA) {
            try {
                logger.debug("{} is registered as an admin dn", dn);
                adminDn.add(new LdapName(dn));
            } catch (final InvalidNameException e) {
                logger.debug("Unable to parse admin dn {}", dn, e);
                adminUsernames.add(dn);
            }
        }

        Object userObject = client.threadPool().getThreadContext().getTransient(OPENDISTRO_SECURITY_USER);
        if (userObject == null)
            return false;
        try {
            return AccessController.doPrivileged((PrivilegedExceptionAction<Boolean>) () -> {
                String userContext = objectMapper.writeValueAsString(userObject);
                final JsonNode node = objectMapper.readTree(userContext);
                final String userName = node.get("name").asText();

                return isAdminDN(userName);
            });
        } catch (PrivilegedActionException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isAdminDN(String dn) {
        if (dn == null)
            return false;
        try {
            return isAdminDN(new LdapName(dn));
        } catch (InvalidNameException e) {
            return adminUsernames.contains(dn);
        }
    }

    private static boolean isAdminDN(LdapName dn) {
        if (dn == null)
            return false;
        boolean isAdmin = adminDn.contains(dn);
        if (logger.isTraceEnabled()) {
            logger.trace("Is principal {} an admin cert? {}", dn.toString(), isAdmin);
        }
        return isAdmin;
    }

    /**
     * Utility to wrap over an action listener to handle index not found error to return empty results instead of failing.
     * This is important when the user is performing a search request against connectors/models/model groups/tasks or other constructs that
     * do not imply an index error but rather imply no items found.
     * @see <a href=https://github.com/opensearch-project/ml-commons/issues/1787>Issue 1787</a>
     * @see <a href=https://github.com/opensearch-project/ml-commons/issues/1778>Issue 1878</a>
     * @see <a href=https://github.com/opensearch-project/ml-commons/issues/1879>Issue 1879</a>
     * @see <a href=https://github.com/opensearch-project/ml-commons/issues/1880>Issue 1880</a>
     * @param e Exception to wrap
     * @param listener ActionListener for a search response to wrap
     */
    public static void wrapListenerToHandleSearchIndexNotFound(Exception e, ActionListener<SearchResponse> listener) {
        if (ExceptionsHelper.unwrapCause(e) instanceof IndexNotFoundException
            || ExceptionsHelper.unwrap(e, IndexNotFoundException.class) != null) {
            log.debug("Connectors index not created yet, therefore we will swallow the exception and return an empty search result");
            final InternalSearchResponse internalSearchResponse = InternalSearchResponse.empty();
            final SearchResponse emptySearchResponse = new SearchResponse(
                internalSearchResponse,
                null,
                0,
                0,
                0,
                0,
                null,
                new ShardSearchFailure[] {},
                SearchResponse.Clusters.EMPTY,
                null
            );
            listener.onResponse(emptySearchResponse);
        } else {
            listener.onFailure(e);
        }
    }

    /**
     * Determine the ActionType from the restful request by checking the url path and method name so there's no need
     * to specify the ActionType in the request body. For example, /_plugins/_ml/models/{model_id}/_predict will return
     * PREDICT as the ActionType, and /_plugins/_ml/models/{model_id}/_batch_predict will return BATCH_PREDICT.
     * @param request A Restful request that needs to determine the ActionType from the path.
     * @return parsed user object
     */
    public static String getActionTypeFromRestRequest(RestRequest request) {
        String path = request.path();
        String[] segments = path.split("/");
        String methodName = segments[segments.length - 1];
        if ("stream".equals(methodName)) {
            methodName = segments[segments.length - 2];
        }
        methodName = methodName.startsWith("_") ? methodName.substring(1) : methodName;

        // find the action type for "/_plugins/_ml/_predict/<algorithm>/<model_id>"
        if (!ActionType.isValidAction(methodName) && segments.length > 3) {
            methodName = segments[3];
            methodName = methodName.contains("_") ? methodName.split("_")[1] : methodName;
        }
        return methodName;
    }

    /**
     * Checks if the REST request contains any MCP (Model Context Protocol) headers.
     *
     * @param request RestRequest to check for MCP headers
     * @return true if any MCP headers are present, false otherwise
     */
    public static boolean hasMcpHeaders(RestRequest request) {
        return request.header(CommonValue.MCP_HEADER_AWS_ACCESS_KEY_ID) != null
            || request.header(CommonValue.MCP_HEADER_AWS_SECRET_ACCESS_KEY) != null
            || request.header(CommonValue.MCP_HEADER_AWS_SESSION_TOKEN) != null
            || request.header(CommonValue.MCP_HEADER_AWS_REGION) != null
            || request.header(CommonValue.MCP_HEADER_AWS_SERVICE_NAME) != null
            || request.header(CommonValue.MCP_HEADER_OPENSEARCH_URL) != null;
    }

    /**
     * Extracts MCP (Model Context Protocol) request headers from the REST request and puts them in ThreadContext.
     *
     * @param request RestRequest containing the MCP headers
     * @param client Client to access ThreadContext
     */
    public static void putMcpRequestHeaders(RestRequest request, Client client) {
        if (client == null) {
            return;
        }

        ThreadContext threadContext = client.threadPool().getThreadContext();

        String accessKeyId = request.header(CommonValue.MCP_HEADER_AWS_ACCESS_KEY_ID);
        if (accessKeyId != null && !accessKeyId.isEmpty()) {
            threadContext.putHeader(CommonValue.MCP_HEADER_AWS_ACCESS_KEY_ID, accessKeyId);
            log.debug("Put MCP header: {}", CommonValue.MCP_HEADER_AWS_ACCESS_KEY_ID);
        }

        String secretAccessKey = request.header(CommonValue.MCP_HEADER_AWS_SECRET_ACCESS_KEY);
        if (secretAccessKey != null && !secretAccessKey.isEmpty()) {
            threadContext.putHeader(CommonValue.MCP_HEADER_AWS_SECRET_ACCESS_KEY, secretAccessKey);
            log.debug("Put MCP header: {}", CommonValue.MCP_HEADER_AWS_SECRET_ACCESS_KEY);
        }

        String sessionToken = request.header(CommonValue.MCP_HEADER_AWS_SESSION_TOKEN);
        if (sessionToken != null && !sessionToken.isEmpty()) {
            threadContext.putHeader(CommonValue.MCP_HEADER_AWS_SESSION_TOKEN, sessionToken);
            log.debug("Put MCP header: {}", CommonValue.MCP_HEADER_AWS_SESSION_TOKEN);
        }

        String region = request.header(CommonValue.MCP_HEADER_AWS_REGION);
        if (region != null && !region.isEmpty()) {
            threadContext.putHeader(CommonValue.MCP_HEADER_AWS_REGION, region);
            log.debug("Put MCP header: {}", CommonValue.MCP_HEADER_AWS_REGION);
        }

        String serviceName = request.header(CommonValue.MCP_HEADER_AWS_SERVICE_NAME);
        if (serviceName != null && !serviceName.isEmpty()) {
            threadContext.putHeader(CommonValue.MCP_HEADER_AWS_SERVICE_NAME, serviceName);
            log.debug("Put MCP header: {}", CommonValue.MCP_HEADER_AWS_SERVICE_NAME);
        }

        String opensearchUrl = request.header(CommonValue.MCP_HEADER_OPENSEARCH_URL);
        if (opensearchUrl != null && !opensearchUrl.isEmpty()) {
            threadContext.putHeader(CommonValue.MCP_HEADER_OPENSEARCH_URL, opensearchUrl);
            log.debug("Put MCP header: {}", CommonValue.MCP_HEADER_OPENSEARCH_URL);
        }
    }

}
