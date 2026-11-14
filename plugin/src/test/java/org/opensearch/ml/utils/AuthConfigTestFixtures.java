/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_TRUSTED_OAUTH2_TOKEN_ENDPOINTS_REGEX;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.opensearch.Version;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodeRole;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.ConnectorProtocols;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.connector.McpConnector;
import org.opensearch.ml.common.connector.McpStreamableHttpConnector;
import org.opensearch.ml.common.connector.auth.ConnectorAuthType;
import org.opensearch.ml.common.connector.auth.OAuth2AuthConfig;
import org.opensearch.ml.common.connector.auth.TokenEndpointAllowlist;
import org.opensearch.ml.common.connector.auth.TokenResponseSpec;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.test.MockLogAppender;
import org.opensearch.test.OpenSearchTestCase;

import tools.jackson.databind.JsonNode;

/**
 * Fixtures shared by the {@code auth_config} tests of this module: cluster states for the version guard, the
 * configurations the tests store, one connector per protocol, documents as JSON trees, and a log watcher.
 */
public final class AuthConfigTestFixtures {

    private AuthConfigTestFixtures() {}

    /** A token URL the default token endpoint patterns admit. */
    public static final String TOKEN_URL = "https://login.microsoftonline.com/t/oauth2/v2.0/token";
    /** The credential key {@link #oauth2()} references. */
    public static final String SECRET_KEY = "api_key";
    /** The node version {@link #oldNodeCluster()} holds, below the version the guard requires. */
    public static final Version OLD_VERSION = CommonValue.VERSION_3_9_0;
    public static final String ACTION_URL = "https://api.openai.com/v1/chat/completions";
    /** The value a stored credential holds: already encrypted, never a plaintext the request supplies. */
    public static final String STORED_CIPHERTEXT = "ciphertext";
    /** The token endpoint patterns of a node with default settings. */
    public static final TokenEndpointAllowlist DEFAULT_TOKEN_ALLOWLIST = new TokenEndpointAllowlist(
        ML_COMMONS_TRUSTED_OAUTH2_TOKEN_ENDPOINTS_REGEX.getDefault(Settings.EMPTY)
    );
    /** The document {@link OAuth2AuthConfig#NONE} emits. */
    public static final JsonNode NONE_DOCUMENT = tree("{\"type\":\"none\"}");
    /** The document a {@code none} update over {@link #fullOauth2()} writes: every stored member marked as removed. */
    public static final JsonNode NONE_OVER_FULL_OAUTH2_DOCUMENT = tree(
        "{\"type\":\"none\",\"token_url\":[],\"grant_type\":[],\"client_auth_method\":[],\"client_id\":[],\"client_secret\":[],"
            + "\"scope\":[],\"audience\":[],\"token_request_params\":[],\"token_response\":[]}"
    );

    /** An oauth2 configuration that passes every write check with the default token endpoint patterns. */
    public static OAuth2AuthConfig oauth2() {
        return oauth2Builder()
            .tokenUrl(TOKEN_URL)
            .clientAuthMethod(OAuth2AuthConfig.CLIENT_AUTH_METHOD_CLIENT_SECRET_BASIC)
            .clientId("id")
            .clientSecret("${credential." + SECRET_KEY + "}")
            .build();
    }

    /**
     * {@link #oauth2()} with every optional member set; the update and supplied-connector tests store it.
     */
    public static OAuth2AuthConfig fullOauth2() {
        return oauth2()
            .toBuilder()
            .scope("s1")
            .audience("a1")
            .tokenRequestParams(pairs("resource", "r", "x", "y"))
            .tokenResponse(ttl(600))
            .build();
    }

    /** The smallest configuration of type {@code oauth2} that builds, as a builder for variants. */
    public static OAuth2AuthConfig.OAuth2AuthConfigBuilder oauth2Builder() {
        return OAuth2AuthConfig
            .builder()
            .type(ConnectorAuthType.OAUTH2)
            .tokenUrl(TOKEN_URL)
            .clientAuthMethod(OAuth2AuthConfig.CLIENT_AUTH_METHOD_CLIENT_SECRET_BASIC);
    }

    /**
     * {@link #oauth2()} with credential {@code key} as its only reference. With a key the connector credential lacks, it
     * is refused at every write site, and in the update tests it stands for a stored configuration that fails validation.
     */
    public static OAuth2AuthConfig oauth2Referencing(String key) {
        return oauth2().toBuilder().clientSecret("${credential." + key + "}").build();
    }

    public static TokenResponseSpec ttl(int seconds) {
        return TokenResponseSpec.builder().defaultTtlSeconds(seconds).build();
    }

    /** An insertion-ordered map of the given keys and values. */
    public static Map<String, String> pairs(String... keysAndValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    public static JsonNode tree(String json) {
        return StringUtils.MAPPER.readTree(json);
    }

    /** The document {@code object} writes, as the index receives it. */
    public static JsonNode documentOf(ToXContentObject object) throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        object.toXContent(builder, ToXContent.EMPTY_PARAMS);
        return tree(builder.toString());
    }

    /** A cluster of data nodes, one per version. */
    public static ClusterState clusterState(Version... versions) {
        DiscoveryNodes.Builder nodes = DiscoveryNodes.builder();
        for (int i = 0; i < versions.length; i++) {
            nodes
                .add(
                    new DiscoveryNode(
                        "n" + i,
                        "n" + i + "Id",
                        OpenSearchTestCase.buildNewFakeTransportAddress(),
                        Collections.emptyMap(),
                        Collections.singleton(DiscoveryNodeRole.DATA_ROLE),
                        versions[i]
                    )
                );
        }
        return ClusterState.builder(new ClusterName("test")).nodes(nodes.build()).build();
    }

    /** A cluster with one node on {@link #OLD_VERSION}, which the version guard rejects. */
    public static ClusterState oldNodeCluster() {
        return clusterState(Version.CURRENT, OLD_VERSION);
    }

    /** One predict action per URL. */
    public static List<ConnectorAction> actions(String... urls) {
        List<ConnectorAction> actions = new ArrayList<>();
        for (String url : urls) {
            actions
                .add(
                    ConnectorAction
                        .builder()
                        .actionType(ConnectorAction.ActionType.PREDICT)
                        .method("POST")
                        .url(url)
                        .requestBody("{}")
                        .build()
                );
        }
        return actions;
    }

    /** Connector parameters holding {@code model}. */
    public static Map<String, String> parameters() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("model", "x");
        return parameters;
    }

    /** An http connector holding {@code authConfig}, which may be null, without a credential. */
    public static HttpConnector http(OAuth2AuthConfig authConfig) {
        return http(authConfig, parameters(), null);
    }

    /** An http connector with one predict action and the given configuration, parameters and credential, each possibly null. */
    public static HttpConnector http(OAuth2AuthConfig authConfig, Map<String, String> parameters, Map<String, String> credential) {
        return HttpConnector
            .builder()
            .name("c")
            .version("1")
            .protocol(ConnectorProtocols.HTTP)
            .parameters(parameters)
            .credential(credential)
            .actions(actions(ACTION_URL))
            .authConfig(authConfig)
            .build();
    }

    /**
     * An http connector holding {@link #oauth2()}, its credential decrypted, and one action of {@code type} to
     * {@link #ACTION_URL}: a connector whose requests carry an OAuth2 token.
     */
    public static HttpConnector oauth2Http(ConnectorAction.ActionType type) {
        HttpConnector connector = HttpConnector
            .builder()
            .name("c")
            .version("1")
            .protocol(ConnectorProtocols.HTTP)
            .parameters(parameters())
            .credential(Map.of(SECRET_KEY, STORED_CIPHERTEXT))
            .actions(List.of(ConnectorAction.builder().actionType(type).method("POST").url(ACTION_URL).requestBody("{}").build()))
            .authConfig(oauth2())
            .build();
        connector.setDecryptedCredential(Map.of(SECRET_KEY, "secret"));
        return connector;
    }

    /** An MCP connector of {@code protocol} without parameters, as MCP connectors are often stored; it never holds {@code auth_config}. */
    public static Connector mcp(String protocol) {
        if (ConnectorProtocols.MCP_SSE.equals(protocol)) {
            return McpConnector.builder().name("m").protocol(protocol).url("https://api.openai.com/mcp").build();
        }
        return McpStreamableHttpConnector.builder().name("m").protocol(protocol).url("https://api.openai.com/mcp").build();
    }

    /**
     * Runs {@code action} while the log of {@code logger} is watched: every {@code seen} line must be logged at
     * {@code level}, and no {@code unseen} line may be.
     */
    public static void expectLog(Class<?> logger, Level level, Runnable action, String[] seen, String[] unseen)
        throws IllegalAccessException {
        String name = logger.getName();
        try (MockLogAppender appender = MockLogAppender.createForLoggers(LogManager.getLogger(logger))) {
            for (String line : seen) {
                appender.addExpectation(new MockLogAppender.SeenEventExpectation(line, name, level, line));
            }
            for (String line : unseen) {
                appender.addExpectation(new MockLogAppender.UnseenEventExpectation(line, name, level, line));
            }
            action.run();
            appender.assertAllExpectationsMatched();
        }
    }
}
