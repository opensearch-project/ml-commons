/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.engine.algorithms.remote.RemoteModel.SDK_CLIENT;
import static org.opensearch.ml.engine.algorithms.remote.RemoteModel.SETTINGS;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.ConnectorProtocols;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.engine.MLEngineClassLoader;
import org.opensearch.ml.engine.MLStaticMockBase;
import org.opensearch.ml.engine.Predictable;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.transport.TransportChannel;

import com.google.common.collect.ImmutableMap;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RemoteModelTest extends MLStaticMockBase {

    @Mock
    MLInput mlInput;

    @Mock
    MLModel mlModel;

    @Mock
    RemoteConnectorExecutor remoteConnectorExecutor;

    @Mock
    SdkClient sdkClient;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    RemoteModel remoteModel;
    Encryptor encryptor;
    Settings settings = Settings.builder().put("plugins.ml-commons.global_tenant_id", "_global_tenant_id").build();

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        remoteModel = new RemoteModel();

        encryptor = mock(Encryptor.class);
        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(1);
            listener.onResponse("test_api_key");
            return null;
        }).when(encryptor).decrypt(any(), any(), isA(ActionListener.class));
        when(sdkClient.isGlobalResource(any(), any())).thenReturn(CompletableFuture.completedFuture(true));
    }

    @Test
    public void predict_ModelNotDeployed() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Model not ready yet");
        remoteModel.predict(mlInput, mlModel);
    }

    @Test
    public void test_predict_throw_IllegalStateException() {
        exceptionRule.expect(IllegalStateException.class);
        exceptionRule.expectMessage("Method is not implemented");
        remoteModel.predict(mlInput);
    }

    @Test
    public void asyncPredict_NullConnectorExecutor() {
        ActionListener<MLTaskResponse> actionListener = mock(ActionListener.class);
        remoteModel.asyncPredict(mlInput, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assert argumentCaptor.getValue() instanceof RuntimeException;
        assertEquals(
            "Model not ready yet. Please run this first: POST /_plugins/_ml/models/<model_id>/_deploy",
            argumentCaptor.getValue().getMessage()
        );
    }

    @Test
    public void asyncPredict_ModelDeployed_WrongInput() {
        asyncPredict_ModelDeployed_WrongInput("pre_process_function not defined in connector");
    }

    @Test
    public void asyncPredict_With_RemoteInferenceInputDataSet() {
        when(mlInput.getInputDataset()).thenReturn(
                new RemoteInferenceInputDataSet(Collections.emptyMap(), ConnectorAction.ActionType.BATCH_PREDICT));
        asyncPredict_ModelDeployed_WrongInput("no BATCH_PREDICT action found");
    }

    @Test
    public void test_initModel_throwIllegalStateException() {
        exceptionRule.expect(IllegalStateException.class);
        exceptionRule.expectMessage("Method is not implemented");
        remoteModel.initModel(mlModel, new HashMap<>(), encryptor);
    }

    private void asyncPredict_ModelDeployed_WrongInput(String expExceptionMessage) {
        Connector connector = createConnector(ImmutableMap.of("Authorization", "Bearer ${credential.key}"));
        when(mlModel.getConnector()).thenReturn(connector);
        ActionListener<Predictable> listener = createPredictableListener(() -> {
            ActionListener<MLTaskResponse> actionListener = mock(ActionListener.class);
            remoteModel.asyncPredict(mlInput, actionListener);
            ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
            verify(actionListener).onFailure(argumentCaptor.capture());
            assert argumentCaptor.getValue() instanceof RuntimeException;
            assertEquals(expExceptionMessage, argumentCaptor.getValue().getMessage());
        });
        remoteModel.initModelAsync(mlModel, ImmutableMap.of(SDK_CLIENT, sdkClient, SETTINGS, settings), encryptor, listener);
    }

    @Test
    public void asyncPredict_Failure_With_RuntimeException() {
        asyncPredict_Failure_With_Throwable(
            new RuntimeException("Remote Connection Exception!"),
            RuntimeException.class,
            "Remote Connection Exception!"
        );
    }

    @Test
    public void asyncPredict_Failure_With_Throwable() {
        asyncPredict_Failure_With_Throwable(
            new Error("Remote Connection Error!"),
            MLException.class,
            "java.lang.Error: Remote Connection Error!"
        );
    }

    private void asyncPredict_Failure_With_Throwable(
        Throwable actualException,
        Class<? extends Throwable> expExceptionClass,
        String expExceptionMessage
    ) {
        ActionListener<MLTaskResponse> actionListener = mock(ActionListener.class);
        TransportChannel channel = mock(TransportChannel.class);
        doThrow(actualException)
            .when(remoteConnectorExecutor)
            .executeAction(ConnectorAction.ActionType.PREDICT.toString(), mlInput, actionListener, channel);
        try (MockedStatic<MLEngineClassLoader> loader = mockStatic(MLEngineClassLoader.class)) {
            Connector connector = createConnector(ImmutableMap.of("Authorization", "Bearer ${credential.key}"));
            when(mlModel.getConnector()).thenReturn(connector);
            loader
                .when(() -> MLEngineClassLoader.initInstance(connector.getProtocol(), connector, Connector.class))
                .thenReturn(remoteConnectorExecutor);
            ActionListener<Predictable> listener = createPredictableListener(() -> {
                remoteModel.asyncPredict(mlInput, actionListener, channel);
                ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
                verify(actionListener).onFailure(argumentCaptor.capture());
                assert expExceptionClass.isInstance(argumentCaptor.getValue());
                assertEquals(expExceptionMessage, argumentCaptor.getValue().getMessage());
            });
            remoteModel.initModelAsync(mlModel, ImmutableMap.of(SDK_CLIENT, sdkClient, SETTINGS, settings), encryptor, listener);
        }
    }

    @Test
    public void initModelAsync_Failure_With_RuntimeException() {
        initModelAsync_Failure_With_Throwable(new IllegalArgumentException("Tag mismatch!"), MLException.class, "Tag mismatch!");
    }

    @Test
    public void initModelAsync_Failure_With_Throwable() {
        initModelAsync_Failure_With_Throwable(new Error("Decryption Error!"), MLException.class, "Decryption Error!");
    }

    private void initModelAsync_Failure_With_Throwable(
        Throwable actualException,
        Class<? extends Throwable> expExcepClass,
        String expExceptionMessage
    ) {
        Connector connector = createConnector(null);
        when(mlModel.getConnector()).thenReturn(connector);
        doThrow(actualException).when(encryptor).decrypt(any(), any(), isA(ActionListener.class));
        ActionListener<Predictable> listener = mock(ActionListener.class);
        remoteModel.initModelAsync(mlModel, ImmutableMap.of(SDK_CLIENT, sdkClient, SETTINGS, settings), encryptor, listener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assert expExcepClass.isInstance(argumentCaptor.getValue());
        assertTrue(argumentCaptor.getValue().getMessage().contains(expExceptionMessage));
    }

    @Test
    public void initModelAsync_NullHeader() {
        Connector connector = createConnector(null);
        when(mlModel.getConnector()).thenReturn(connector);
        ActionListener<Predictable> listener = createPredictableListener(() -> {
            Map<String, String> decryptedHeaders = connector.getDecryptedHeaders();
            assertNull(decryptedHeaders);
        });
        remoteModel.initModelAsync(mlModel, ImmutableMap.of(SDK_CLIENT, sdkClient, SETTINGS, settings), encryptor, listener);
    }

    @Test
    public void initModelAsync_WithHeader() {
        Connector connector = createConnector(ImmutableMap.of("Authorization", "Bearer ${credential.key}"));
        when(mlModel.getConnector()).thenReturn(connector);
        ActionListener<Predictable> listener = createPredictableListener(() -> {
            Map<String, String> decryptedHeaders = connector.getDecryptedHeaders();
            RemoteConnectorExecutor executor = remoteModel.getConnectorExecutor();
            Assert.assertNotNull(executor);
            assertNull(decryptedHeaders);
            Assert.assertNotNull(executor.getConnector().getDecryptedHeaders());
            assertEquals(1, executor.getConnector().getDecryptedHeaders().size());
            assertEquals("Bearer test_api_key", executor.getConnector().getDecryptedHeaders().get("Authorization"));
            remoteModel.close();
            assertNull(remoteModel.getConnectorExecutor());
        });
        remoteModel.initModelAsync(mlModel, ImmutableMap.of(SDK_CLIENT, sdkClient, SETTINGS, settings), encryptor, listener);
    }

    @Test
    public void initModelAsync_setsTenantIdOnClonedConnector_whenMissing() {
        Connector connector = createConnector(ImmutableMap.of("Authorization", "Bearer ${credential.key}"));
        when(mlModel.getConnector()).thenReturn(connector);
        when(mlModel.getTenantId()).thenReturn("tenantId");
        ActionListener<Predictable> listener = createPredictableListener(() -> {
            RemoteConnectorExecutor executor = remoteModel.getConnectorExecutor();
            remoteModel.close();
            assertNull(connector.getTenantId());
            assertEquals("tenantId", executor.getConnector().getTenantId());
        });
        remoteModel.initModelAsync(mlModel, ImmutableMap.of(SDK_CLIENT, sdkClient, SETTINGS, settings), encryptor, listener);
    }

    @Test
    public void initModelAsync_bothTenantIdsNull() {
        Connector connector = createConnector(ImmutableMap.of("Authorization", "Bearer ${credential.key}"));
        when(mlModel.getConnector()).thenReturn(connector);
        when(mlModel.getTenantId()).thenReturn(null);
        ActionListener<Predictable> listener = createPredictableListener(() -> {
            RemoteConnectorExecutor executor = remoteModel.getConnectorExecutor();
            assertNull(connector.getTenantId());
            assertNull(executor.getConnector().getTenantId());
        });
        remoteModel.initModelAsync(mlModel, ImmutableMap.of(SDK_CLIENT, sdkClient, SETTINGS, settings), encryptor, listener);
    }

    @Test
    public void initModelAsync_connectorHasTenantId() {
        Connector connector = createConnector(ImmutableMap.of("Authorization", "Bearer ${credential.key}"));
        connector.setTenantId("connectorTenantId");
        when(mlModel.getConnector()).thenReturn(connector);
        when(mlModel.getTenantId()).thenReturn(null);
        ActionListener<Predictable> listener = createPredictableListener(() -> {
            RemoteConnectorExecutor executor = remoteModel.getConnectorExecutor();
            assertEquals("connectorTenantId", connector.getTenantId());
            assertEquals("connectorTenantId", executor.getConnector().getTenantId());
        });
        remoteModel.initModelAsync(mlModel, ImmutableMap.of(SDK_CLIENT, sdkClient, SETTINGS, settings), encryptor, listener);
    }

    @Test
    public void initModelAsync_bothHaveTenantIds() {
        Connector connector = createConnector(ImmutableMap.of("Authorization", "Bearer ${credential.key}"));
        connector.setTenantId("connectorTenantId");
        when(mlModel.getConnector()).thenReturn(connector);
        when(mlModel.getTenantId()).thenReturn("modelTenantId");
        ActionListener<Predictable> listener = createPredictableListener(() -> {
            RemoteConnectorExecutor executor = remoteModel.getConnectorExecutor();
            assertEquals("connectorTenantId", connector.getTenantId());
            assertEquals("connectorTenantId", executor.getConnector().getTenantId());
        });
        remoteModel.initModelAsync(mlModel, ImmutableMap.of(SDK_CLIENT, sdkClient, SETTINGS, settings), encryptor, listener);
    }

    private Connector createConnector(Map<String, String> headers) {
        ConnectorAction predictAction = ConnectorAction
            .builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("wrong_method")
            .url("http://test.com/mock")
            .headers(headers)
            .requestBody("{\"input\": \"${parameters.input}\"}")
            .build();
        Connector connector = HttpConnector
            .builder()
            .name("test connector")
            .protocol(ConnectorProtocols.HTTP)
            .version("1")
            .credential(ImmutableMap.of("key", "dummy-encrypted-value"))
            .actions(Arrays.asList(predictAction))
            .build();
        return connector;
    }

    private ActionListener<Predictable> createPredictableListener(Runnable runnable) {
        ActionListener<Predictable> listener = ActionListener.wrap(r -> { runnable.run(); }, e -> { fail("Failed to init remote model"); });
        return listener;
    }

}
