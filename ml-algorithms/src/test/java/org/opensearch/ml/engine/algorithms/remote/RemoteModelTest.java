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
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.Version;
import org.opensearch.action.ActionListener;
import org.opensearch.action.get.GetResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.ConnectorProtocols;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.CREATE_TIME_FIELD;
import static org.opensearch.ml.common.CommonValue.MASTER_KEY;
import static org.opensearch.ml.common.CommonValue.ML_CONFIG_INDEX;
import static org.opensearch.ml.engine.algorithms.remote.RemoteModel.CLIENT;
import static org.opensearch.ml.engine.algorithms.remote.RemoteModel.CLUSTER_SERVICE;

public class RemoteModelTest {

    @Mock
    MLInput mlInput;

    @Mock
    Client client;

    @Mock
    ClusterService clusterService;

    @Mock
    ClusterState clusterState;

    @Mock
    MLModel mlModel;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    RemoteModel remoteModel;
    Encryptor encryptor;

    String masterKey;

    Map<String, Object> params;
    private static final AtomicInteger portGenerator = new AtomicInteger();

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        remoteModel = new RemoteModel();
        encryptor = spy(new EncryptorImpl());
        masterKey = "0000000000000001";
        encryptor.setMasterKey(masterKey);
        params = new HashMap<>();
        params.put(CLIENT, client);
        params.put(CLUSTER_SERVICE, clusterService);

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            GetResponse response = mock(GetResponse.class);
            when(response.isExists()).thenReturn(true);
            when(response.getSourceAsMap())
                    .thenReturn(ImmutableMap.of(MASTER_KEY, masterKey, CREATE_TIME_FIELD, Instant.now().toEpochMilli()));
            listener.onResponse(response);
            return null;
        }).when(client).get(any(), any());


        when(clusterService.state()).thenReturn(clusterState);

        Metadata metadata = new Metadata.Builder()
                .indices(ImmutableMap
                        .<String, IndexMetadata>builder()
                        .put(ML_CONFIG_INDEX, IndexMetadata.builder(ML_CONFIG_INDEX)
                                .settings(Settings.builder()
                                        .put("index.number_of_shards", 1)
                                        .put("index.number_of_replicas", 1)
                                        .put("index.version.created", Version.CURRENT.id))
                                .build())
                        .build()).build();
        when(clusterState.metadata()).thenReturn(metadata);
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

    @Test
    public void initModel_WithHeader_NullMasterKey_MasterKeyExistInIndex() {
        Connector connector = createConnector(ImmutableMap.of("Authorization", "Bearer ${credential.key}"));
        when(mlModel.getConnector()).thenReturn(connector);
        Encryptor encryptor = new EncryptorImpl();
        remoteModel.initModel(mlModel, params, encryptor);
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

    @Test
    public void initModel_WithHeader_NullMasterKey_MasterKeyNotExistInIndex() {
        exceptionRule.expect(ResourceNotFoundException.class);
        exceptionRule.expectMessage("ML encryption master key not initialized yet");

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            GetResponse response = mock(GetResponse.class);
            when(response.isExists()).thenReturn(false);
            listener.onResponse(response);
            return null;
        }).when(client).get(any(), any());

        Connector connector = createConnector(ImmutableMap.of("Authorization", "Bearer ${credential.key}"));
        when(mlModel.getConnector()).thenReturn(connector);
        Encryptor encryptor = new EncryptorImpl();
        remoteModel.initModel(mlModel, params, encryptor);
    }

    @Test
    public void initModel_WithHeader_GetMasterKey_Exception() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("test error");

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("test error"));
            return null;
        }).when(client).get(any(), any());

        Connector connector = createConnector(ImmutableMap.of("Authorization", "Bearer ${credential.key}"));
        when(mlModel.getConnector()).thenReturn(connector);
        Encryptor encryptor = new EncryptorImpl();
        remoteModel.initModel(mlModel, params, encryptor);
    }

    @Test
    public void initModel_WithHeader_IndexNotFound() {
        exceptionRule.expect(ResourceNotFoundException.class);
        exceptionRule.expectMessage("ML encryption master key not initialized yet");

        Metadata metadata = new Metadata.Builder().indices(ImmutableMap.of()).build();
        when(clusterState.metadata()).thenReturn(metadata);

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("test error"));
            return null;
        }).when(client).get(any(), any());

        Connector connector = createConnector(ImmutableMap.of("Authorization", "Bearer ${credential.key}"));
        when(mlModel.getConnector()).thenReturn(connector);
        Encryptor encryptor = new EncryptorImpl();
        remoteModel.initModel(mlModel, params, encryptor);
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
