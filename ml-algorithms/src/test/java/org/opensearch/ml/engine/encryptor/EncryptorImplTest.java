package org.opensearch.ml.engine.encryptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.CREATE_TIME_FIELD;
import static org.opensearch.ml.common.CommonValue.MASTER_KEY;
import static org.opensearch.ml.common.CommonValue.ML_CONFIG_INDEX;
import static org.opensearch.ml.engine.encryptor.EncryptorImpl.DEFAULT_TENANT_ID;
import static org.opensearch.ml.engine.encryptor.EncryptorImpl.MASTER_KEY_NOT_READY_ERROR;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.Version;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.engine.VersionConflictEngineException;
import org.opensearch.index.get.GetResult;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.threadpool.ThreadPool;

import com.google.common.collect.ImmutableMap;

public class EncryptorImplTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();
    @Mock
    Client client;

    @Mock
    ClusterService clusterService;

    @Mock
    ClusterState clusterState;

    @Mock
    private MLIndicesHandler mlIndicesHandler;

    Map<String, String> masterKey;

    @Mock
    ThreadPool threadPool;
    ThreadContext threadContext;
    final String USER_STRING = "myuser|role1,role2|myTenant";
    final String TENANT_ID = "myTenant";

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        masterKey = new ConcurrentHashMap<>();
        masterKey.put(DEFAULT_TENANT_ID, "m+dWmfmnNRiNlOdej/QelEkvMTyH//frS2TBeS2BP4w=");

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
            .indices(
                ImmutableMap
                    .<String, IndexMetadata>builder()
                    .put(
                        ML_CONFIG_INDEX,
                        IndexMetadata
                            .builder(ML_CONFIG_INDEX)
                            .settings(
                                Settings
                                    .builder()
                                    .put("index.number_of_shards", 1)
                                    .put("index.number_of_replicas", 1)
                                    .put("index.version.created", Version.CURRENT.id)
                            )
                            .build()
                    )
                    .build()
            )
            .build();
        when(clusterState.metadata()).thenReturn(metadata);

        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, USER_STRING);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    @Test
    public void encrypt_ExistingMasterKey() throws IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = (ActionListener) invocation.getArgument(0);
            actionListener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLConfigIndex(any());

        GetResponse response = prepareMLConfigResponse(null);
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = (ActionListener) invocation.getArgument(1);
            actionListener.onResponse(response);
            return null;
        }).when(client).get(any(), any());

        Encryptor encryptor = new EncryptorImpl(clusterService, client, mlIndicesHandler);
        Assert.assertNull(encryptor.getMasterKey(null));
        String encrypted = encryptor.encrypt("test", null);
        Assert.assertNotNull(encrypted);
        Assert.assertEquals(masterKey.get(DEFAULT_TENANT_ID), encryptor.getMasterKey(null));
    }

    @Test
    public void encrypt_NonExistingMasterKey() {
        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = (ActionListener) invocation.getArgument(0);
            actionListener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLConfigIndex(any());
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = (ActionListener) invocation.getArgument(1);
            GetResponse response = mock(GetResponse.class);
            when(response.isExists()).thenReturn(false);
            actionListener.onResponse(response);
            return null;
        }).when(client).get(any(), any());
        doAnswer(invocation -> {
            ActionListener<IndexResponse> actionListener = (ActionListener) invocation.getArgument(1);
            IndexResponse response = mock(IndexResponse.class);
            actionListener.onResponse(response);
            return null;
        }).when(client).index(any(), any());

        Encryptor encryptor = new EncryptorImpl(clusterService, client, mlIndicesHandler);
        Assert.assertNull(encryptor.getMasterKey(TENANT_ID));
        String encrypted = encryptor.encrypt("test", TENANT_ID);
        Assert.assertNotNull(encrypted);
        Assert.assertNotEquals(masterKey.get(DEFAULT_TENANT_ID), encryptor.getMasterKey(TENANT_ID));
    }

    @Test
    public void encrypt_NonExistingMasterKey_FailedToCreateNewKey() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("random test exception");
        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = (ActionListener) invocation.getArgument(0);
            actionListener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLConfigIndex(any());
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = (ActionListener) invocation.getArgument(1);
            GetResponse response = mock(GetResponse.class);
            when(response.isExists()).thenReturn(false);
            actionListener.onResponse(response);
            return null;
        }).when(client).get(any(), any());
        doAnswer(invocation -> {
            ActionListener<IndexResponse> actionListener = (ActionListener) invocation.getArgument(1);
            actionListener.onFailure(new RuntimeException("random test exception"));
            return null;
        }).when(client).index(any(), any());

        Encryptor encryptor = new EncryptorImpl(clusterService, client, mlIndicesHandler);
        Assert.assertNull(encryptor.getMasterKey(TENANT_ID));
        encryptor.encrypt("test", TENANT_ID);
    }

    @Test
    public void encrypt_NonExistingMasterKey_FailedToCreateNewKey_NonRuntimeException() {
        exceptionRule.expect(MLException.class);
        exceptionRule.expectMessage("random IO exception");
        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = (ActionListener) invocation.getArgument(0);
            actionListener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLConfigIndex(any());
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = (ActionListener) invocation.getArgument(1);
            GetResponse response = mock(GetResponse.class);
            when(response.isExists()).thenReturn(false);
            actionListener.onResponse(response);
            return null;
        }).when(client).get(any(), any());
        doAnswer(invocation -> {
            ActionListener<IndexResponse> actionListener = (ActionListener) invocation.getArgument(1);
            actionListener.onFailure(new IOException("random IO exception"));
            return null;
        }).when(client).index(any(), any());

        Encryptor encryptor = new EncryptorImpl(clusterService, client, mlIndicesHandler);
        Assert.assertNull(encryptor.getMasterKey(TENANT_ID));
        encryptor.encrypt("test", TENANT_ID);
    }

    @Test
    public void encrypt_NonExistingMasterKey_FailedToCreateNewKey_VersionConflict() {
        exceptionRule.expect(ResourceNotFoundException.class);
        exceptionRule.expectMessage(MASTER_KEY_NOT_READY_ERROR);
        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = (ActionListener) invocation.getArgument(0);
            actionListener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLConfigIndex(any());
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = (ActionListener) invocation.getArgument(1);
            GetResponse response = mock(GetResponse.class);
            when(response.isExists()).thenReturn(false);
            actionListener.onResponse(response);
            return null;
        }).doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = (ActionListener) invocation.getArgument(1);
            GetResponse response = mock(GetResponse.class);
            when(response.isExists()).thenReturn(false);
            actionListener.onResponse(response);
            return null;
        }).when(client).get(any(), any());
        doAnswer(invocation -> {
            ActionListener<IndexResponse> actionListener = (ActionListener) invocation.getArgument(1);
            actionListener
                .onFailure(new VersionConflictEngineException(new ShardId(ML_CONFIG_INDEX, "index_uuid", 1), "test_id", "failed"));
            return null;
        }).when(client).index(any(), any());

        Encryptor encryptor = new EncryptorImpl(clusterService, client, mlIndicesHandler);
        Assert.assertNull(encryptor.getMasterKey(TENANT_ID));
        encryptor.encrypt("test", TENANT_ID);
    }

    @Test
    public void encrypt_NonExistingMasterKey_FailedToCreateNewKey_VersionConflict_NullGetResponse() {
        exceptionRule.expect(ResourceNotFoundException.class);
        exceptionRule.expectMessage(MASTER_KEY_NOT_READY_ERROR);
        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = (ActionListener) invocation.getArgument(0);
            actionListener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLConfigIndex(any());
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = (ActionListener) invocation.getArgument(1);
            GetResponse response = mock(GetResponse.class);
            when(response.isExists()).thenReturn(false);
            actionListener.onResponse(response);
            return null;
        }).doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = (ActionListener) invocation.getArgument(1);
            GetResponse response = null;
            actionListener.onResponse(response);
            return null;
        }).when(client).get(any(), any());
        doAnswer(invocation -> {
            ActionListener<IndexResponse> actionListener = (ActionListener) invocation.getArgument(1);
            actionListener
                .onFailure(new VersionConflictEngineException(new ShardId(ML_CONFIG_INDEX, "index_uuid", 1), "test_id", "failed"));
            return null;
        }).when(client).index(any(), any());

        Encryptor encryptor = new EncryptorImpl(clusterService, client, mlIndicesHandler);
        Assert.assertNull(encryptor.getMasterKey(TENANT_ID));
        encryptor.encrypt("test", TENANT_ID);
    }

    @Test
    public void encrypt_NonExistingMasterKey_FailedToCreateNewKey_VersionConflict_NullResponse() {
        exceptionRule.expect(ResourceNotFoundException.class);
        exceptionRule.expectMessage(MASTER_KEY_NOT_READY_ERROR);
        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = (ActionListener) invocation.getArgument(0);
            actionListener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLConfigIndex(any());
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = (ActionListener) invocation.getArgument(1);
            GetResponse response = null;
            actionListener.onResponse(response);
            return null;
        }).doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = (ActionListener) invocation.getArgument(1);
            GetResponse response = mock(GetResponse.class);
            when(response.isExists()).thenReturn(false);
            actionListener.onResponse(response);
            return null;
        }).when(client).get(any(), any());
        doAnswer(invocation -> {
            ActionListener<IndexResponse> actionListener = (ActionListener) invocation.getArgument(1);
            actionListener
                .onFailure(new VersionConflictEngineException(new ShardId(ML_CONFIG_INDEX, "index_uuid", 1), "test_id", "failed"));
            return null;
        }).when(client).index(any(), any());

        Encryptor encryptor = new EncryptorImpl(clusterService, client, mlIndicesHandler);
        Assert.assertNull(encryptor.getMasterKey(null));
        String encrypted = encryptor.encrypt("test", null);
        Assert.assertNotNull(encrypted);
        Assert.assertEquals(masterKey.get(DEFAULT_TENANT_ID), encryptor.getMasterKey(null));
    }

    @Test
    public void encrypt_NonExistingMasterKey_FailedToCreateNewKey_VersionConflict_GetExistingMasterKey() throws IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = (ActionListener) invocation.getArgument(0);
            actionListener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLConfigIndex(any());

        GetResponse response = prepareMLConfigResponse(DEFAULT_TENANT_ID);

        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = (ActionListener) invocation.getArgument(1);
            actionListener.onResponse(response);
            return null;
        }).doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = (ActionListener) invocation.getArgument(1);
            actionListener.onResponse(response);
            return null;
        }).when(client).get(any(), any());
        doAnswer(invocation -> {
            ActionListener<IndexResponse> actionListener = (ActionListener) invocation.getArgument(1);
            actionListener
                .onFailure(new VersionConflictEngineException(new ShardId(ML_CONFIG_INDEX, "index_uuid", 1), "test_id", "failed"));
            return null;
        }).when(client).index(any(), any());

        Encryptor encryptor = new EncryptorImpl(clusterService, client, mlIndicesHandler);
        Assert.assertNull(encryptor.getMasterKey(null));
        String encrypted = encryptor.encrypt("test", null);
        Assert.assertNotNull(encrypted);
        Assert.assertEquals(masterKey.get(DEFAULT_TENANT_ID), encryptor.getMasterKey(null));
    }

    @Test
    public void encrypt_NonExistingMasterKey_FailedToCreateNewKey_VersionConflict_FailedToGetExistingMasterKey() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("random test exception");
        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = (ActionListener) invocation.getArgument(0);
            actionListener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLConfigIndex(any());
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = (ActionListener) invocation.getArgument(1);
            GetResponse response = mock(GetResponse.class);
            when(response.isExists()).thenReturn(false);
            actionListener.onResponse(response);
            return null;
        }).doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = (ActionListener) invocation.getArgument(1);
            actionListener.onFailure(new RuntimeException("random test exception"));
            return null;
        }).when(client).get(any(), any());
        doAnswer(invocation -> {
            ActionListener<IndexResponse> actionListener = (ActionListener) invocation.getArgument(1);
            actionListener
                .onFailure(new VersionConflictEngineException(new ShardId(ML_CONFIG_INDEX, "index_uuid", 1), "test_id", "failed"));
            return null;
        }).when(client).index(any(), any());

        Encryptor encryptor = new EncryptorImpl(clusterService, client, mlIndicesHandler);
        Assert.assertNull(encryptor.getMasterKey(null));
        String encrypted = encryptor.encrypt("test", null);
        Assert.assertNotNull(encrypted);
        Assert.assertEquals(masterKey.get(DEFAULT_TENANT_ID), encryptor.getMasterKey(null));
    }

    @Test
    public void encrypt_ThrowExceptionWhenInitMLConfigIndex() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("test exception");
        doThrow(new RuntimeException("test exception")).when(mlIndicesHandler).initMLConfigIndex(any());
        Encryptor encryptor = new EncryptorImpl(clusterService, client, mlIndicesHandler);
        encryptor.encrypt(masterKey.get(DEFAULT_TENANT_ID), null);
    }

    @Test
    public void encrypt_FailedToInitMLConfigIndex() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("random test exception");
        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = (ActionListener) invocation.getArgument(0);
            actionListener.onFailure(new RuntimeException("random test exception"));
            return null;
        }).when(mlIndicesHandler).initMLConfigIndex(any());
        Encryptor encryptor = new EncryptorImpl(clusterService, client, mlIndicesHandler);
        encryptor.encrypt(masterKey.get(DEFAULT_TENANT_ID), null);
    }

    @Test
    public void encrypt_FailedToGetMasterKey() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("random test exception");
        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = (ActionListener) invocation.getArgument(0);
            actionListener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLConfigIndex(any());
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = (ActionListener) invocation.getArgument(1);
            actionListener.onFailure(new RuntimeException("random test exception"));
            return null;
        }).when(client).get(any(), any());
        Encryptor encryptor = new EncryptorImpl(clusterService, client, mlIndicesHandler);
        encryptor.encrypt(masterKey.get(DEFAULT_TENANT_ID), null);
    }

    @Test
    public void encrypt_DifferentMasterKey() {
        Encryptor encryptor = new EncryptorImpl(null, masterKey.get(DEFAULT_TENANT_ID));
        String test = encryptor.getMasterKey(null);
        Assert.assertNotNull(test);
        String encrypted1 = encryptor.encrypt("test", null);

        encryptor.setMasterKey(null, encryptor.generateMasterKey());
        String encrypted2 = encryptor.encrypt("test", null);
        Assert.assertNotEquals(encrypted1, encrypted2);
    }

    @Test
    public void decrypt() throws IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = (ActionListener) invocation.getArgument(0);
            actionListener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLConfigIndex(any());

        GetResponse response = prepareMLConfigResponse(DEFAULT_TENANT_ID);

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(response);
            return null;
        }).when(client).get(any(), any());

        Encryptor encryptor = new EncryptorImpl(clusterService, client, mlIndicesHandler);
        Assert.assertNull(encryptor.getMasterKey(null));
        String encrypted = encryptor.encrypt("test", null);
        String decrypted = encryptor.decrypt(encrypted, null);
        Assert.assertEquals("test", decrypted);
        Assert.assertEquals(masterKey.get(DEFAULT_TENANT_ID), encryptor.getMasterKey(null));
    }

    @Test
    public void encrypt_NullMasterKey_NullMasterKey_MasterKeyNotExistInIndex() {
        exceptionRule.expect(MLException.class);
        exceptionRule.expectMessage("Fetching master key timed out.");

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            GetResponse response = mock(GetResponse.class);
            when(response.isExists()).thenReturn(false);
            listener.onResponse(response);
            return null;
        }).when(client).get(any(), any());

        Encryptor encryptor = new EncryptorImpl(clusterService, client, mlIndicesHandler);
        Assert.assertNull(encryptor.getMasterKey(null));
        encryptor.encrypt("test", null);
    }

    @Test
    public void decrypt_NullMasterKey_GetMasterKey_Exception() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("test error");

        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = (ActionListener) invocation.getArgument(0);
            actionListener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLConfigIndex(any());
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("test error"));
            return null;
        }).when(client).get(any(), any());

        Encryptor encryptor = new EncryptorImpl(clusterService, client, mlIndicesHandler);
        Assert.assertNull(encryptor.getMasterKey(null));
        encryptor.decrypt("test", null);
    }

    @Test
    public void decrypt_NoResponseToInitConfigIndex() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("No response to create ML Config index");

        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = (ActionListener) invocation.getArgument(0);
            actionListener.onResponse(false);
            return null;
        }).when(mlIndicesHandler).initMLConfigIndex(any());

        Encryptor encryptor = new EncryptorImpl(clusterService, client, mlIndicesHandler);
        Assert.assertNull(encryptor.getMasterKey(null));
        encryptor.decrypt("test", null);
    }

    @Test
    public void decrypt_MLConfigIndexNotFound() {
        exceptionRule.expect(MLException.class);
        exceptionRule.expectMessage("Fetching master key timed out.");

        Metadata metadata = new Metadata.Builder().indices(ImmutableMap.of()).build();
        when(clusterState.metadata()).thenReturn(metadata);

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("test error"));
            return null;
        }).when(client).get(any(), any());

        Encryptor encryptor = new EncryptorImpl(clusterService, client, mlIndicesHandler);
        Assert.assertNull(encryptor.getMasterKey(null));
        encryptor.decrypt("test", null);
    }

    // Helper method to prepare a valid GetResponse
    private GetResponse prepareMLConfigResponse(String tenantId) throws IOException {
        // Create the source map with the expected fields
        Map<String, Object> sourceMap = Map
            .of(MASTER_KEY, "m+dWmfmnNRiNlOdej/QelEkvMTyH//frS2TBeS2BP4w=", CREATE_TIME_FIELD, Instant.now().toEpochMilli());

        // Serialize the source map to JSON
        XContentBuilder builder = XContentFactory.jsonBuilder();
        builder.startObject();
        for (Map.Entry<String, Object> entry : sourceMap.entrySet()) {
            builder.field(entry.getKey(), entry.getValue());
        }
        builder.endObject();
        BytesReference sourceBytes = BytesReference.bytes(builder);

        // Create the GetResult
        GetResult getResult = new GetResult(ML_CONFIG_INDEX, MASTER_KEY, 1L, 1L, 1L, true, sourceBytes, null, null);

        // Create and return the GetResponse
        return new GetResponse(getResult);
    }

    // Helper method to prepare a valid IndexResponse
    private IndexResponse prepareIndexResponse() {
        ShardId shardId = new ShardId(ML_CONFIG_INDEX, "index_uuid", 0);
        return new IndexResponse(shardId, MASTER_KEY, 1L, 1L, 1L, true);
    }
}
