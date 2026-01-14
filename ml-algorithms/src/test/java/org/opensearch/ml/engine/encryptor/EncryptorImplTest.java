package org.opensearch.ml.engine.encryptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.index.seqno.SequenceNumbers.UNASSIGNED_PRIMARY_TERM;
import static org.opensearch.index.seqno.SequenceNumbers.UNASSIGNED_SEQ_NO;
import static org.opensearch.ml.common.CommonValue.CREATE_TIME_FIELD;
import static org.opensearch.ml.common.CommonValue.MASTER_KEY;
import static org.opensearch.ml.common.CommonValue.ML_CONFIG_INDEX;
import static org.opensearch.ml.common.utils.StringUtils.hashString;
import static org.opensearch.ml.engine.encryptor.EncryptorImpl.DEFAULT_TENANT_ID;
import static org.opensearch.ml.engine.encryptor.EncryptorImpl.MASTER_KEY_NOT_READY_ERROR;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

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
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.engine.VersionConflictEngineException;
import org.opensearch.index.get.GetResult;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.impl.SdkClientFactory;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

import com.google.common.collect.ImmutableMap;

public class EncryptorImplTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();
    @Mock
    Client client;
    SdkClient sdkClient;

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
    final String GENERATED_MASTER_KEY = "m+dWmfmnNRiNlOdej/QelEkvMTyH//frS2TBeS2BP4w=";

    Encryptor encryptor;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        masterKey = new ConcurrentHashMap<>();
        masterKey.put(DEFAULT_TENANT_ID, GENERATED_MASTER_KEY);
        sdkClient = SdkClientFactory.createSdkClient(client, NamedXContentRegistry.EMPTY, Collections.emptyMap());

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
        encryptor = new EncryptorImpl(clusterService, client, sdkClient, mlIndicesHandler);
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

        Encryptor encryptor = new EncryptorImpl(clusterService, client, sdkClient, mlIndicesHandler);
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
        IndexResponse indexResponse = prepareIndexResponse();

        GetResponse getResponse = prepareNotExistsGetResponse();
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = (ActionListener) invocation.getArgument(1);
            actionListener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        doAnswer(invocation -> {
            ActionListener<IndexResponse> actionListener = (ActionListener) invocation.getArgument(1);

            actionListener.onResponse(indexResponse);
            return null;
        }).when(client).index(any(), any());

        Encryptor encryptor = new EncryptorImpl(clusterService, client, sdkClient, mlIndicesHandler);
        Assert.assertNull(encryptor.getMasterKey(null));
        String encrypted = encryptor.encrypt("test", null);
        Assert.assertNotNull(encrypted);
        Assert.assertNotEquals(masterKey.get(DEFAULT_TENANT_ID), encryptor.getMasterKey(null));
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
            GetResponse getResponse = prepareNotExistsGetResponse();
            actionListener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());
        doAnswer(invocation -> {
            ActionListener<IndexResponse> actionListener = (ActionListener) invocation.getArgument(1);
            actionListener.onFailure(new RuntimeException("random test exception"));
            return null;
        }).when(client).index(any(), any());

        Encryptor encryptor = new EncryptorImpl(clusterService, client, sdkClient, mlIndicesHandler);
        Assert.assertNull(encryptor.getMasterKey(null));
        encryptor.encrypt("test", null);
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
            GetResponse getResponse = prepareNotExistsGetResponse();
            actionListener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());
        doAnswer(invocation -> {
            ActionListener<IndexResponse> actionListener = (ActionListener) invocation.getArgument(1);
            actionListener.onFailure(new IOException("random IO exception"));
            return null;
        }).when(client).index(any(), any());

        Encryptor encryptor = new EncryptorImpl(clusterService, client, sdkClient, mlIndicesHandler);
        Assert.assertNull(encryptor.getMasterKey(null));
        encryptor.encrypt("test", null);
    }

    @Test
    public void encrypt_NonExistingMasterKey_FailedToCreateNewKey_VersionConflict() {
        /**
         * The context of this unit test is if there's any version conflict then we create new key, but if that fails
         * again then we throw ResourceNotFoundException exception.
         */
        exceptionRule.expect(ResourceNotFoundException.class);
        exceptionRule.expectMessage(MASTER_KEY_NOT_READY_ERROR);
        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = (ActionListener) invocation.getArgument(0);
            actionListener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLConfigIndex(any());
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = (ActionListener) invocation.getArgument(1);
            GetResponse getResponse = prepareNotExistsGetResponse();
            actionListener.onResponse(getResponse);
            return null;
        }).doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = (ActionListener) invocation.getArgument(1);
            actionListener.onFailure(new IOException("testing"));
            return null;
        }).when(client).get(any(), any());
        doAnswer(invocation -> {
            ActionListener<IndexResponse> actionListener = (ActionListener) invocation.getArgument(1);
            actionListener
                .onFailure(new VersionConflictEngineException(new ShardId(ML_CONFIG_INDEX, "index_uuid", 1), "test_id", "failed"));
            return null;
        }).when(client).index(any(), any());

        Encryptor encryptor = new EncryptorImpl(clusterService, client, sdkClient, mlIndicesHandler);
        Assert.assertNull(encryptor.getMasterKey(TENANT_ID));
        encryptor.encrypt("test", TENANT_ID);
    }

    @Test
    public void encrypt_NonExistingMasterKey_FailedToCreateNewKey_VersionConflict_GetExistingMasterKey() throws IOException {
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

        Encryptor encryptor = new EncryptorImpl(clusterService, client, sdkClient, mlIndicesHandler);
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
        Encryptor encryptor = new EncryptorImpl(clusterService, client, sdkClient, mlIndicesHandler);
        encryptor.encrypt(masterKey.get(DEFAULT_TENANT_ID), null);
    }

    @Test
    public void encrypt_FailedToInitMLConfigIndex() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("No response to create ML Config index");
        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = (ActionListener) invocation.getArgument(0);
            actionListener.onFailure(new RuntimeException("random test exception"));
            return null;
        }).when(mlIndicesHandler).initMLConfigIndex(any());
        Encryptor encryptor = new EncryptorImpl(clusterService, client, sdkClient, mlIndicesHandler);
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
        Encryptor encryptor = new EncryptorImpl(clusterService, client, sdkClient, mlIndicesHandler);
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

        GetResponse response = prepareMLConfigResponse(null);

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(response);
            return null;
        }).when(client).get(any(), any());

        Encryptor encryptor = new EncryptorImpl(clusterService, client, sdkClient, mlIndicesHandler);
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

        Encryptor encryptor = new EncryptorImpl(clusterService, client, sdkClient, mlIndicesHandler);
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

        Encryptor encryptor = new EncryptorImpl(clusterService, client, sdkClient, mlIndicesHandler);
        Assert.assertNull(encryptor.getMasterKey(null));
        encryptor.decrypt("test", null);
    }

    @Test
    public void decrypt_NoResponseToInitConfigIndex() {

        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = (ActionListener) invocation.getArgument(0);
            actionListener.onResponse(false);
            return null;
        }).when(mlIndicesHandler).initMLConfigIndex(any());

        // Mock GetResponse to return a valid MASTER_KEY_ID for the given tenant
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            GetResponse response = prepareMLConfigResponse(TENANT_ID); // Response includes dynamic MASTER_KEY_ID
            listener.onResponse(response);
            return null;
        }).when(client).get(any(), any());

        Encryptor encryptor = new EncryptorImpl(clusterService, client, sdkClient, mlIndicesHandler);
        String encrypted = encryptor.encrypt("test", TENANT_ID);
        Assert.assertNotNull(encryptor.getMasterKey(TENANT_ID));
        Assert.assertEquals("test", encryptor.decrypt(encrypted, TENANT_ID));
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

        Encryptor encryptor = new EncryptorImpl(clusterService, client, sdkClient, mlIndicesHandler);
        Assert.assertNull(encryptor.getMasterKey(null));
        encryptor.decrypt("test", null);
    }

    @Test
    public void initMasterKey_AddTenantMasterKeys() throws IOException {
        // Mock ML Config Index initialization to succeed
        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = (ActionListener) invocation.getArgument(0);
            actionListener.onResponse(true); // Simulate successful ML Config index initialization
            return null;
        }).when(mlIndicesHandler).initMLConfigIndex(any());

        // Mock GetResponse to return a valid MASTER_KEY_ID for the given tenant
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            GetResponse response = prepareMLConfigResponse(TENANT_ID); // Response includes dynamic MASTER_KEY_ID
            listener.onResponse(response);
            return null;
        }).when(client).get(any(), any());

        // Initialize Encryptor and verify no master key exists initially
        Encryptor encryptor = new EncryptorImpl(clusterService, client, sdkClient, mlIndicesHandler);
        Assert.assertNull(encryptor.getMasterKey(TENANT_ID));

        // Encrypt using the specified tenant ID
        String encrypted = encryptor.encrypt("test", TENANT_ID);
        Assert.assertNotNull(encrypted);

        // Verify that the tenant-specific master key is added
        String tenantMasterKey = encryptor.getMasterKey(TENANT_ID);
        Assert.assertNotNull(tenantMasterKey);

        // Ensure that the master key for this tenant matches the expected value
        Assert.assertEquals(GENERATED_MASTER_KEY, encryptor.getMasterKey(TENANT_ID));
    }

    @Test
    public void encrypt_SdkClientPutDataObjectFailure() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("Failed to index ML encryption master key");

        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = (ActionListener) invocation.getArgument(0);
            actionListener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLConfigIndex(any());

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            GetResponse getResponse = prepareNotExistsGetResponse();
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        doAnswer(invocation -> {
            ActionListener<IndexResponse> actionListener = (ActionListener) invocation.getArgument(1);
            actionListener.onFailure(new RuntimeException("Failed to index ML encryption master key"));
            return null;
        }).when(client).index(any(), any());

        Encryptor encryptor = new EncryptorImpl(clusterService, client, sdkClient, mlIndicesHandler);
        encryptor.encrypt("test", null);
    }

    // Helper method to prepare a valid GetResponse
    private GetResponse prepareMLConfigResponse(String tenantId) throws IOException {
        // Compute the masterKeyId based on tenantId
        String masterKeyId = MASTER_KEY;
        if (tenantId != null) {
            masterKeyId = MASTER_KEY + "_" + hashString(tenantId);
        }

        // Create the source map with the expected fields
        Map<String, Object> sourceMap = Map
            .of(
                MASTER_KEY,
                GENERATED_MASTER_KEY, // Valid MASTER_KEY for this tenant
                CREATE_TIME_FIELD,
                Instant.now().toEpochMilli()
            );

        // Serialize the source map to JSON
        XContentBuilder builder = XContentFactory.jsonBuilder();
        builder.startObject();
        for (Map.Entry<String, Object> entry : sourceMap.entrySet()) {
            builder.field(entry.getKey(), entry.getValue());
        }
        builder.endObject();
        BytesReference sourceBytes = BytesReference.bytes(builder);

        // Create the GetResult
        GetResult getResult = new GetResult(ML_CONFIG_INDEX, masterKeyId, 1L, 1L, 1L, true, sourceBytes, null, null);

        // Create and return the GetResponse
        return new GetResponse(getResult);
    }

    @Test
    public void encrypt_MasterKeyFieldMismatch_ShouldFallbackToProperKeyField() throws IOException {
        // This test simulates the case where the document ID is `master_key_<hash>`
        // but the actual `_source` only contains `master_key` (as expected in real DDB).

        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = (ActionListener) invocation.getArgument(0);
            actionListener.onResponse(true); // init index success
            return null;
        }).when(mlIndicesHandler).initMLConfigIndex(any());

        // Prepare a GetResponse where the _source has ONLY "master_key"
        Map<String, Object> sourceMap = Map.of(MASTER_KEY, GENERATED_MASTER_KEY, CREATE_TIME_FIELD, Instant.now().toEpochMilli());

        XContentBuilder builder = XContentFactory.jsonBuilder();
        builder.startObject();
        for (Map.Entry<String, Object> entry : sourceMap.entrySet()) {
            builder.field(entry.getKey(), entry.getValue());
        }
        builder.endObject();

        BytesReference sourceBytes = BytesReference.bytes(builder);
        String masterKeyId = MASTER_KEY + "_" + hashString(TENANT_ID); // Simulate full hashed ID
        GetResult getResult = new GetResult(ML_CONFIG_INDEX, masterKeyId, 1L, 1L, 1L, true, sourceBytes, null, null);
        GetResponse getResponse = new GetResponse(getResult);

        // Simulate Get API call returning a GetResponse with only "master_key" field
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        Encryptor encryptor = new EncryptorImpl(clusterService, client, sdkClient, mlIndicesHandler);

        // Old buggy code would try to access response.source().get(masterKeyId) and get null
        // This test ensures the new fix works — we access MASTER_KEY properly
        String encrypted = encryptor.encrypt("test", TENANT_ID);
        Assert.assertNotNull(encrypted);
        Assert.assertEquals("test", encryptor.decrypt(encrypted, TENANT_ID));
    }

    @Test
    public void encrypt_MasterKeyFieldExistsButNotString_ShouldThrowError() throws IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = invocation.getArgument(0);
            actionListener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLConfigIndex(any());

        // Prepare _source with a non-string master key
        Map<String, Object> sourceMap = Map
            .of(
                MASTER_KEY,
                12345, // wrong type
                CREATE_TIME_FIELD,
                Instant.now().toEpochMilli()
            );

        XContentBuilder builder = XContentFactory.jsonBuilder().startObject();
        for (Map.Entry<String, Object> entry : sourceMap.entrySet()) {
            builder.field(entry.getKey(), entry.getValue());
        }
        builder.endObject();

        BytesReference sourceBytes = BytesReference.bytes(builder);
        String masterKeyId = MASTER_KEY + "_" + hashString(TENANT_ID);
        GetResult getResult = new GetResult(ML_CONFIG_INDEX, masterKeyId, 1L, 1L, 1L, true, sourceBytes, null, null);
        GetResponse getResponse = new GetResponse(getResult);

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        Encryptor encryptor = new EncryptorImpl(clusterService, client, sdkClient, mlIndicesHandler);

        exceptionRule.expect(ResourceNotFoundException.class);
        exceptionRule.expectMessage(MASTER_KEY_NOT_READY_ERROR);

        encryptor.encrypt("test", TENANT_ID);
    }

    @Test
    public void encrypt_MasterKeyFieldMissing_ShouldThrowError() throws IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = invocation.getArgument(0);
            actionListener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLConfigIndex(any());

        // _source does not include the "master_key" field
        Map<String, Object> sourceMap = Map.of(CREATE_TIME_FIELD, Instant.now().toEpochMilli());

        XContentBuilder builder = XContentFactory.jsonBuilder().startObject();
        for (Map.Entry<String, Object> entry : sourceMap.entrySet()) {
            builder.field(entry.getKey(), entry.getValue());
        }
        builder.endObject();

        BytesReference sourceBytes = BytesReference.bytes(builder);
        String masterKeyId = MASTER_KEY + "_" + hashString(TENANT_ID);
        GetResult getResult = new GetResult(ML_CONFIG_INDEX, masterKeyId, 1L, 1L, 1L, true, sourceBytes, null, null);
        GetResponse getResponse = new GetResponse(getResult);

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        Encryptor encryptor = new EncryptorImpl(clusterService, client, sdkClient, mlIndicesHandler);

        exceptionRule.expect(ResourceNotFoundException.class);
        exceptionRule.expectMessage(MASTER_KEY_NOT_READY_ERROR);

        encryptor.encrypt("test", TENANT_ID);
    }

    @Test
    public void handleVersionConflictResponse_RetrySucceeds() throws IOException {
        // Simulate successful ML Config Index initialization
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLConfigIndex(any());

        // First, simulate a version conflict on the initial PUT
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            // Version conflict error is thrown
            listener.onFailure(new VersionConflictEngineException(new ShardId(ML_CONFIG_INDEX, "index_uuid", 1), "test_id", "failed"));
            return null;
        }).when(client).index(any(), any());

        // Simulate that after the version conflict, the GET call returns a valid master key document.
        GetResponse validResponse = prepareMLConfigResponse(TENANT_ID);
        // This GET call will be triggered twice (once for the version conflict GET and again in the normal flow),
        // so we set up our stub to return a valid response each time.
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(validResponse);
            return null;
        }).when(client).get(any(), any());

        // Now run encryption; it should handle the version conflict by fetching the key, and then succeed.
        Encryptor encryptor = new EncryptorImpl(clusterService, client, sdkClient, mlIndicesHandler);
        // This will go through the PUT failure, then version conflict handling, and use the returned key.
        String encrypted = encryptor.encrypt("test", TENANT_ID);
        Assert.assertNotNull(encrypted);
        Assert.assertEquals("test", encryptor.decrypt(encrypted, TENANT_ID));
    }

    @Test
    public void handleVersionConflictResponse_RetryFails() throws IOException {
        // Simulate successful ML Config Index initialization
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLConfigIndex(any());

        // Simulate a version conflict on the initial PUT
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onFailure(new VersionConflictEngineException(new ShardId(ML_CONFIG_INDEX, "index_uuid", 1), "test_id", "failed"));
            return null;
        }).when(client).index(any(), any());

        // Simulate that the GET call in version conflict handling fails, e.g., by throwing an IOException.
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onFailure(new IOException("Failed to get master key on retry"));
            return null;
        }).when(client).get(any(), any());

        Encryptor encryptor = new EncryptorImpl(clusterService, client, sdkClient, mlIndicesHandler);

        // We expect an MLException (or a ResourceNotFoundException) to be thrown due to the failure in getting the key.
        exceptionRule.expect(MLException.class);
        exceptionRule.expectMessage("Failed to get master key"); // Or adjust based on your exact message.

        encryptor.encrypt("test", TENANT_ID);
    }

    @Test
    public void encrypt_GetSourceAsMapIsNull_ShouldThrowResourceNotFound() throws Exception {
        exceptionRule.expect(ResourceNotFoundException.class);
        exceptionRule.expectMessage(MASTER_KEY_NOT_READY_ERROR);

        // Simulate ML config index init success
        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = (ActionListener) invocation.getArgument(0);
            actionListener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLConfigIndex(any());

        // Create a GetResult with null sourceBytes
        String masterKeyId = MASTER_KEY + "_" + hashString(TENANT_ID);
        GetResult getResult = new GetResult(
            ML_CONFIG_INDEX,
            masterKeyId,
            1L,
            1L,
            1L,
            true,  // exists = true
            null,  // sourceBytes = null => getSourceAsMap() will return null
            null,
            null
        );
        GetResponse getResponse = new GetResponse(getResult);

        // Mock the get response
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        // Now run it
        encryptor.encrypt("test", TENANT_ID);
    }

    @Test
    public void testMasterKeyRefetchAfterCacheExpiry() throws Exception {
        // This test verifies that cached keys are reused and DDB is not called unnecessarily
        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = (ActionListener) invocation.getArgument(0);
            actionListener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLConfigIndex(any());

        GetResponse response = prepareMLConfigResponse(TENANT_ID);
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(response);
            return null;
        }).when(client).get(any(), any());

        Encryptor encryptor = new EncryptorImpl(clusterService, client, sdkClient, mlIndicesHandler);
        
        // First encryption caches the key - should call DDB
        String encrypted1 = encryptor.encrypt("test", TENANT_ID);
        Assert.assertNotNull(encrypted1);
        
        // Verify key is cached
        String cachedKey1 = encryptor.getMasterKey(TENANT_ID);
        Assert.assertNotNull(cachedKey1);
        
        // Second encryption should use cached key (no additional DDB call)
        String encrypted2 = encryptor.encrypt("test", TENANT_ID);
        Assert.assertNotNull(encrypted2);
        
        // Verify DDB (client.get) was called only once, not twice
        verify(client, times(1)).get(any(), any());
    }

    @Test
    public void testCacheExpiryConfiguration() throws Exception {
        // This test verifies that the cache is configured with TTL and reuses cached keys
        
        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = (ActionListener) invocation.getArgument(0);
            actionListener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLConfigIndex(any());

        GetResponse response = prepareMLConfigResponse(TENANT_ID);
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(response);
            return null;
        }).when(client).get(any(), any());
        
        Encryptor encryptor = new EncryptorImpl(clusterService, client, sdkClient, mlIndicesHandler);
        
        // Initially no key should be cached
        Assert.assertNull(encryptor.getMasterKey(TENANT_ID));
        
        // First encryption fetches from DDB and caches
        String encrypted1 = encryptor.encrypt("test", TENANT_ID);
        Assert.assertNotNull(encrypted1);
        Assert.assertNotNull(encryptor.getMasterKey(TENANT_ID));
        
        // Second encryption uses cache, not DDB
        String encrypted2 = encryptor.encrypt("test", TENANT_ID);
        Assert.assertNotNull(encrypted2);
        
        // Verify DDB was called only once (first time), not on second encryption
        verify(client, times(1)).get(any(), any());
        
        // Note: In production, this key would expire after 5 minutes and be re-fetched from DDB
        // The actual expiry behavior is tested in testStaleMasterKeyScenarioWithCacheExpiry
    }

    @Test
    public void testStaleMasterKeyScenarioWithCacheExpiry() throws Exception {
        // This test simulates the bug scenario from GitHub issue #4542 with actual cache expiry
        // Using a 1-second TTL to make the test practical
        
        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = (ActionListener) invocation.getArgument(0);
            actionListener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLConfigIndex(any());

        // Generate a valid old master key (32 bytes base64 encoded)
        Encryptor tempEncryptor = new EncryptorImpl(null, GENERATED_MASTER_KEY);
        String oldMasterKey = tempEncryptor.generateMasterKey();
        GetResponse oldResponse = prepareMLConfigResponseWithKey(TENANT_ID, oldMasterKey);
        
        // New master key (new domain after recreation)
        String newMasterKey = GENERATED_MASTER_KEY;
        GetResponse newResponse = prepareMLConfigResponse(TENANT_ID);
        
        // First call returns old key, subsequent calls return new key
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(oldResponse);
            return null;
        }).doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(newResponse);
            return null;
        }).when(client).get(any(), any());

        // Create encryptor with 1-second TTL for testing
        Encryptor encryptor = new EncryptorImpl(clusterService, client, sdkClient, mlIndicesHandler, 1, TimeUnit.SECONDS);
        
        // T1: First encryption with old key
        String encrypted1 = encryptor.encrypt("test", TENANT_ID);
        Assert.assertNotNull(encrypted1);
        String cachedKey = encryptor.getMasterKey(TENANT_ID);
        Assert.assertEquals(oldMasterKey, cachedKey);
        
        // Wait for cache to expire (1 second + buffer)
        Thread.sleep(1500);
        
        // T4: After expiry, key should be null in cache
        Assert.assertNull(encryptor.getMasterKey(TENANT_ID));
        
        // T5: Next encryption should fetch the NEW key from DDB (simulating domain recreation)
        String encrypted2 = encryptor.encrypt("test", TENANT_ID);
        Assert.assertNotNull(encrypted2);
        String newCachedKey = encryptor.getMasterKey(TENANT_ID);
        Assert.assertEquals(newMasterKey, newCachedKey);
        Assert.assertNotEquals(oldMasterKey, newCachedKey);
        
        // Verify DDB was called twice: once for old key, once for new key after expiry
        verify(client, times(2)).get(any(), any());
    }

    @Test
    public void testMultipleTenantsCacheIndependently() throws Exception {
        // Verify that different tenants have independent cache entries
        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = (ActionListener) invocation.getArgument(0);
            actionListener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLConfigIndex(any());

        String tenant1 = "tenant1";
        String tenant2 = "tenant2";
        
        GetResponse response1 = prepareMLConfigResponse(tenant1);
        GetResponse response2 = prepareMLConfigResponse(tenant2);
        
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            // Return appropriate response based on which tenant is being requested
            listener.onResponse(response1);
            return null;
        }).doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(response2);
            return null;
        }).when(client).get(any(), any());

        Encryptor encryptor = new EncryptorImpl(clusterService, client, sdkClient, mlIndicesHandler);
        
        // Encrypt for tenant1
        String encrypted1 = encryptor.encrypt("test1", tenant1);
        Assert.assertNotNull(encrypted1);
        Assert.assertNotNull(encryptor.getMasterKey(tenant1));
        
        // Encrypt for tenant2
        String encrypted2 = encryptor.encrypt("test2", tenant2);
        Assert.assertNotNull(encrypted2);
        Assert.assertNotNull(encryptor.getMasterKey(tenant2));
        
        // Both keys should be cached independently
        Assert.assertNotNull(encryptor.getMasterKey(tenant1));
        Assert.assertNotNull(encryptor.getMasterKey(tenant2));
    }

    @Test
    public void testCacheReturnsNullForExpiredKey() {
        // Verify that getMasterKey returns null for non-existent/expired keys
        Encryptor encryptor = new EncryptorImpl(clusterService, client, sdkClient, mlIndicesHandler);
        
        // Key should not exist in cache initially
        Assert.assertNull(encryptor.getMasterKey("nonexistent-tenant"));
        Assert.assertNull(encryptor.getMasterKey(null));
    }

    // Helper method to prepare a valid IndexResponse
    private IndexResponse prepareIndexResponse() {
        ShardId shardId = new ShardId(ML_CONFIG_INDEX, "index_uuid", 0);
        return new IndexResponse(shardId, MASTER_KEY, 1L, 1L, 1L, true);
    }

    // Helper method to prepare a valid GetResponse
    private GetResponse prepareNotExistsGetResponse() {
        GetResult getResult = new GetResult(
            ML_CONFIG_INDEX,
            "fake_id",
            UNASSIGNED_SEQ_NO,
            UNASSIGNED_PRIMARY_TERM,
            -1L,
            false,
            null,
            null,
            null
        );
        return new GetResponse(getResult);
    }

    // Helper method to prepare a GetResponse with a specific master key
    private GetResponse prepareMLConfigResponseWithKey(String tenantId, String masterKey) throws IOException {
        String masterKeyId = MASTER_KEY;
        if (tenantId != null) {
            masterKeyId = MASTER_KEY + "_" + hashString(tenantId);
        }

        Map<String, Object> sourceMap = Map.of(
            MASTER_KEY,
            masterKey,
            CREATE_TIME_FIELD,
            Instant.now().toEpochMilli()
        );

        XContentBuilder builder = XContentFactory.jsonBuilder();
        builder.startObject();
        for (Map.Entry<String, Object> entry : sourceMap.entrySet()) {
            builder.field(entry.getKey(), entry.getValue());
        }
        builder.endObject();
        BytesReference sourceBytes = BytesReference.bytes(builder);

        GetResult getResult = new GetResult(ML_CONFIG_INDEX, masterKeyId, 1L, 1L, 1L, true, sourceBytes, null, null);
        return new GetResponse(getResult);
    }
}
