/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.register;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_ALLOW_MODEL_URL;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_CONNECTOR_AUTH_DISABLED_MESSAGE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_TRUSTED_URL_REGEX;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_VERTEXAI_CONNECTOR_DISABLED_MESSAGE;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.DEFAULT_TOKEN_ALLOWLIST;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.OLD_VERSION;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.clusterState;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.http;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.oauth2;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.oauth2Referencing;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.oldNodeCluster;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.parameters;
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
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorProtocols;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.connector.auth.AuthConfigValidator;
import org.opensearch.ml.common.connector.auth.OAuth2AuthConfig;
import org.opensearch.ml.common.connector.auth.TokenEndpointAllowlist;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorAction;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorResponse;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.common.transport.register.MLRegisterModelRequest;
import org.opensearch.ml.common.transport.register.MLRegisterModelResponse;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelGroupManager;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.stats.MLStat;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.ml.task.MLTaskDispatcher;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.ml.utils.ConnectorProtocolValidator;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.impl.SdkClientFactory;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

/**
 * Model register with an inline connector that holds {@code auth_config}: the write-time checks on both branches, with
 * a {@code connector_id} beside the inline connector and without one, with real connectors.
 */
public class TransportRegisterModelActionAuthConfigTests extends OpenSearchTestCase {

    private static final String CONNECTOR_ID = "connector-1";
    private static final String[] BRANCHES = { null, CONNECTOR_ID };

    @Mock
    private TransportService transportService;
    @Mock
    private ModelHelper modelHelper;
    @Mock
    private MLIndicesHandler mlIndicesHandler;
    @Mock
    private MLModelManager mlModelManager;
    @Mock
    private MLModelGroupManager mlModelGroupManager;
    @Mock
    private MLTaskManager mlTaskManager;
    @Mock
    private ClusterService clusterService;
    @Mock
    private ThreadPool threadPool;
    @Mock
    private Client client;
    @Mock
    private DiscoveryNodeHelper nodeFilter;
    @Mock
    private MLTaskDispatcher mlTaskDispatcher;
    @Mock
    private MLStats mlStats;
    @Mock
    private ActionFilters actionFilters;
    @Mock
    private Task task;
    @Mock
    private ActionListener<MLRegisterModelResponse> actionListener;
    @Mock
    private DiscoveryNode localNode;
    @Mock
    private IndexResponse indexResponse;
    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;
    @Mock
    private ConnectorAccessControlHelper connectorAccessControlHelper;
    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    private SdkClient sdkClient;
    private TransportRegisterModelAction action;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        sdkClient = SdkClientFactory.createSdkClient(client, NamedXContentRegistry.EMPTY, Collections.emptyMap());
        Settings settings = Settings
            .builder()
            .put(ML_COMMONS_TRUSTED_URL_REGEX.getKey(), "^(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]")
            .put(ML_COMMONS_ALLOW_MODEL_URL.getKey(), true)
            .putList(
                ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX.getKey(),
                List.of("^https://runtime\\.sagemaker\\..*\\.amazonaws\\.com/.*$", "^https://api\\.openai\\.com/.*$")
            )
            .build();
        ClusterSettings clusterSettings = clusterSetting(
            settings,
            ML_COMMONS_TRUSTED_URL_REGEX,
            ML_COMMONS_ALLOW_MODEL_URL,
            ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX
        );
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        when(clusterService.getSettings()).thenReturn(settings);
        when(clusterService.state()).thenReturn(clusterState(Version.CURRENT));
        when(clusterService.localNode()).thenReturn(localNode);
        when(localNode.getId()).thenReturn("node-1");
        when(mlFeatureEnabledSetting.getTrustedOAuth2TokenEndpoints()).thenReturn(DEFAULT_TOKEN_ALLOWLIST);
        action = spy(
            new TransportRegisterModelAction(
                transportService,
                actionFilters,
                modelHelper,
                mlIndicesHandler,
                mlModelManager,
                mlTaskManager,
                clusterService,
                settings,
                threadPool,
                client,
                sdkClient,
                nodeFilter,
                mlTaskDispatcher,
                mlStats,
                modelAccessControlHelper,
                connectorAccessControlHelper,
                mlModelGroupManager,
                mlFeatureEnabledSetting
            )
        );
        doReturn(false).when(action).isSuperAdminUserWrapper(any(), any());
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(7);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any(), any(), any(), any(), any());
        MLStat mlStat = mock(MLStat.class);
        when(mlStats.getStat(eq(MLNodeLevelStat.ML_REQUEST_COUNT))).thenReturn(mlStat);
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(indexResponse);
            return null;
        }).when(mlTaskManager).createMLTask(any(), any());
        doAnswer(invocation -> {
            ActionListener<MLCreateConnectorResponse> listener = invocation.getArgument(2);
            listener.onResponse(mock(MLCreateConnectorResponse.class));
            return null;
        }).when(client).execute(eq(MLCreateConnectorAction.INSTANCE), any(), isA(ActionListener.class));
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(new ThreadContext(settings));
    }

    private void mixedCluster() {
        when(clusterService.state()).thenReturn(oldNodeCluster());
    }

    /** An inline http connector with a plaintext credential, as a register request carries it. */
    private static HttpConnector inline(OAuth2AuthConfig authConfig) {
        return http(authConfig, parameters(), Map.of("api_key", "plaintext"));
    }

    private static MLRegisterModelInput input(Connector inline, String connectorId) {
        return MLRegisterModelInput
            .builder()
            .functionName(FunctionName.REMOTE)
            .modelName("remote-model")
            .modelGroupId("model-group-1")
            .connector(inline)
            .connectorId(connectorId)
            .build();
    }

    private void register(Connector inline, String connectorId) {
        action.doExecute(task, new MLRegisterModelRequest(input(inline, connectorId)), actionListener);
    }

    private void assertRejected(Connector inline, String connectorId, RestStatus status, String message) {
        clearInvocations(actionListener, mlModelManager, connectorAccessControlHelper);
        register(inline, connectorId);
        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        verify(mlModelManager, never()).registerMLRemoteModel(any(), any(), any(), any());
        assertEquals(message, captor.getValue().getMessage());
        assertEquals(status, ExceptionsHelper.status(captor.getValue()));
        if (connectorId != null) {
            verify(connectorAccessControlHelper, never()).validateConnectorAccess(any(), any(), any(), any(), any(), any());
        }
    }

    public void testActiveType_isForbiddenWithTheFlagOffAndAcceptedWithItOnBothBranches() {
        when(mlFeatureEnabledSetting.isConnectorAuthEnabled()).thenReturn(false);
        for (String connectorId : BRANCHES) {
            assertRejected(inline(oauth2()), connectorId, RestStatus.FORBIDDEN, ML_COMMONS_CONNECTOR_AUTH_DISABLED_MESSAGE);
        }

        clearInvocations(actionListener, mlModelManager, connectorAccessControlHelper);
        when(mlFeatureEnabledSetting.isConnectorAuthEnabled()).thenReturn(true);
        register(inline(oauth2()), null);
        verify(actionListener, never()).onFailure(any());
        ArgumentCaptor<MLRegisterModelInput> handed = ArgumentCaptor.forClass(MLRegisterModelInput.class);
        verify(mlModelManager).registerMLRemoteModel(eq(sdkClient), handed.capture(), isA(MLTask.class), eq(actionListener));
        assertEquals(oauth2(), handed.getValue().getConnector().getAuthConfig());

        register(inline(oauth2()), CONNECTOR_ID);
        verify(connectorAccessControlHelper).validateConnectorAccess(any(), any(), eq(CONNECTOR_ID), any(), any(), any());
        verify(actionListener, never()).onFailure(any());
    }

    public void testMixedCluster_noneIsRejectedOnBothBranches() {
        mixedCluster();
        for (String connectorId : BRANCHES) {
            assertRejected(
                inline(OAuth2AuthConfig.NONE),
                connectorId,
                RestStatus.BAD_REQUEST,
                ConnectorProtocolValidator.versionGuardMessage(OLD_VERSION)
            );
        }
    }

    public void testTokenUrlOutsideTheAllowlist_isRejectedOnBothBranchesAndForAHiddenModel() {
        when(mlFeatureEnabledSetting.isConnectorAuthEnabled()).thenReturn(true);
        OAuth2AuthConfig outside = oauth2().toBuilder().tokenUrl("https://login.example.com/t").build();
        for (String connectorId : BRANCHES) {
            assertRejected(inline(outside), connectorId, RestStatus.BAD_REQUEST, TokenEndpointAllowlist.NOT_ALLOWED_MESSAGE);
        }
        doReturn(true).when(action).isSuperAdminUserWrapper(any(), any());
        assertRejected(inline(outside), null, RestStatus.BAD_REQUEST, TokenEndpointAllowlist.NOT_ALLOWED_MESSAGE);
    }

    public void testReferenceToAMissingCredentialKey_isRejectedOnBothBranches() {
        when(mlFeatureEnabledSetting.isConnectorAuthEnabled()).thenReturn(true);
        for (String connectorId : BRANCHES) {
            assertRejected(
                inline(oauth2Referencing("missing")),
                connectorId,
                RestStatus.BAD_REQUEST,
                AuthConfigValidator.missingCredentialKeyMessage("missing")
            );
        }
    }

    public void testNone_isAcceptedOnBothBranches() {
        register(inline(OAuth2AuthConfig.NONE), null);
        verify(actionListener, never()).onFailure(any());
        verify(mlModelManager).registerMLRemoteModel(eq(sdkClient), any(MLRegisterModelInput.class), isA(MLTask.class), eq(actionListener));
        register(inline(OAuth2AuthConfig.NONE), CONNECTOR_ID);
        verify(connectorAccessControlHelper).validateConnectorAccess(any(), any(), eq(CONNECTOR_ID), any(), any(), any());
    }

    public void testInlineGoogleCloudWithAConfiguration_isRejectedAfterTheVertexFlag() throws IOException {
        String json = "{\"name\":\"g\",\"protocol\":\"google_cloud\",\"parameters\":{\"model\":\"x\"},\"auth_config\":{\"type\":\"none\"},"
            + "\"credential\":{\"private_key\":\"k\",\"client_email\":\"e\"},"
            + "\"actions\":[{\"action_type\":\"PREDICT\",\"method\":\"POST\","
            + "\"url\":\"https://us-central1-aiplatform.googleapis.com/v1/projects/p/locations/l/endpoints/e:predict\",\"request_body\":\"{}\"}]}";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, json);
        parser.nextToken();
        Connector inline = Connector.createConnectorFromRequest(parser);

        when(mlFeatureEnabledSetting.isVertexAIConnectorEnabled()).thenReturn(false);
        assertRejected(inline, null, RestStatus.FORBIDDEN, ML_COMMONS_VERTEXAI_CONNECTOR_DISABLED_MESSAGE);
        when(mlFeatureEnabledSetting.isVertexAIConnectorEnabled()).thenReturn(true);
        assertRejected(
            inline,
            null,
            RestStatus.BAD_REQUEST,
            ConnectorProtocolValidator.protocolNotSupportedMessage(ConnectorProtocols.GOOGLE_CLOUD)
        );
    }
}
