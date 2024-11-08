/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.ConnectorProtocols;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.engine.MLEngineClassLoader;
import org.opensearch.ml.engine.MLStaticMockBase;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;

import com.google.common.collect.ImmutableMap;

public class RemoteModelTest extends MLStaticMockBase {

    @Mock
    MLInput mlInput;

    @Mock
    MLModel mlModel;

    @Mock
    RemoteConnectorExecutor remoteConnectorExecutor;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    RemoteModel remoteModel;
    Encryptor encryptor;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        remoteModel = new RemoteModel();
        encryptor = spy(new EncryptorImpl("m+dWmfmnNRiNlOdej/QelEkvMTyH//frS2TBeS2BP4w="));
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
        Connector connector = createConnector(ImmutableMap.of("Authorization", "Bearer ${credential.key}"));
        when(mlModel.getConnector()).thenReturn(connector);
        remoteModel.initModel(mlModel, ImmutableMap.of(), encryptor);
        ActionListener<MLTaskResponse> actionListener = mock(ActionListener.class);
        remoteModel.asyncPredict(mlInput, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assert argumentCaptor.getValue() instanceof RuntimeException;
        assertEquals("pre_process_function not defined in connector", argumentCaptor.getValue().getMessage());
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
        doThrow(actualException)
            .when(remoteConnectorExecutor)
            .executeAction(ConnectorAction.ActionType.PREDICT.toString(), mlInput, actionListener);
        try (MockedStatic<MLEngineClassLoader> loader = mockStatic(MLEngineClassLoader.class)) {
            Connector connector = createConnector(ImmutableMap.of("Authorization", "Bearer ${credential.key}"));
            when(mlModel.getConnector()).thenReturn(connector);
            loader
                .when(() -> MLEngineClassLoader.initInstance(connector.getProtocol(), connector, Connector.class))
                .thenReturn(remoteConnectorExecutor);
            remoteModel.initModel(mlModel, ImmutableMap.of(), encryptor);
            remoteModel.asyncPredict(mlInput, actionListener);
            ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
            verify(actionListener).onFailure(argumentCaptor.capture());
            assert expExceptionClass.isInstance(argumentCaptor.getValue());
            assertEquals(expExceptionMessage, argumentCaptor.getValue().getMessage());
        }
    }

    @Test
    public void initModel_Failure_With_RuntimeException() {
        initModel_Failure_With_Throwable(new IllegalArgumentException("Tag mismatch!"), IllegalArgumentException.class, "Tag mismatch!");
    }

    @Test
    public void initModel_Failure_With_Throwable() {
        initModel_Failure_With_Throwable(new Error("Decryption Error!"), MLException.class, "Decryption Error!");
    }

    private void initModel_Failure_With_Throwable(
        Throwable actualException,
        Class<? extends Throwable> expExcepClass,
        String expExceptionMessage
    ) {
        exceptionRule.expect(expExcepClass);
        exceptionRule.expectMessage(expExceptionMessage);
        Connector connector = createConnector(null);
        when(mlModel.getConnector()).thenReturn(connector);
        doThrow(actualException).when(encryptor).decrypt(any());
        remoteModel.initModel(mlModel, ImmutableMap.of(), encryptor);
    }

    @Test
    public void initModel_NullHeader() {
        Connector connector = createConnector(null);
        when(mlModel.getConnector()).thenReturn(connector);
        remoteModel.initModel(mlModel, ImmutableMap.of(), encryptor);
        Map<String, String> decryptedHeaders = connector.getDecryptedHeaders();
        Assert.assertNull(decryptedHeaders);
    }

    @Test
    public void initModel_WithHeader() {
        Connector connector = createConnector(ImmutableMap.of("Authorization", "Bearer ${credential.key}"));
        when(mlModel.getConnector()).thenReturn(connector);
        remoteModel.initModel(mlModel, ImmutableMap.of(), encryptor);
        Map<String, String> decryptedHeaders = connector.getDecryptedHeaders();
        RemoteConnectorExecutor executor = remoteModel.getConnectorExecutor();
        Assert.assertNotNull(executor);
        Assert.assertNull(decryptedHeaders);
        Assert.assertNotNull(executor.getConnector().getDecryptedHeaders());
        assertEquals(1, executor.getConnector().getDecryptedHeaders().size());
        assertEquals("Bearer test_api_key", executor.getConnector().getDecryptedHeaders().get("Authorization"));
        remoteModel.close();
        Assert.assertNull(remoteModel.getConnectorExecutor());
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
            .credential(ImmutableMap.of("key", encryptor.encrypt("test_api_key")))
            .actions(Arrays.asList(predictAction))
            .build();
        return connector;
    }

}
