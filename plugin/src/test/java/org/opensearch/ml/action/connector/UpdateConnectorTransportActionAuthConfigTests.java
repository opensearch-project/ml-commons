/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.connector;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.ML_CONNECTOR_INDEX;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_CONNECTOR_AUTH_DISABLED_MESSAGE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.DEFAULT_TOKEN_ALLOWLIST;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.NONE_DOCUMENT;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.NONE_OVER_FULL_OAUTH2_DOCUMENT;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.OLD_VERSION;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.SECRET_KEY;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.STORED_CIPHERTEXT;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.clusterState;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.documentOf;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.expectLog;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.fullOauth2;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.http;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.mcp;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.oauth2;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.oauth2Referencing;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.oldNodeCluster;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.parameters;
import static org.opensearch.ml.utils.AuthConfigTestFixtures.tree;
import static org.opensearch.ml.utils.TestHelper.clusterSetting;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.logging.log4j.Level;
import org.apache.lucene.search.TotalHits;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.Version;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchResponseSections;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.index.Index;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorProtocols;
import org.opensearch.ml.common.connector.auth.AuthConfigValidator;
import org.opensearch.ml.common.connector.auth.OAuth2AuthConfig;
import org.opensearch.ml.common.connector.auth.TokenEndpointAllowlist;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorInput;
import org.opensearch.ml.common.transport.connector.MLUpdateConnectorRequest;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.utils.ConnectorProtocolValidator;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.UpdateDataObjectRequest;
import org.opensearch.remote.metadata.client.impl.SdkClientFactory;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.aggregations.InternalAggregations;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import tools.jackson.databind.JsonNode;

/**
 * Connector update with {@code auth_config}: the write-time checks run before {@code update()}, encryption and the
 * write, and the index mapping is brought up to date before the write.
 */
public class UpdateConnectorTransportActionAuthConfigTests extends OpenSearchTestCase {

    private static final String CONNECTOR_ID = "test_connector_id";
    private static final List<String> TRUSTED_ENDPOINTS = List
        .of("^https://api\\.openai\\.com/.*$", "^https://runtime\\.sagemaker\\..*\\.amazonaws\\.com/.*$");
    private static final String REJECTED_LOG = "Rejected connector update for connector id " + CONNECTOR_ID;
    private static final String PERMISSION_DENIED_LOG = "Permission denied";
    private static final String KEPT_FAILING_CONFIGURATION_LOG = "Connector "
        + CONNECTOR_ID
        + " keeps a stored auth_config that fails validation; this update leaves it unchanged";

    @Mock
    private ConnectorAccessControlHelper connectorAccessControlHelper;
    @Mock
    private Task task;
    @Mock
    private Client client;
    @Mock
    private ThreadPool threadPool;
    @Mock
    private ClusterService clusterService;
    @Mock
    private TransportService transportService;
    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;
    @Mock
    private ActionFilters actionFilters;
    @Mock
    private MLUpdateConnectorRequest updateRequest;
    @Mock
    private ActionListener<UpdateResponse> actionListener;
    @Mock
    private MLModelManager mlModelManager;
    @Mock
    private MLIndicesHandler mlIndicesHandler;

    private SdkClient sdkClient;
    private MLEngine mlEngine;
    private UpdateResponse updateResponse;
    private SearchResponse searchResponse;
    private UpdateConnectorTransportAction action;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        Settings settings = Settings.builder().putList(ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX.getKey(), TRUSTED_ENDPOINTS).build();
        ClusterSettings clusterSettings = clusterSetting(
            settings,
            ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX,
            ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED
        );
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        when(clusterService.state()).thenReturn(clusterState(Version.CURRENT));
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);
        when(mlFeatureEnabledSetting.getTrustedOAuth2TokenEndpoints()).thenReturn(DEFAULT_TOKEN_ALLOWLIST);
        ThreadContext threadContext = new ThreadContext(
            Settings.builder().put(ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED.getKey(), true).build()
        );
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(updateRequest.getConnectorId()).thenReturn(CONNECTOR_ID);
        when(mlModelManager.getAllModelIds()).thenReturn(new String[] {});

        sdkClient = spy(SdkClientFactory.createSdkClient(client, NamedXContentRegistry.EMPTY, Collections.emptyMap()));
        Encryptor encryptor = new EncryptorImpl(null, "m+dWmfmnNRiNlOdej/QelEkvMTyH//frS2TBeS2BP4w=");
        mlEngine = spy(new MLEngine(Path.of("/tmp/test" + UUID.randomUUID()), encryptor));

        action = new UpdateConnectorTransportAction(
            transportService,
            actionFilters,
            client,
            sdkClient,
            connectorAccessControlHelper,
            mlModelManager,
            settings,
            clusterService,
            mlEngine,
            mlFeatureEnabledSetting,
            mlIndicesHandler
        );

        updateResponse = new UpdateResponse(
            new ShardId(new Index("indexName", "uuid"), 1),
            CONNECTOR_ID,
            1,
            1,
            1,
            DocWriteResponse.Result.UPDATED
        );
        SearchHits hits = new SearchHits(new SearchHit[] {}, new TotalHits(0, TotalHits.Relation.EQUAL_TO), Float.NaN);
        searchResponse = new SearchResponse(
            new SearchResponseSections(hits, InternalAggregations.EMPTY, null, false, false, null, 1),
            null,
            1,
            1,
            0,
            11,
            ShardSearchFailure.EMPTY_ARRAY,
            SearchResponse.Clusters.EMPTY
        );
        doReturn(true).when(connectorAccessControlHelper).validateConnectorAccess(any(Client.class), any(Connector.class));
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(searchResponse);
            return null;
        }).when(client).search(any(SearchRequest.class), isA(ActionListener.class));
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(), any());
        initAnswers(true);
    }

    private void mixedCluster() {
        when(clusterService.state()).thenReturn(oldNodeCluster());
    }

    private void stubStored(Connector stored) {
        doAnswer(invocation -> {
            ActionListener<Connector> listener = invocation.getArgument(5);
            listener.onResponse(stored);
            return null;
        }).when(connectorAccessControlHelper).getConnector(any(), any(), any(), any(), any(), any(), anyBoolean());
    }

    private void stubRequest(MLCreateConnectorInput updateContent) {
        when(updateRequest.getUpdateContent()).thenReturn(updateContent);
    }

    private static MLCreateConnectorInput descriptionOnly() {
        return MLCreateConnectorInput.builder().updateConnector(true).description("updated description").build();
    }

    /** An update as the REST layer parses it: parameters and credential are always maps. */
    private static MLCreateConnectorInput authUpdate(OAuth2AuthConfig authConfig, Map<String, String> credential) {
        return MLCreateConnectorInput
            .builder()
            .updateConnector(true)
            .parameters(new HashMap<>())
            .authConfig(authConfig)
            .credential(credential == null ? new HashMap<>() : credential)
            .build();
    }

    private static MLCreateConnectorInput protocolChange(String protocol) {
        return MLCreateConnectorInput.builder().updateConnector(true).protocol(protocol).parameters(new HashMap<>()).build();
    }

    private static Connector storedWithCredential(OAuth2AuthConfig authConfig) {
        return http(authConfig, parameters(), new HashMap<>(Map.of(SECRET_KEY, STORED_CIPHERTEXT)));
    }

    /** An update that supplies a credential and leaves everything else alone. */
    private static MLCreateConnectorInput credentialOnly(Map<String, String> credential) {
        return MLCreateConnectorInput.builder().updateConnector(true).parameters(new HashMap<>()).credential(credential).build();
    }

    private void initAnswers(boolean answer) {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(answer);
            return null;
        }).when(mlIndicesHandler).initMLConnectorIndex(isA(ActionListener.class));
    }

    private JsonNode writtenDocument() throws IOException {
        ArgumentCaptor<UpdateDataObjectRequest> captor = ArgumentCaptor.forClass(UpdateDataObjectRequest.class);
        verify(sdkClient).updateDataObjectAsync(captor.capture());
        assertEquals(ML_CONNECTOR_INDEX, captor.getValue().index());
        return documentOf(captor.getValue().dataObject());
    }

    private void assertSucceeded() {
        verify(actionListener, never()).onFailure(any());
        verify(actionListener).onResponse(updateResponse);
    }

    private void execute() {
        action.doExecute(task, updateRequest, actionListener);
    }

    /** Runs the update of {@code stored} and asserts it is rejected with {@code message} before {@code update()}, encryption and any write. */
    private void assertRejectedBeforeUpdate(Connector stored, MLCreateConnectorInput request, String message) {
        Connector spied = spy(stored);
        stubStored(spied);
        stubRequest(request);
        execute();
        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        verify(actionListener, never()).onResponse(any());
        assertEquals(message, captor.getValue().getMessage());
        verify(spied, never()).update(any());
        verify(mlEngine, never()).encrypt(any(), any(), any());
        verify(sdkClient, never()).updateDataObjectAsync(any(UpdateDataObjectRequest.class));
    }

    /** Asserts the update was rejected with {@code message} before encryption and any write. */
    private void assertRejectedBeforeEncryption(String message) {
        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        verify(actionListener, never()).onResponse(any());
        assertEquals(message, captor.getValue().getMessage());
        verify(mlEngine, never()).encrypt(any(), any(), any());
        verify(sdkClient, never()).updateDataObjectAsync(any(UpdateDataObjectRequest.class));
    }

    @Test
    public void testStoredNone_descriptionOnly_refreshesTheMappingBeforeTheWrite() throws IOException {
        stubStored(http(OAuth2AuthConfig.NONE));
        stubRequest(descriptionOnly());

        execute();

        InOrder inOrder = inOrder(mlIndicesHandler, sdkClient);
        inOrder.verify(mlIndicesHandler).initMLConnectorIndex(isA(ActionListener.class));
        inOrder.verify(sdkClient).updateDataObjectAsync(any(UpdateDataObjectRequest.class));
        verify(actionListener).onResponse(updateResponse);
        JsonNode document = writtenDocument();
        assertEquals(NONE_DOCUMENT, document.get("auth_config"));
        assertEquals(tree("{\"model\":\"x\"}"), document.get("parameters"));
    }

    @Test
    public void testInitAnswersFalse_failsAsCreateDoesAndDoesNotWrite() {
        stubStored(http(OAuth2AuthConfig.NONE));
        stubRequest(descriptionOnly());
        initAnswers(false);

        execute();

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(captor.capture());
        assertEquals("No response to create ML Connector index", captor.getValue().getMessage());
        verify(sdkClient, never()).updateDataObjectAsync(any(UpdateDataObjectRequest.class));
        verify(actionListener, never()).onResponse(any());
    }

    @Test
    public void testNoneOverAFullConfiguration_writesEveryStoredMemberAsRemoved() throws IOException {
        stubStored(http(fullOauth2()));
        // Switching to none needs no credential.
        stubRequest(authUpdate(OAuth2AuthConfig.NONE, null));

        execute();

        assertSucceeded();
        JsonNode document = writtenDocument();
        assertEquals(NONE_OVER_FULL_OAUTH2_DOCUMENT, document.get("auth_config"));
        assertEquals(tree("{\"model\":\"x\"}"), document.get("parameters"));
    }

    @Test
    public void testUpdateWithoutCredential_doesNotWriteTheStoredCiphertextBack() throws IOException {
        stubStored(storedWithCredential(OAuth2AuthConfig.NONE));
        stubRequest(authUpdate(OAuth2AuthConfig.NONE, null));

        execute();

        assertSucceeded();
        assertNull(writtenDocument().get("credential"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCredentialUpdate_encryptsEachSuppliedValueOnce() throws IOException {
        stubStored(storedWithCredential(fullOauth2()));
        stubRequest(authUpdate(OAuth2AuthConfig.NONE, Map.of(SECRET_KEY, "p1", "other", "p2")));

        execute();

        assertSucceeded();
        ArgumentCaptor<List<String>> encrypted = ArgumentCaptor.forClass(List.class);
        verify(mlEngine, times(1)).encrypt(encrypted.capture(), any(), any());
        List<String> values = encrypted.getValue();
        assertEquals(2, values.size());
        assertEquals(1, Collections.frequency(values, "p1"));
        assertEquals(1, Collections.frequency(values, "p2"));
        assertFalse(values.contains(STORED_CIPHERTEXT));
        JsonNode credential = writtenDocument().get("credential");
        assertNotNull(credential);
        assertNotEquals("p1", credential.get(SECRET_KEY).stringValue());
    }

    @Test
    public void testRejectedUpdate_isLoggedAsARejectedUpdateNotAsAPermissionDenial() throws Exception {
        expectLog(
            UpdateConnectorTransportAction.class,
            Level.ERROR,
            () -> assertRejectedBeforeUpdate(
                http(OAuth2AuthConfig.NONE),
                protocolChange(ConnectorProtocols.AWS_SIGV4),
                ConnectorProtocolValidator.protocolNotSupportedMessage(ConnectorProtocols.AWS_SIGV4)
            ),
            new String[] { REJECTED_LOG },
            new String[] { PERMISSION_DENIED_LOG }
        );
    }

    @Test
    public void testProtectedChangeWithoutCredential_isRejected() {
        when(mlFeatureEnabledSetting.isConnectorAuthEnabled()).thenReturn(true);
        assertRejectedBeforeUpdate(
            http(OAuth2AuthConfig.NONE),
            authUpdate(oauth2(), null),
            ConnectorProtocolValidator.credentialRequiredMessage("token_url")
        );
    }

    @Test
    public void testMixedCluster_authConfigIsRejected() {
        mixedCluster();
        assertRejectedBeforeUpdate(
            http(OAuth2AuthConfig.NONE),
            authUpdate(OAuth2AuthConfig.NONE, null),
            ConnectorProtocolValidator.versionGuardMessage(OLD_VERSION)
        );
    }

    @Test
    public void testAuthOnlyUpdateAddingOauth2_flagOff_isForbidden() {
        when(mlFeatureEnabledSetting.isConnectorAuthEnabled()).thenReturn(false);
        assertRejectedBeforeUpdate(http(OAuth2AuthConfig.NONE), authUpdate(oauth2(), null), ML_COMMONS_CONNECTOR_AUTH_DISABLED_MESSAGE);
    }

    @Test
    public void testOauth2WithCredential_overNone_isWritten() throws IOException {
        when(mlFeatureEnabledSetting.isConnectorAuthEnabled()).thenReturn(true);
        stubStored(http(OAuth2AuthConfig.NONE));
        stubRequest(authUpdate(oauth2(), Map.of(SECRET_KEY, "p")));

        execute();

        assertSucceeded();
        assertEquals(documentOf(oauth2()), writtenDocument().get("auth_config"));
    }

    @Test
    public void testTokenUrlOutsideTheAllowlist_onAnAuthUpdate_isRejected() {
        when(mlFeatureEnabledSetting.isConnectorAuthEnabled()).thenReturn(true);
        stubStored(http(OAuth2AuthConfig.NONE));
        stubRequest(authUpdate(oauth2().toBuilder().tokenUrl("https://login.example.com/t").build(), Map.of(SECRET_KEY, "p")));

        execute();

        assertRejectedBeforeEncryption(TokenEndpointAllowlist.NOT_ALLOWED_MESSAGE);
    }

    @Test
    public void testKeyView_isTheStoredKeysUnlessTheRequestSuppliesACredential() throws Exception {
        when(mlFeatureEnabledSetting.isConnectorAuthEnabled()).thenReturn(true);
        OAuth2AuthConfig storedOnly = oauth2().toBuilder().clientSecret("${credential.stored_only}").build();
        String missingKey = AuthConfigValidator.missingCredentialKeyMessage("stored_only");
        // The stored keys apply when the request has no credential; a request credential replaces them, whether the
        // request touches auth_config or not. The keys are checked on the updated connector, before encryption and the
        // write. A stored configuration that passes validation is never grandfathered, so the credential-only update
        // is rejected without the warning.
        MLCreateConnectorInput[] requests = {
            authUpdate(storedOnly, new HashMap<>()),
            authUpdate(storedOnly, Map.of("new_key", "p")),
            credentialOnly(Map.of("other", "p")) };
        String[] rejections = { null, missingKey, missingKey };
        for (int i = 0; i < requests.length; i++) {
            clearInvocations(actionListener, sdkClient, mlEngine);
            stubStored(http(storedOnly, parameters(), new HashMap<>(Map.of("stored_only", STORED_CIPHERTEXT))));
            stubRequest(requests[i]);

            expectLog(
                UpdateConnectorTransportAction.class,
                Level.WARN,
                this::execute,
                new String[0],
                new String[] { KEPT_FAILING_CONFIGURATION_LOG }
            );

            if (rejections[i] == null) {
                assertSucceeded();
            } else {
                assertRejectedBeforeEncryption(rejections[i]);
            }
        }
    }

    @Test
    public void testStoredConfigurationThatFails_unrelatedUpdate_passesWithAWarning() throws Exception {
        stubStored(storedWithCredential(oauth2Referencing("missing")));
        stubRequest(descriptionOnly());

        expectLog(
            UpdateConnectorTransportAction.class,
            Level.WARN,
            this::execute,
            new String[] { KEPT_FAILING_CONFIGURATION_LOG },
            new String[0]
        );

        assertSucceeded();
    }

    @Test
    public void testStoredOauth2OutsideTheTokenEndpointAllowlist_unrelatedUpdate_passesWithAWarning() throws Exception {
        stubStored(storedWithCredential(oauth2().toBuilder().tokenUrl("https://login.example.com/t").build()));
        stubRequest(descriptionOnly());

        expectLog(
            UpdateConnectorTransportAction.class,
            Level.WARN,
            this::execute,
            new String[] { KEPT_FAILING_CONFIGURATION_LOG },
            new String[0]
        );

        assertSucceeded();
    }

    @Test
    public void testMcpUpdateWithoutAuthConfig_succeedsWithoutReadingActions() {
        Connector stored = spy(mcp(ConnectorProtocols.MCP_SSE));
        stubStored(stored);
        stubRequest(descriptionOnly());

        execute();

        assertSucceeded();
        verify(stored, never()).getActions();
        verify(clusterService, never()).state();
    }

}
