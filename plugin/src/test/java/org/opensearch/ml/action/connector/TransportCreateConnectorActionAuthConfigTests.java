/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.connector;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.ML_CONNECTOR_INDEX;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_CONNECTOR_AUTH_DISABLED_MESSAGE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_CONNECTOR_ENABLED;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX;
import static org.opensearch.ml.task.MLPredictTaskRunnerTests.USER_STRING;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.ACTION_URL;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.DEFAULT_TOKEN_ALLOWLIST;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.NONE_DOCUMENT;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.OLD_VERSION;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.SECRET_KEY;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.actions;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.clusterState;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.documentOf;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.oauth2;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.oldNodeCluster;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.parameters;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.tree;
import static org.opensearch.ml.utils.TestHelper.clusterSetting;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.ExceptionsHelper;
import org.opensearch.Version;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.connector.ConnectorProtocols;
import org.opensearch.ml.common.connector.auth.OAuth2AuthConfig;
import org.opensearch.ml.common.connector.auth.TokenEndpointAllowlist;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorInput;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorRequest;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorResponse;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.utils.ConnectorProtocolValidator;
import org.opensearch.remote.metadata.client.PutDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.impl.SdkClientFactory;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import tools.jackson.databind.JsonNode;

/** Connector create with {@code auth_config}: the write-time checks run before anything is encrypted or written. */
public class TransportCreateConnectorActionAuthConfigTests extends OpenSearchTestCase {

    private static final String CONNECTOR_ID = "connector_id";
    private static final String ACTION = "[{\"action_type\":\"PREDICT\",\"method\":\"POST\",\"url\":\""
        + ACTION_URL
        + "\",\"request_body\":\"{}\"}]";

    @Mock
    private MLIndicesHandler mlIndicesHandler;
    @Mock
    private Client client;
    @Mock
    private MLEngine mlEngine;
    @Mock
    private ConnectorAccessControlHelper connectorAccessControlHelper;
    @Mock
    private ActionFilters actionFilters;
    @Mock
    private TransportService transportService;
    @Mock
    private MLModelManager mlModelManager;
    @Mock
    private Task task;
    @Mock
    private ActionListener<MLCreateConnectorResponse> actionListener;
    @Mock
    private ThreadPool threadPool;
    @Mock
    private ClusterService clusterService;
    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    private SdkClient sdkClient;
    private TransportCreateConnectorAction action;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        doAnswer(invocation -> {
            ActionListener<List<String>> listener = invocation.getArgument(2);
            listener.onResponse(List.of("mock"));
            return null;
        }).when(mlEngine).encrypt(any(), any(), any());
        Settings settings = Settings
            .builder()
            .putList(ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX.getKey(), List.of("^https://api\\.openai\\.com/.*$"))
            .build();
        sdkClient = spy(SdkClientFactory.createSdkClient(client, NamedXContentRegistry.EMPTY, Collections.emptyMap()));
        ClusterSettings clusterSettings = clusterSetting(
            settings,
            ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX,
            ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED,
            ML_COMMONS_MCP_CONNECTOR_ENABLED
        );
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        when(clusterService.getSettings()).thenReturn(settings);
        when(clusterService.state()).thenReturn(clusterState(Version.CURRENT));
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);
        when(mlFeatureEnabledSetting.getTrustedOAuth2TokenEndpoints()).thenReturn(DEFAULT_TOKEN_ALLOWLIST);

        action = new TransportCreateConnectorAction(
            transportService,
            actionFilters,
            mlIndicesHandler,
            client,
            sdkClient,
            mlEngine,
            connectorAccessControlHelper,
            settings,
            clusterService,
            mlModelManager,
            mlFeatureEnabledSetting
        );
        ThreadContext threadContext = new ThreadContext(
            Settings.builder().put(ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED.getKey(), true).build()
        );
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, USER_STRING);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(connectorAccessControlHelper.accessControlNotEnabled(any(User.class))).thenReturn(true);
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLConnectorIndex(isA(ActionListener.class));
        IndexResponse indexResponse = new IndexResponse(new ShardId(ML_CONNECTOR_INDEX, "_na_", 0), CONNECTOR_ID, 1, 0, 2, true);
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(indexResponse);
            return null;
        }).when(client).index(any(IndexRequest.class), isA(ActionListener.class));
    }

    private void mixedCluster() {
        when(clusterService.state()).thenReturn(oldNodeCluster());
    }

    /** A create request as the REST layer parses it. */
    private static MLCreateConnectorInput parse(String json) throws IOException {
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, json);
        parser.nextToken();
        return MLCreateConnectorInput.parse(parser);
    }

    private static String createJson(String protocol, String extraMembers) {
        return "{\"name\":\"c\",\"version\":\"1\",\"protocol\":\""
            + protocol
            + "\",\"credential\":{\"api_key\":\"secret-value\"},\"actions\":"
            + ACTION
            + extraMembers
            + "}";
    }

    private static String withAuthConfig(OAuth2AuthConfig authConfig) throws IOException {
        return createJson(ConnectorProtocols.HTTP, ",\"parameters\":{\"model\":\"x\"},\"auth_config\":" + documentOf(authConfig));
    }

    private void execute(MLCreateConnectorInput input) {
        action.doExecute(task, new MLCreateConnectorRequest(input), actionListener);
    }

    private Exception failure() {
        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        verify(actionListener, never()).onResponse(any());
        return captor.getValue();
    }

    private void assertRejected(RestStatus status, String message) {
        Exception e = failure();
        assertEquals(message, e.getMessage());
        assertEquals(status, ExceptionsHelper.status(e));
        verify(mlEngine, never()).encrypt(any(), any(), any());
        verify(sdkClient, never()).putDataObjectAsync(any(PutDataObjectRequest.class));
    }

    private void assertCreated() {
        verify(actionListener, never()).onFailure(any());
        ArgumentCaptor<MLCreateConnectorResponse> captor = ArgumentCaptor.forClass(MLCreateConnectorResponse.class);
        verify(actionListener).onResponse(captor.capture());
        assertEquals(CONNECTOR_ID, captor.getValue().getConnectorId());
    }

    private JsonNode writtenDocument() throws IOException {
        ArgumentCaptor<PutDataObjectRequest> captor = ArgumentCaptor.forClass(PutDataObjectRequest.class);
        verify(sdkClient).putDataObjectAsync(captor.capture());
        return documentOf(captor.getValue().dataObject());
    }

    public void testActiveType_isForbiddenWithTheFlagOffAndCreatedWithItOn() throws IOException {
        when(mlFeatureEnabledSetting.isConnectorAuthEnabled()).thenReturn(false);
        execute(parse(withAuthConfig(oauth2())));
        assertRejected(RestStatus.FORBIDDEN, ML_COMMONS_CONNECTOR_AUTH_DISABLED_MESSAGE);

        when(mlFeatureEnabledSetting.isConnectorAuthEnabled()).thenReturn(true);
        clearInvocations(actionListener);
        execute(parse(withAuthConfig(oauth2())));
        assertCreated();
        JsonNode authConfig = writtenDocument().get("auth_config");
        assertEquals(documentOf(oauth2()), authConfig);
        assertEquals("${credential." + SECRET_KEY + "}", authConfig.get("client_secret").stringValue());
    }

    public void testTokenUrlOutsideTheAllowlist_isRejected() throws IOException {
        when(mlFeatureEnabledSetting.isConnectorAuthEnabled()).thenReturn(true);
        execute(parse(withAuthConfig(oauth2().toBuilder().tokenUrl("https://login.example.com/t").build())));
        assertRejected(RestStatus.BAD_REQUEST, TokenEndpointAllowlist.NOT_ALLOWED_MESSAGE);
    }

    public void testDryRun_runsTheChecksAndNeedsAProtocol() throws IOException {
        String dryRun = "{\"dry_run\":true,\"protocol\":\"http\",\"auth_config\":" + documentOf(oauth2()) + "}";
        when(mlFeatureEnabledSetting.isConnectorAuthEnabled()).thenReturn(false);
        execute(parse(dryRun));
        assertRejected(RestStatus.FORBIDDEN, ML_COMMONS_CONNECTOR_AUTH_DISABLED_MESSAGE);

        clearInvocations(actionListener);
        when(mlFeatureEnabledSetting.isConnectorAuthEnabled()).thenReturn(true);
        execute(parse(dryRun));
        ArgumentCaptor<MLCreateConnectorResponse> captor = ArgumentCaptor.forClass(MLCreateConnectorResponse.class);
        verify(actionListener).onResponse(captor.capture());
        verify(actionListener, never()).onFailure(any());
        assertEquals(MLCreateConnectorInput.DRY_RUN_CONNECTOR_NAME, captor.getValue().getConnectorId());

        // Only a dry run may omit the protocol; a configuration still needs one, and is refused before any flag or state is read.
        clearInvocations(actionListener, clusterService, mlFeatureEnabledSetting);
        execute(parse("{\"dry_run\":true,\"auth_config\":{\"type\":\"none\"}}"));
        assertRejected(RestStatus.BAD_REQUEST, ConnectorProtocolValidator.protocolNotSupportedMessage(null));
        verify(clusterService, never()).state();
        verify(mlFeatureEnabledSetting, never()).isConnectorAuthEnabled();
    }

    public void testNone_isStoredAsTheAuthConfigObject() throws IOException {
        execute(parse(withAuthConfig(OAuth2AuthConfig.NONE)));

        assertCreated();
        JsonNode document = writtenDocument();
        assertEquals(NONE_DOCUMENT, document.get("auth_config"));
        assertEquals(tree("{\"model\":\"x\"}"), document.get("parameters"));
        verify(mlFeatureEnabledSetting, never()).isConnectorAuthEnabled();
    }

    public void testAuthParameter_isAnOrdinaryParameter() throws IOException {
        // Whatever it holds, a JSON object included: nothing reads it, and no auth_config is written for it.
        mixedCluster();
        execute(parse(createJson(ConnectorProtocols.HTTP, ",\"parameters\":{\"model\":\"x\",\"auth\":\"{\\\"type\\\":\\\"none\\\"}\"}")));

        assertCreated();
        JsonNode document = writtenDocument();
        assertEquals("{\"type\":\"none\"}", document.get("parameters").get("auth").stringValue());
        assertNull(document.get("auth_config"));
        verify(clusterService, never()).state();
        verify(mlFeatureEnabledSetting, never()).isConnectorAuthEnabled();
    }

    public void testConfigurationOnAwsSigV4_isRejected() {
        // Built in code: the request parse of an aws_sigv4 connector never yields a configuration.
        Map<String, String> parameters = parameters();
        parameters.put("region", "us-east-1");
        parameters.put("service_name", "sagemaker");
        MLCreateConnectorInput input = MLCreateConnectorInput
            .builder()
            .name("c")
            .version("1")
            .protocol(ConnectorProtocols.AWS_SIGV4)
            .parameters(parameters)
            .credential(Map.of("access_key", "ak", "secret_key", "sk"))
            .actions(actions(ACTION_URL))
            .authConfig(OAuth2AuthConfig.NONE)
            .build();
        execute(input);
        assertRejected(RestStatus.BAD_REQUEST, ConnectorProtocolValidator.protocolNotSupportedMessage(ConnectorProtocols.AWS_SIGV4));
    }

    public void testMixedCluster_noneIsRejected() throws IOException {
        mixedCluster();
        execute(parse(withAuthConfig(OAuth2AuthConfig.NONE)));
        assertRejected(RestStatus.BAD_REQUEST, ConnectorProtocolValidator.versionGuardMessage(OLD_VERSION));
    }
}
