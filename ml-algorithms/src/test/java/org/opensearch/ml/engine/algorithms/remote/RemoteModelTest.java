/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import com.google.common.collect.ImmutableMap;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.ConnectorProtocols;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class RemoteModelTest {

    @InjectMocks
    RemoteModel remoteModel;

    @Mock
    RemoteConnectorExecutor remoteConnectorExecutor;

    @Mock
    MLInput mlInput;

    @Mock
    MLModel mlModel;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

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
    public void predict_NullConnectorExecutor() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("Model not ready yet");
        remoteModel.predict(mlInput);
    }

    @Test
    public void predict_ModelPredictSuccess() {
        Connector connector = createConnector(ImmutableMap.of("Authorization", "Bearer ${credential.key}"));
        when(mlModel.getConnector()).thenReturn(connector);
        remoteModel.initModel(mlModel, ImmutableMap.of(), encryptor);
        Assert.assertTrue(remoteModel.isModelReady());
        when(remoteConnectorExecutor.executePredict(mlInput)).thenReturn(new ModelTensorOutput(new ArrayList<>()));
    }

    @Test
    public void predict_ModelDeployed_WrongInput() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("Wrong input type");
        Connector connector = createConnector(ImmutableMap.of("Authorization", "Bearer ${credential.key}"));
        when(mlModel.getConnector()).thenReturn(connector);
        remoteModel.initModel(mlModel, ImmutableMap.of(), encryptor);
        remoteModel.predict(mlInput);
    }

    @Test
    public void initModel_RuntimeException() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Tag mismatch!");
        Connector connector = createConnector(null);
        when(mlModel.getConnector()).thenReturn(connector);
        doThrow(new IllegalArgumentException("Tag mismatch!")).when(encryptor).decrypt(any());
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
        Assert.assertEquals(1, executor.getConnector().getDecryptedHeaders().size());
        Assert.assertEquals("Bearer test_api_key", executor.getConnector().getDecryptedHeaders().get("Authorization"));

        remoteModel.close();
        Assert.assertNull(remoteModel.getConnectorExecutor());
    }

    private Connector createConnector(Map<String, String> headers) {
        ConnectorAction predictAction = ConnectorAction.builder()
                .actionType(ConnectorAction.ActionType.PREDICT)
                .method("wrong_method")
                .url("http://test.com/mock")
                .headers(headers)
                .requestBody("{\"input\": \"${parameters.input}\"}")
                .build();
        Connector connector = HttpConnector.builder()
                .name("test connector")
                .protocol(ConnectorProtocols.HTTP)
                .version("1")
                .credential(ImmutableMap.of("key", encryptor.encrypt("test_api_key")))
                .actions(Arrays.asList(predictAction))
                .build();
        return connector;
    }

}