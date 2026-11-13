/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.models;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_CONNECTOR_AUTH_DISABLED_MESSAGE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MODEL_ACCESS_CONTROL_ENABLED;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.DEFAULT_TOKEN_ALLOWLIST;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.NONE_DOCUMENT;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.OLD_VERSION;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.SECRET_KEY;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.STORED_CIPHERTEXT;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.clusterState;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.documentOf;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.expectLog;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.fullOauth2;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.http;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.oauth2;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.oauth2Referencing;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.oldNodeCluster;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.parameters;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.ttl;
import static org.opensearch.ml.utils.TestHelper.clusterSetting;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Level;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.ExceptionsHelper;
import org.opensearch.Version;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.index.Index;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.get.GetResult;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLModelGroup;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorProtocols;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.connector.auth.AuthConfigValidator;
import org.opensearch.ml.common.connector.auth.OAuth2AuthConfig;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorInput;
import org.opensearch.ml.common.transport.model.MLUpdateModelInput;
import org.opensearch.ml.common.transport.model.MLUpdateModelRequest;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelGroupManager;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.utils.ConnectorProtocolValidator;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.UpdateDataObjectRequest;
import org.opensearch.remote.metadata.client.impl.SdkClientFactory;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import tools.jackson.databind.JsonNode;

/**
 * Model update with a connector that holds {@code auth_config}: the write-time checks of a model's inline connector
 * where they differ from the connector API, those of a caller-supplied {@code updatedConnector}, and the model mapping
 * brought up to date before the writes.
 */
public class UpdateModelTransportActionAuthConfigTests extends OpenSearchTestCase {

    private static final String MODEL_ID = "test_model_id";
    private static final String MODEL_GROUP_ID = "test_model_group_id";
    private static final String NEW_MODEL_GROUP_ID = "updated_test_model_group_id";
    private static final String REJECTED_LOG = "Rejected inline connector update for model " + MODEL_ID;

    @Mock
    ThreadPool threadPool;
    @Mock
    Client client;
    @Mock
    Task task;
    @Mock
    TransportService transportService;
    @Mock
    ActionFilters actionFilters;
    @Mock
    ActionListener<UpdateResponse> actionListener;
    @Mock
    MLModelManager mlModelManager;
    @Mock
    MLModelGroupManager mlModelGroupManager;
    @Mock
    MLFeatureEnabledSetting mlFeatureEnabledSetting;
    @Mock
    ConnectorAccessControlHelper connectorAccessControlHelper;
    @Mock
    ClusterService clusterService;
    @Mock
    MLEngine mlEngine;
    @Mock
    MLIndicesHandler mlIndicesHandler;

    private SdkClient sdkClient;
    private UpdateResponse updateResponse;
    private UpdateModelTransportAction action;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        Settings settings = Settings
            .builder()
            .putList(
                ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX.getKey(),
                List.of("^https://api\\.openai\\.com/.*$", "^https://runtime\\.sagemaker\\..*\\.amazonaws\\.com/.*$")
            )
            .put(ML_COMMONS_MODEL_ACCESS_CONTROL_ENABLED.getKey(), true)
            .build();
        ClusterSettings clusterSettings = clusterSetting(
            settings,
            ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX,
            ML_COMMONS_MODEL_ACCESS_CONTROL_ENABLED
        );
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(new ThreadContext(settings));
        when(clusterService.state()).thenReturn(clusterState(Version.CURRENT));
        when(clusterService.getSettings()).thenReturn(settings);
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        when(mlFeatureEnabledSetting.getTrustedOAuth2TokenEndpoints()).thenReturn(DEFAULT_TOKEN_ALLOWLIST);

        sdkClient = spy(SdkClientFactory.createSdkClient(client, NamedXContentRegistry.EMPTY, Collections.emptyMap()));
        ModelAccessControlHelper modelAccessControlHelper = spy(new ModelAccessControlHelper(clusterService, settings));
        action = new UpdateModelTransportAction(
            transportService,
            actionFilters,
            client,
            sdkClient,
            connectorAccessControlHelper,
            modelAccessControlHelper,
            mlModelManager,
            mlModelGroupManager,
            settings,
            clusterService,
            mlEngine,
            mlFeatureEnabledSetting,
            mlIndicesHandler
        );

        updateResponse = new UpdateResponse(
            new ShardId(new Index("indexName", "uuid"), 1),
            MODEL_ID,
            1,
            1,
            1,
            DocWriteResponse.Result.UPDATED
        );
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(7);
            listener.onResponse(true);
            return null;
        })
            .when(modelAccessControlHelper)
            .validateModelGroupAccess(
                any(),
                any(),
                any(),
                eq(MODEL_GROUP_ID),
                any(),
                any(),
                any(SdkClient.class),
                isA(ActionListener.class)
            );
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(4);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), eq(NEW_MODEL_GROUP_ID), any(), any(), isA(ActionListener.class));
        GetResponse modelGroupResponse = modelGroupResponse();
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(2);
            listener.onResponse(modelGroupResponse);
            return null;
        }).when(mlModelGroupManager).getModelGroupResponse(eq(sdkClient), eq(NEW_MODEL_GROUP_ID), isA(ActionListener.class));
        initAnswers(true);
    }

    private static GetResponse modelGroupResponse() throws IOException {
        MLModelGroup modelGroup = MLModelGroup
            .builder()
            .modelGroupId(NEW_MODEL_GROUP_ID)
            .name("test")
            .latestVersion(1)
            .owner(new User())
            .access(AccessMode.PUBLIC.name())
            .build();
        XContentBuilder content = modelGroup.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        return new GetResponse(new GetResult("indexName", "111", 111L, 111L, 111L, true, BytesReference.bytes(content), null, null));
    }

    private void mixedCluster() {
        when(clusterService.state()).thenReturn(oldNodeCluster());
    }

    /** An inline http connector as a model stores it: its credential is the ciphertext. */
    private static HttpConnector connector(OAuth2AuthConfig authConfig) {
        return http(authConfig, parameters(), Map.of(SECRET_KEY, STORED_CIPHERTEXT));
    }

    private void stubStoredModel(Connector inline, String connectorId) {
        stubStoredModel(inline, connectorId, FunctionName.REMOTE);
    }

    private void stubStoredModel(Connector inline, String connectorId, FunctionName algorithm) {
        MLModel model = MLModel
            .builder()
            .name("test_name")
            .modelId(MODEL_ID)
            .modelGroupId(MODEL_GROUP_ID)
            .modelState(MLModelState.REGISTERED)
            .algorithm(algorithm)
            .connector(inline)
            .connectorId(connectorId)
            .build();
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(4);
            listener.onResponse(model);
            return null;
        }).when(mlModelManager).getModel(eq(MODEL_ID), any(), any(), any(), isA(ActionListener.class));
    }

    private static MLUpdateModelRequest inlineDescriptionOnly(String modelGroupId) {
        return inlineUpdate(
            modelGroupId,
            MLCreateConnectorInput.builder().updateConnector(true).description("updated description").build()
        );
    }

    private static MLUpdateModelRequest inlineUpdate(String modelGroupId, MLCreateConnectorInput connectorUpdate) {
        MLUpdateModelInput input = MLUpdateModelInput
            .builder()
            .modelId(MODEL_ID)
            .modelGroupId(modelGroupId)
            .connector(connectorUpdate)
            .build();
        return MLUpdateModelRequest.builder().updateModelInput(input).build();
    }

    private static MLUpdateModelRequest supplied(Connector updatedConnector) {
        return MLUpdateModelRequest
            .builder()
            .updateModelInput(MLUpdateModelInput.builder().modelId(MODEL_ID).updatedConnector(updatedConnector).build())
            .build();
    }

    private void initAnswers(boolean answer) {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(answer);
            return null;
        }).when(mlIndicesHandler).initModelIndexIfAbsent(isA(ActionListener.class));
    }

    private List<UpdateDataObjectRequest> writes(int expected) {
        ArgumentCaptor<UpdateDataObjectRequest> captor = ArgumentCaptor.forClass(UpdateDataObjectRequest.class);
        verify(sdkClient, times(expected)).updateDataObjectAsync(captor.capture());
        return captor.getAllValues();
    }

    private JsonNode writtenConnector() throws IOException {
        return documentOf(writes(1).get(0).dataObject()).get("connector");
    }

    private void assertSucceeded() {
        verify(actionListener, never()).onFailure(any());
        verify(actionListener).onResponse(updateResponse);
    }

    private Exception failure() {
        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        verify(actionListener, never()).onResponse(any());
        verify(sdkClient, never()).updateDataObjectAsync(any(UpdateDataObjectRequest.class));
        return captor.getValue();
    }

    /**
     * Runs an inline-connector update over {@code stored} and asserts it is rejected with {@code message} before
     * {@code update()}, encryption and any write, the model-group write included.
     */
    private void assertInlineRejected(HttpConnector stored, MLUpdateModelRequest request, String message) {
        HttpConnector spied = spy(stored);
        stubStoredModel(spied, null);
        action.doExecute(task, request, actionListener);
        assertEquals(message, failure().getMessage());
        verify(spied, never()).update(any());
        verify(mlEngine, never()).encrypt(any(), any(), any());
    }

    @Test
    public void testInlineNone_descriptionOnly_refreshesTheMappingBeforeTheModelWrite() throws IOException {
        stubStoredModel(connector(OAuth2AuthConfig.NONE), null);

        action.doExecute(task, inlineDescriptionOnly(null), actionListener);

        InOrder inOrder = inOrder(mlIndicesHandler, sdkClient);
        inOrder.verify(mlIndicesHandler).initModelIndexIfAbsent(isA(ActionListener.class));
        inOrder.verify(sdkClient).updateDataObjectAsync(any(UpdateDataObjectRequest.class));
        verify(actionListener).onResponse(updateResponse);
        UpdateDataObjectRequest write = writes(1).get(0);
        assertEquals(ML_MODEL_INDEX, write.index());
        JsonNode written = documentOf(write.dataObject()).get("connector");
        assertEquals(NONE_DOCUMENT, written.get("auth_config"));
        assertEquals("the stored ciphertext is written back", STORED_CIPHERTEXT, written.get("credential").get(SECRET_KEY).stringValue());
    }

    @Test
    public void testInlineNone_withANewModelGroup_refreshesBeforeTheModelGroupWriteAndTheModelWrite() {
        stubStoredModel(connector(OAuth2AuthConfig.NONE), null);

        action.doExecute(task, inlineDescriptionOnly(NEW_MODEL_GROUP_ID), actionListener);

        InOrder inOrder = inOrder(mlIndicesHandler, sdkClient);
        inOrder.verify(mlIndicesHandler).initModelIndexIfAbsent(isA(ActionListener.class));
        inOrder.verify(sdkClient, times(2)).updateDataObjectAsync(any(UpdateDataObjectRequest.class));
        List<UpdateDataObjectRequest> writes = writes(2);
        assertEquals("the model-group write comes first", ML_MODEL_GROUP_INDEX, writes.get(0).index());
        assertEquals(ML_MODEL_INDEX, writes.get(1).index());
        verify(actionListener).onResponse(updateResponse);
    }

    @Test
    public void testInitAnswersFalse_failsAsRegisterDoesAndWritesNothing_inBothOverloads() {
        for (String modelGroupId : new String[] { null, NEW_MODEL_GROUP_ID }) {
            clearInvocations(actionListener, sdkClient);
            stubStoredModel(connector(OAuth2AuthConfig.NONE), null);
            initAnswers(false);

            action.doExecute(task, inlineDescriptionOnly(modelGroupId), actionListener);

            assertEquals("No response to create ML Model index", failure().getMessage());
        }
    }

    @Test
    public void testInlineRejection_isLoggedAsARejectedInlineConnectorUpdate() throws Exception {
        MLUpdateModelRequest request = inlineUpdate(
            null,
            MLCreateConnectorInput.builder().updateConnector(true).protocol(ConnectorProtocols.AWS_SIGV4).build()
        );

        expectLog(
            UpdateModelTransportAction.class,
            Level.ERROR,
            () -> assertInlineRejected(
                connector(OAuth2AuthConfig.NONE),
                request,
                ConnectorProtocolValidator.protocolNotSupportedMessage(ConnectorProtocols.AWS_SIGV4)
            ),
            new String[] { REJECTED_LOG },
            new String[0]
        );
    }

    @Test
    public void testInlineMixedCluster_withANewModelGroup_writesNoModelGroupEither() {
        mixedCluster();
        MLUpdateModelRequest request = inlineUpdate(
            NEW_MODEL_GROUP_ID,
            MLCreateConnectorInput.builder().updateConnector(true).parameters(new HashMap<>()).authConfig(OAuth2AuthConfig.NONE).build()
        );
        assertInlineRejected(connector(OAuth2AuthConfig.NONE), request, ConnectorProtocolValidator.versionGuardMessage(OLD_VERSION));
        verify(mlModelGroupManager, never()).getModelGroupResponse(any(), any(), any());
    }

    private Exception assertSuppliedRejectedBeforeTheModelIsRead(Connector updatedConnector, RestStatus status, String message) {
        clearInvocations(actionListener, sdkClient, mlModelManager);
        action.doExecute(task, supplied(updatedConnector), actionListener);
        Exception e = failure();
        assertEquals(message, e.getMessage());
        assertEquals(status, ExceptionsHelper.status(e));
        verify(mlModelManager, never()).getModel(any(), any(), any(), any(), isA(ActionListener.class));
        return e;
    }

    @Test
    public void testSuppliedOauth2_isForbiddenWithTheFlagOffAndWrittenWithItOn() throws IOException {
        // Over a stored inline configuration too: the flag is read before the model is.
        stubStoredModel(connector(oauth2()), null);
        when(mlFeatureEnabledSetting.isConnectorAuthEnabled()).thenReturn(false);
        assertSuppliedRejectedBeforeTheModelIsRead(connector(oauth2()), RestStatus.FORBIDDEN, ML_COMMONS_CONNECTOR_AUTH_DISABLED_MESSAGE);

        clearInvocations(actionListener, sdkClient);
        when(mlFeatureEnabledSetting.isConnectorAuthEnabled()).thenReturn(true);
        initAnswers(true);
        action.doExecute(task, supplied(connector(oauth2())), actionListener);

        assertSucceeded();
        assertEquals(documentOf(oauth2()), writtenConnector().get("auth_config"));
    }

    @Test
    public void testSuppliedNone_inAMixedCluster_isRejected() {
        mixedCluster();
        assertSuppliedRejectedBeforeTheModelIsRead(
            connector(OAuth2AuthConfig.NONE),
            RestStatus.BAD_REQUEST,
            ConnectorProtocolValidator.versionGuardMessage(OLD_VERSION)
        );
    }

    @Test
    public void testSuppliedReferencingAMissingCredentialKey_isRejected() {
        when(mlFeatureEnabledSetting.isConnectorAuthEnabled()).thenReturn(true);
        assertSuppliedRejectedBeforeTheModelIsRead(
            connector(oauth2Referencing("missing")),
            RestStatus.BAD_REQUEST,
            AuthConfigValidator.missingCredentialKeyMessage("missing")
        );
    }

    private String suppliedRejected(Connector updatedConnector) {
        clearInvocations(actionListener, sdkClient, mlIndicesHandler);
        action.doExecute(task, supplied(updatedConnector), actionListener);
        Exception e = failure();
        assertEquals(RestStatus.BAD_REQUEST, ExceptionsHelper.status(e));
        verifyNoInteractions(mlIndicesHandler);
        return e.getMessage();
    }

    @Test
    public void testSuppliedOverAStoredConfiguration_mayNotRemoveIt() {
        stubStoredModel(connector(fullOauth2()), null);
        assertEquals(ConnectorProtocolValidator.SUPPLIED_CONNECTOR_REMOVES_FIELDS_MESSAGE, suppliedRejected(connector(null)));
    }

    @Test
    public void testSuppliedOverAStoredConfiguration_mayNotChangeAProtectedField() {
        when(mlFeatureEnabledSetting.isConnectorAuthEnabled()).thenReturn(true);
        stubStoredModel(connector(oauth2()), null);
        assertEquals(
            ConnectorProtocolValidator.suppliedConnectorChangesMessage("client_id"),
            suppliedRejected(connector(oauth2().toBuilder().clientId("other").build()))
        );
    }

    @Test
    public void testSuppliedOverAStoredConfiguration_mayNotChangeTheReferencedKeys() {
        when(mlFeatureEnabledSetting.isConnectorAuthEnabled()).thenReturn(true);
        stubStoredModel(connector(oauth2()), null);
        OAuth2AuthConfig otherKey = oauth2().toBuilder().clientSecret("${credential.other}").build();
        assertEquals(
            ConnectorProtocolValidator.SUPPLIED_CONNECTOR_CHANGES_REFERENCED_KEYS_MESSAGE,
            suppliedRejected(http(otherKey, parameters(), Map.of("other", STORED_CIPHERTEXT)))
        );
    }

    @Test
    public void testSuppliedSupersetChangingOnlyTokenResponse_isWritten() throws IOException {
        when(mlFeatureEnabledSetting.isConnectorAuthEnabled()).thenReturn(true);
        stubStoredModel(connector(oauth2().toBuilder().tokenResponse(ttl(600)).build()), null);
        initAnswers(true);
        OAuth2AuthConfig superset = oauth2().toBuilder().tokenResponse(ttl(1200)).build();

        action.doExecute(task, supplied(connector(superset)), actionListener);

        assertSucceeded();
        assertEquals(documentOf(superset), writtenConnector().get("auth_config"));
    }

    @Test
    public void testSuppliedOverAModelWithoutAStoredConfiguration_comparesWithTheNoneBaselineAndRefreshesTheMapping() throws IOException {
        // A model registered with connector_id, and one whose inline connector holds no configuration.
        when(mlFeatureEnabledSetting.isConnectorAuthEnabled()).thenReturn(true);
        for (Connector inline : new Connector[] { null, connector(null) }) {
            stubStoredModel(inline, inline == null ? "test_connector_id" : null);
            assertEquals(ConnectorProtocolValidator.suppliedConnectorChangesMessage("token_url"), suppliedRejected(connector(oauth2())));

            clearInvocations(actionListener, sdkClient, mlIndicesHandler);
            action.doExecute(task, supplied(connector(OAuth2AuthConfig.NONE)), actionListener);
            assertSucceeded();
            InOrder inOrder = inOrder(mlIndicesHandler, sdkClient);
            inOrder.verify(mlIndicesHandler).initModelIndexIfAbsent(isA(ActionListener.class));
            inOrder.verify(sdkClient).updateDataObjectAsync(any(UpdateDataObjectRequest.class));
            assertEquals(NONE_DOCUMENT, writtenConnector().get("auth_config"));
        }
    }

    @Test
    public void testSuppliedOnTheStandaloneConnectorSwitch_isChecked() {
        when(mlFeatureEnabledSetting.isConnectorAuthEnabled()).thenReturn(true);
        stubStoredModel(null, "old_connector_id");
        MLUpdateModelRequest request = MLUpdateModelRequest
            .builder()
            .updateModelInput(
                MLUpdateModelInput
                    .builder()
                    .modelId(MODEL_ID)
                    .connectorId("new_connector_id")
                    .updatedConnector(connector(oauth2()))
                    .build()
            )
            .build();

        action.doExecute(task, request, actionListener);

        assertEquals(ConnectorProtocolValidator.suppliedConnectorChangesMessage("token_url"), failure().getMessage());
        verify(connectorAccessControlHelper, never()).validateConnectorAccess(any(), any(), any(), any(), any(), any());
    }

    @Test
    public void testSuppliedOnATextEmbeddingModel_isChecked() {
        when(mlFeatureEnabledSetting.isConnectorAuthEnabled()).thenReturn(true);
        stubStoredModel(null, null, FunctionName.TEXT_EMBEDDING);

        action.doExecute(task, supplied(connector(oauth2())), actionListener);

        assertEquals(ConnectorProtocolValidator.suppliedConnectorChangesMessage("token_url"), failure().getMessage());
    }

    @Test
    public void testSuppliedWithConnectorIdAndAnInlineConnector_isCheckedBeforeTheSwitch() {
        // A request carrying connector_id, connector and updatedConnector together is headed for the connector switch.
        stubStoredModel(connector(fullOauth2()), "old_connector_id");
        MLUpdateModelRequest request = MLUpdateModelRequest
            .builder()
            .updateModelInput(
                MLUpdateModelInput
                    .builder()
                    .modelId(MODEL_ID)
                    .connectorId("new_connector_id")
                    .connector(MLCreateConnectorInput.builder().updateConnector(true).description("d").build())
                    .updatedConnector(connector(null))
                    .build()
            )
            .build();

        action.doExecute(task, request, actionListener);

        assertEquals(ConnectorProtocolValidator.SUPPLIED_CONNECTOR_REMOVES_FIELDS_MESSAGE, failure().getMessage());
        verify(connectorAccessControlHelper, never()).validateConnectorAccess(any(), any(), any(), any(), any(), any());
    }
}
