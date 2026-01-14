package org.opensearch.ml.engine.encryptor;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.Version;
import org.opensearch.action.LatchedActionListener;
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
import org.opensearch.threadpool.TestThreadPool;
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
    private static final long LATCH_WAIT_TIME = 5;
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
    public void encrypt_ExistingMasterKey() throws IOException, InterruptedException {
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
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ActionListener<String> actionListener = ActionListener.wrap(encrypted -> {
            try {
                Assert.assertNotNull(encrypted);
                Assert.assertEquals(masterKey.get(DEFAULT_TENANT_ID), encryptor.getMasterKey(null));
            } catch (Throwable t) {
                failure.set(t);
            }
        }, error -> { failure.set(error); });
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        encryptor.encrypt("test", null, latchedActionListener);
        Assert.assertTrue("Encryption failed", latch.await(LATCH_WAIT_TIME, SECONDS));
        if (failure.get() != null)
            throw new AssertionError("Encryption failed", failure.get());
    }

    @Test
    public void encrypt_NonExistingMasterKey() throws InterruptedException {
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
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ActionListener<String> actionListener = ActionListener.wrap(encrypted -> {
            try {
                Assert.assertNotNull(encrypted);
                Assert.assertNotEquals(masterKey.get(DEFAULT_TENANT_ID), encryptor.getMasterKey(null));
            } catch (Throwable t) {
                failure.set(t);
            }
        }, error -> { failure.set(error); });
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        encryptor.encrypt("test", null, latchedActionListener);
        Assert.assertTrue("Encryption failed", latch.await(LATCH_WAIT_TIME, SECONDS));
        if (failure.get() != null)
            throw new AssertionError("Encryption failed", failure.get());
    }

    @Test
    public void encrypt_NonExistingMasterKey_FailedToCreateNewKey() throws InterruptedException {
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
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ActionListener<String> actionListener = ActionListener.wrap(encrypted -> {
            failure.set(new RuntimeException("Successfully encrypted, expected exception here"));
        }, error -> {
            try {
                Assert.assertTrue(error instanceof RuntimeException);
                Assert.assertEquals("random test exception", error.getMessage());
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        encryptor.encrypt("test", null, latchedActionListener);
        Assert.assertTrue("Encryption failed", latch.await(LATCH_WAIT_TIME, SECONDS));
        if (failure.get() != null)
            throw new AssertionError("Encryption failed", failure.get());
    }

    @Test
    public void encrypt_NonExistingMasterKey_FailedToCreateNewKey_NonRuntimeException() throws InterruptedException {
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
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ActionListener<String> actionListener = ActionListener.wrap(encrypted -> {
            failure.set(new RuntimeException("Successfully encrypted, expected Exception here"));
        }, error -> {
            try {
                Assert.assertTrue(error instanceof RuntimeException);
                Assert.assertEquals("java.io.IOException: random IO exception", error.getMessage());
            } catch (Throwable t) {
                failure.set(t);
            }

        });
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        encryptor.encrypt("test", null, latchedActionListener);
        Assert.assertTrue("Encryption failed", latch.await(LATCH_WAIT_TIME, SECONDS));
        if (failure.get() != null)
            throw new AssertionError("Encryption failed", failure.get());
    }

    @Test
    public void encrypt_NonExistingMasterKey_FailedToCreateNewKey_VersionConflict() throws InterruptedException {
        /**
         * The context of this unit test is if there's any version conflict then we create new key, but if that fails
         * again then we throw ResourceNotFoundException exception.
         */
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
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ActionListener<String> actionListener = ActionListener.wrap(encrypted -> {
            failure.set(new RuntimeException("Successfully encrypted, expected Exception here"));
        }, error -> {
            try {
                Assert.assertTrue(error instanceof ResourceNotFoundException);
                Assert.assertEquals(MASTER_KEY_NOT_READY_ERROR, error.getMessage());
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        encryptor.encrypt("test", TENANT_ID, latchedActionListener);
        Assert.assertTrue("Encryption failed", latch.await(LATCH_WAIT_TIME, SECONDS));
        if (failure.get() != null)
            throw new AssertionError("Encryption failed", failure.get());
    }

    @Test
    public void encrypt_NonExistingMasterKey_FailedToCreateNewKey_VersionConflict_GetExistingMasterKey() throws IOException,
        InterruptedException {
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
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ActionListener<String> actionListener = ActionListener.wrap(encrypted -> {
            try {
                Assert.assertNotNull(encrypted);
                Assert.assertEquals(masterKey.get(DEFAULT_TENANT_ID), encryptor.getMasterKey(null));
            } catch (Throwable t) {
                failure.set(t);
            }

        }, error -> { failure.set(new MLException(error)); });
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        encryptor.encrypt("test", null, latchedActionListener);
        Assert.assertTrue("Encryption failed", latch.await(LATCH_WAIT_TIME, SECONDS));
        if (failure.get() != null)
            throw new AssertionError("Encryption failed", failure.get());
    }

    @Test
    public void encrypt_ThrowExceptionWhenInitMLConfigIndex() throws InterruptedException {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("test exception");
        doThrow(new RuntimeException("test exception")).when(mlIndicesHandler).initMLConfigIndex(any());
        Encryptor encryptor = new EncryptorImpl(clusterService, client, sdkClient, mlIndicesHandler);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ActionListener<String> actionListener = ActionListener.wrap(encrypted -> {
            failure.set(new RuntimeException("Successfully encrypted, expected Exception here"));
        }, error -> { throw new MLException(error); });
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        encryptor.encrypt(masterKey.get(DEFAULT_TENANT_ID), null, latchedActionListener);
        Assert.assertTrue("Encryption failed", latch.await(LATCH_WAIT_TIME, SECONDS));
        if (failure.get() != null)
            throw new AssertionError("Encryption failed", failure.get());
    }

    @Test
    public void encrypt_FailedToInitMLConfigIndex() throws InterruptedException {
        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = (ActionListener) invocation.getArgument(0);
            actionListener.onFailure(new RuntimeException("random test exception"));
            return null;
        }).when(mlIndicesHandler).initMLConfigIndex(any());
        Encryptor encryptor = new EncryptorImpl(clusterService, client, sdkClient, mlIndicesHandler);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ActionListener<String> actionListener = ActionListener.wrap(encrypted -> {
            failure.set(new RuntimeException("Successfully encrypted, expected Exception here"));
        }, error -> {
            try {
                Assert.assertTrue(error instanceof RuntimeException);
                Assert.assertEquals("No response to create ML Config index", error.getMessage());
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        encryptor.encrypt(masterKey.get(DEFAULT_TENANT_ID), null, latchedActionListener);
        Assert.assertTrue("Encryption failed", latch.await(LATCH_WAIT_TIME, SECONDS));
        if (failure.get() != null)
            throw new AssertionError("Encryption failed", failure.get());
    }

    @Test
    public void encrypt_FailedToGetMasterKey() throws InterruptedException {
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
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ActionListener<String> actionListener = ActionListener.wrap(encrypted -> {
            failure.set(new RuntimeException("Successfully encrypted, expected Exception here"));
        }, error -> {
            try {
                Assert.assertTrue(error instanceof RuntimeException);
                Assert.assertEquals("random test exception", error.getMessage());
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        encryptor.encrypt(masterKey.get(DEFAULT_TENANT_ID), null, latchedActionListener);
        Assert.assertTrue("Encryption failed", latch.await(LATCH_WAIT_TIME, SECONDS));
        if (failure.get() != null)
            throw new AssertionError("Encryption failed", failure.get());
    }

    @Test
    public void encrypt_DifferentMasterKey() throws InterruptedException {
        Encryptor encryptor = new EncryptorImpl(null, masterKey.get(DEFAULT_TENANT_ID));
        String test = encryptor.getMasterKey(null);
        Assert.assertNotNull(test);
        AtomicReference<Throwable> failure1 = new AtomicReference<>();
        ActionListener<String> actionListener1 = ActionListener.wrap(encrypted1 -> {
            try {
                AtomicReference<Throwable> failure2 = new AtomicReference<>();
                ActionListener<String> actionListener2 = ActionListener.wrap(encrypted2 -> {
                    try {
                        Assert.assertNotEquals(encrypted1, encrypted2);
                    } catch (Throwable t) {
                        failure2.set(t);
                    }
                }, error -> { failure2.set(new MLException(error)); });
                encryptor.setMasterKey(null, encryptor.generateMasterKey());
                CountDownLatch latch = new CountDownLatch(1);
                LatchedActionListener<String> latchedActionListener2 = new LatchedActionListener<>(actionListener2, latch);
                encryptor.encrypt("test", null, latchedActionListener2);
                Assert.assertTrue("Encryption failed", latch.await(LATCH_WAIT_TIME, SECONDS));
                if (failure2.get() != null)
                    throw new AssertionError("Encryption failed", failure2.get());
            } catch (Throwable t) {
                failure1.set(t);
            }
        }, error -> { failure1.set(new MLException(error)); });
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener1 = new LatchedActionListener<>(actionListener1, latch);
        encryptor.encrypt("test", null, latchedActionListener1);
        Assert.assertTrue("Encryption failed", latch.await(LATCH_WAIT_TIME, SECONDS));
        if (failure1.get() != null)
            throw new AssertionError("Encryption failed", failure1.get());
    }

    @Test
    public void decrypt() throws IOException, InterruptedException {
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
        AtomicReference<Throwable> encryptionFailure = new AtomicReference<>();
        ActionListener<String> encryptionListener = ActionListener.wrap(encrypted -> {
            try {
                AtomicReference<Throwable> decryptionFailure = new AtomicReference<>();
                ActionListener<String> decryptionListener = ActionListener.wrap(decrypted -> {
                    try {
                        Assert.assertEquals("test", decrypted);
                        Assert.assertEquals(masterKey.get(DEFAULT_TENANT_ID), encryptor.getMasterKey(null));
                    } catch (Throwable t) {
                        decryptionFailure.set(t);
                    }
                }, error -> { decryptionFailure.set(new MLException(error)); });
                CountDownLatch decryptionLatch = new CountDownLatch(1);
                LatchedActionListener<String> latchedDecryptionListener = new LatchedActionListener<>(decryptionListener, decryptionLatch);
                encryptor.decrypt(encrypted, null, latchedDecryptionListener);
                Assert.assertTrue("Decryption failed", decryptionLatch.await(LATCH_WAIT_TIME, SECONDS));
                if (decryptionFailure.get() != null)
                    throw new AssertionError("Decryption failed", decryptionFailure.get());
            } catch (Throwable t) {
                encryptionFailure.set(t);
            }
        }, error -> { encryptionFailure.set(new MLException(error)); });
        CountDownLatch encryptionLatch = new CountDownLatch(1);
        LatchedActionListener<String> latchedEncryptionListener = new LatchedActionListener<>(encryptionListener, encryptionLatch);
        encryptor.encrypt("test", null, latchedEncryptionListener);
        Assert.assertTrue("Encryption failed", encryptionLatch.await(LATCH_WAIT_TIME, SECONDS));
        if (encryptionFailure.get() != null)
            throw new AssertionError("Encryption failed", encryptionFailure.get());
    }

    @Test
    public void decrypt_NullMasterKey_GetMasterKey_Exception() throws InterruptedException {
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
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ActionListener<String> actionListener = ActionListener.wrap(decrypted -> {
            failure.set(new RuntimeException("Successfully encrypted, expected Exception here"));
        }, error -> {
            try {
                Assert.assertTrue(error instanceof RuntimeException);
                Assert.assertEquals("test error", error.getMessage());
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        encryptor.decrypt("test", null, latchedActionListener);
        Assert.assertTrue("Encryption failed", latch.await(LATCH_WAIT_TIME, SECONDS));
        if (failure.get() != null)
            throw new AssertionError("Encryption failed", failure.get());
    }

    @Test
    public void decrypt_NoResponseToInitConfigIndex() throws InterruptedException {

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
        AtomicReference<Throwable> failure1 = new AtomicReference<>();
        ActionListener<String> actionListener1 = ActionListener.wrap(encrypted -> {
            try {
                Assert.assertNotNull(encryptor.getMasterKey(TENANT_ID));
                AtomicReference<Throwable> failure2 = new AtomicReference<>();
                ActionListener<String> actionListener2 = ActionListener.wrap(decrypted -> {
                    try {
                        Assert.assertEquals("test", decrypted);
                    } catch (Throwable t) {
                        failure2.set(t);
                    }
                }, error -> { failure2.set(new MLException(error)); });
                CountDownLatch latch2 = new CountDownLatch(1);
                LatchedActionListener<String> latchedActionListener2 = new LatchedActionListener<>(actionListener2, latch2);
                encryptor.decrypt(encrypted, TENANT_ID, latchedActionListener2);
                Assert.assertTrue("Encryption failed", latch2.await(LATCH_WAIT_TIME, SECONDS));
                if (failure2.get() != null)
                    throw new AssertionError("Encryption failed", failure2.get());
            } catch (Throwable t) {
                failure1.set(t);
            }
        }, error -> { failure1.set(new MLException(error)); });
        CountDownLatch latch1 = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener1 = new LatchedActionListener<>(actionListener1, latch1);
        encryptor.encrypt("test", TENANT_ID, latchedActionListener1);
        Assert.assertTrue("Encryption failed", latch1.await(LATCH_WAIT_TIME, SECONDS));
        if (failure1.get() != null)
            throw new AssertionError("Encryption failed", failure1.get());
    }

    @Test
    public void initMasterKey_AddTenantMasterKeys() throws IOException, InterruptedException {
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
        AtomicReference<Throwable> failure = new AtomicReference<>();

        ActionListener<String> actionListener = ActionListener.wrap(encrypted -> {
            try {
                Assert.assertNotNull(encrypted);

                // Verify that the tenant-specific master key is added
                String tenantMasterKey = encryptor.getMasterKey(TENANT_ID);
                Assert.assertNotNull(tenantMasterKey);

                // Ensure that the master key for this tenant matches the expected value
                Assert.assertEquals(GENERATED_MASTER_KEY, encryptor.getMasterKey(TENANT_ID));
            } catch (Throwable t) {
                failure.set(t);
            }
        }, error -> { failure.set(new MLException(error)); });
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        // Encrypt using the specified tenant ID
        encryptor.encrypt("test", TENANT_ID, latchedActionListener);
        Assert.assertTrue("Encryption failed", latch.await(LATCH_WAIT_TIME, SECONDS));
        if (failure.get() != null)
            throw new AssertionError("Encryption failed", failure.get());
    }

    @Test
    public void encrypt_SdkClientPutDataObjectFailure() throws InterruptedException {
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
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ActionListener<String> actionListener = ActionListener.wrap(decrypted -> {
            failure.set(new RuntimeException("Successfully encrypted, expected Exception here"));
        }, error -> {
            try {
                Assert.assertTrue(error instanceof RuntimeException);
                Assert.assertEquals("Failed to index ML encryption master key", error.getMessage());
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        encryptor.encrypt("test", null, latchedActionListener);
        Assert.assertTrue("Encryption failed", latch.await(LATCH_WAIT_TIME, SECONDS));
        if (failure.get() != null)
            throw new AssertionError("Encryption failed", failure.get());
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
    public void encrypt_MasterKeyFieldMismatch_ShouldFallbackToProperKeyField() throws IOException, InterruptedException {
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
        AtomicReference<Throwable> failure1 = new AtomicReference<>();

        // Old buggy code would try to access response.source().get(masterKeyId) and get null
        // This test ensures the new fix works — we access MASTER_KEY properly
        ActionListener<String> actionListener1 = ActionListener.wrap(encrypted -> {
            try {
                AtomicReference<Throwable> failure2 = new AtomicReference<>();
                Assert.assertNotNull(encrypted);
                ActionListener<String> actionListener2 = ActionListener.wrap(decrypted -> {
                    try {
                        Assert.assertEquals("test", decrypted);
                    } catch (Throwable t) {
                        failure2.set(t);
                    }
                }, error -> { failure2.set(new MLException(error)); });
                CountDownLatch latch2 = new CountDownLatch(1);
                LatchedActionListener<String> latchedActionListener2 = new LatchedActionListener<>(actionListener2, latch2);
                encryptor.decrypt(encrypted, TENANT_ID, latchedActionListener2);
                Assert.assertTrue("Decryption failed", latch2.await(LATCH_WAIT_TIME, SECONDS));
                if (failure2.get() != null)
                    throw new AssertionError("Decryption failed", failure2.get());
            } catch (Throwable t) {
                failure1.set(t);
            }
        }, error -> { failure1.set(new MLException(error)); });
        CountDownLatch latch1 = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener1 = new LatchedActionListener<>(actionListener1, latch1);
        encryptor.encrypt("test", TENANT_ID, latchedActionListener1);
        Assert.assertTrue("Encryption failed", latch1.await(LATCH_WAIT_TIME, SECONDS));
        if (failure1.get() != null)
            throw new AssertionError("Encryption failed", failure1.get());
    }

    @Test
    public void encrypt_MasterKeyFieldExistsButNotString_ShouldThrowError() throws IOException, InterruptedException {
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
        AtomicReference<Throwable> failure = new AtomicReference<>();

        ActionListener<String> actionListener = ActionListener.wrap(decrypted -> {
            failure.set(new RuntimeException("Successfully encrypted, expected Exception here"));
        }, error -> {
            try {
                Assert.assertTrue(error instanceof RuntimeException);
                Assert.assertEquals(MASTER_KEY_NOT_READY_ERROR, error.getMessage());
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        encryptor.encrypt("test", TENANT_ID, latchedActionListener);
        Assert.assertTrue("Encryption failed", latch.await(LATCH_WAIT_TIME, SECONDS));
        if (failure.get() != null)
            throw new AssertionError("Encryption failed", failure.get());
    }

    @Test
    public void encrypt_MasterKeyFieldMissing_ShouldThrowError() throws IOException, InterruptedException {
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
        AtomicReference<Throwable> failure = new AtomicReference<>();

        ActionListener<String> actionListener = ActionListener.wrap(decrypted -> {
            failure.set(new RuntimeException("Successfully encrypted, expected Exception here"));
        }, error -> {
            try {
                Assert.assertTrue(error instanceof RuntimeException);
                Assert.assertEquals(MASTER_KEY_NOT_READY_ERROR, error.getMessage());
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        encryptor.encrypt("test", TENANT_ID, latchedActionListener);
        Assert.assertTrue("Encryption failed", latch.await(LATCH_WAIT_TIME, SECONDS));
        if (failure.get() != null)
            throw new AssertionError("Encryption failed", failure.get());
    }

    @Test
    public void handleVersionConflictResponse_RetrySucceeds() throws IOException, InterruptedException {
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
        AtomicReference<Throwable> failure1 = new AtomicReference<>();
        // This will go through the PUT failure, then version conflict handling, and use the returned key.
        ActionListener<String> actionListener1 = ActionListener.wrap(encrypted -> {
            try {
                AtomicReference<Throwable> failure2 = new AtomicReference<>();
                Assert.assertNotNull(encrypted);
                ActionListener<String> actionListener2 = ActionListener
                    .wrap(decrypted -> { Assert.assertEquals("test", decrypted); }, error -> {
                        failure2.set(new MLException(error));
                    });
                CountDownLatch latch2 = new CountDownLatch(1);
                LatchedActionListener<String> latchedActionListener2 = new LatchedActionListener<>(actionListener2, latch2);
                encryptor.decrypt(encrypted, TENANT_ID, latchedActionListener2);
                Assert.assertTrue("Decryption failed", latch2.await(LATCH_WAIT_TIME, SECONDS));
                if (failure2.get() != null)
                    throw new AssertionError("Decryption failed", failure2.get());
            } catch (Throwable t) {
                failure1.set(t);
            }
        }, error -> { failure1.set(new MLException(error)); });
        CountDownLatch latch1 = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener1 = new LatchedActionListener<>(actionListener1, latch1);
        encryptor.encrypt("test", TENANT_ID, latchedActionListener1);
        Assert.assertTrue("Encryption failed", latch1.await(LATCH_WAIT_TIME, SECONDS));
        if (failure1.get() != null)
            throw new AssertionError("Encryption failed", failure1.get());
    }

    @Test
    public void handleVersionConflictResponse_RetryFails() throws IOException, InterruptedException {
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
        AtomicReference<Throwable> failure = new AtomicReference<>();

        // We expect an MLException (or a ResourceNotFoundException) to be thrown due to the failure in getting the key.
        ActionListener<String> actionListener = ActionListener.wrap(decrypted -> {
            failure.set(new RuntimeException("Successfully encrypted, expected Exception here"));
        }, error -> {
            try {
                Assert.assertTrue(error instanceof RuntimeException);
                Assert.assertEquals("java.io.IOException: Failed to get master key on retry", error.getMessage()); // Or adjust based on
                                                                                                                   // your
                                                                                                                   // exact message.
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        encryptor.encrypt("test", TENANT_ID, latchedActionListener);
        Assert.assertTrue("Encryption failed", latch.await(LATCH_WAIT_TIME, SECONDS));
        if (failure.get() != null)
            throw new AssertionError("Encryption failed", failure.get());
    }

    @Test
    public void encrypt_GetSourceAsMapIsNull_ShouldThrowResourceNotFound() throws Exception, InterruptedException {

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

        AtomicReference<Throwable> failure = new AtomicReference<>();
        // Now run it
        ActionListener<String> actionListener = ActionListener.wrap(decrypted -> {
            failure.set(new RuntimeException("Successfully encrypted, expected Exception here"));
        }, error -> {
            try {
                Assert.assertTrue(error instanceof RuntimeException);
                Assert.assertEquals(MASTER_KEY_NOT_READY_ERROR, error.getMessage());
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        encryptor.encrypt("test", TENANT_ID, latchedActionListener);
        Assert.assertTrue("Encryption failed", latch.await(LATCH_WAIT_TIME, SECONDS));
        if (failure.get() != null)
            throw new AssertionError("Encryption failed", failure.get());
    }

    @Test
    public void test_MultipleEncryptDecryptRequests_From_SingleThread() throws InterruptedException, IOException {
        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = invocation.getArgument(0);
            actionListener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLConfigIndex(any());

        GetResponse response = prepareMLConfigResponse(null);
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(response);
            return null;
        }).when(client).get(any(), any());

        Encryptor encryptor = new EncryptorImpl(clusterService, client, sdkClient, mlIndicesHandler);
        Assert.assertNull(encryptor.getMasterKey(null));
        AtomicReference<Throwable> failure1 = new AtomicReference<>();
        for (int i = 0; i < 3; i++) {
            ActionListener<String> actionListener1 = ActionListener.wrap(encrypted -> {
                AtomicReference<Throwable> failure2 = new AtomicReference<>();
                try {
                    Assert.assertNotNull(encrypted);
                    ActionListener<String> actionListener2 = ActionListener.wrap(decrypted -> {
                        try {
                            Assert.assertEquals("test", decrypted);
                        } catch (Throwable t) {
                            failure2.set(t);
                        }
                    }, error -> { failure2.set(new MLException(error)); });
                    CountDownLatch latch2 = new CountDownLatch(1);
                    LatchedActionListener<String> latchedActionListener2 = new LatchedActionListener<>(actionListener2, latch2);
                    encryptor.decrypt(encrypted, TENANT_ID, latchedActionListener2);
                    Assert.assertTrue("Decryption failed", latch2.await(LATCH_WAIT_TIME, SECONDS));
                    if (failure2.get() != null)
                        throw new AssertionError("Decryption failed", failure2.get());
                } catch (Throwable t) {
                    failure1.set(t);
                }
            }, error -> { failure1.set(new MLException(error)); });
            CountDownLatch latch1 = new CountDownLatch(1);
            LatchedActionListener<String> latchedActionListener1 = new LatchedActionListener<>(actionListener1, latch1);
            encryptor.encrypt("test", TENANT_ID, latchedActionListener1);
            Assert.assertTrue("Encryption failed", latch1.await(LATCH_WAIT_TIME, SECONDS));
            if (failure1.get() != null)
                throw new AssertionError("Encryption failed", failure1.get());
        }
    }

    @Test
    public void test_MultipleEncryptDecryptRequests_From_MultipleThreads() throws IOException, InterruptedException {
        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = invocation.getArgument(0);
            actionListener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLConfigIndex(any());

        GetResponse response = prepareMLConfigResponse(null);
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            actionListener.onResponse(response);
            return null;
        }).when(client).get(any(), any());

        TestThreadPool testThreadPool = null;
        try {
            testThreadPool = new TestThreadPool("testThreadPool");
            int numberOfThreads = 9;
            CountDownLatch threadLatch = new CountDownLatch(numberOfThreads);
            String[] tenantIds = new String[] { "123456", "1234567", null };
            String[] texts = new String[] { "test1", "test2", "test3" };
            for (int i = 0; i < 3; i++) {
                testThreadPool.generic().submit(() -> { testEncryptionDecryption(tenantIds[0], texts[0], threadLatch); });
                testThreadPool.generic().submit(() -> { testEncryptionDecryption(tenantIds[1], texts[1], threadLatch); });
                testThreadPool.generic().submit(() -> { testEncryptionDecryption(tenantIds[2], texts[2], threadLatch); });
            }
            Assert.assertTrue("Encryption failed with multiple threads", threadLatch.await(LATCH_WAIT_TIME * numberOfThreads, SECONDS));
        } finally {
            if (testThreadPool != null) {
                testThreadPool.shutdown();
            }
        }
    }

    @Test
    public void test_MultipleEncryptDecryptRequests_From_MultipleThreads_Throws_Exception() throws InterruptedException {
        doAnswer(invocation -> {
            ActionListener<Boolean> actionListener = invocation.getArgument(0);
            actionListener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLConfigIndex(any());
        doAnswer(invocation -> {
            ActionListener<GetResponse> actionListener = invocation.getArgument(1);
            GetResponse getResponse = prepareNotExistsGetResponse();
            actionListener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());
        doAnswer(invocation -> {
            ActionListener<IndexResponse> actionListener = invocation.getArgument(1);
            actionListener.onFailure(new RuntimeException("random test exception"));
            return null;
        }).when(client).index(any(), any());

        Encryptor encryptor = new EncryptorImpl(clusterService, client, sdkClient, mlIndicesHandler);
        Assert.assertNull(encryptor.getMasterKey(null));
        TestThreadPool testThreadPool = null;
        try {
            testThreadPool = new TestThreadPool("testThreadPool");
            int numberOfThreads = 3;
            CountDownLatch threadLatch = new CountDownLatch(numberOfThreads);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            for (int i = 0; i < numberOfThreads; i++) {
                testThreadPool.generic().submit(() -> {
                    ActionListener<String> actionListener = ActionListener.wrap(decrypted -> {
                        failure.set(new RuntimeException("Successfully encrypted, expected Exception here"));
                        threadLatch.countDown();
                    }, error -> {
                        try {
                            Assert.assertTrue(error instanceof RuntimeException);
                            Assert.assertEquals("random test exception", error.getMessage());
                        } catch (Throwable t) {
                            failure.set(t);
                        } finally {
                            threadLatch.countDown();
                        }
                    });
                    CountDownLatch latch = new CountDownLatch(1);
                    LatchedActionListener<String> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
                    encryptor.encrypt("test", null, latchedActionListener);
                    try {
                        Assert.assertTrue("Encryption failed", latch.await(LATCH_WAIT_TIME, SECONDS));
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    if (failure.get() != null)
                        throw new AssertionError("Encryption failed", failure.get());
                });
            }
            Assert.assertTrue("Encryption failed with multiple threads", threadLatch.await(LATCH_WAIT_TIME * numberOfThreads, SECONDS));
        } finally {
            if (testThreadPool != null) {
                testThreadPool.shutdown();
            }
        }
    }

    void testEncryptionDecryption(String tenantId, String text, CountDownLatch threadLatch) {
        Encryptor encryptor = new EncryptorImpl(clusterService, client, sdkClient, mlIndicesHandler);
        Assert.assertNull(encryptor.getMasterKey(null));
        AtomicReference<Throwable> failure1 = new AtomicReference<>();
        ActionListener<String> actionListener1 = ActionListener.wrap(encrypted -> {
            try {
                AtomicReference<Throwable> failure2 = new AtomicReference<>();
                Assert.assertNotNull(encrypted);
                ActionListener<String> actionListener2 = ActionListener.wrap(decrypted -> {
                    try {
                        Assert.assertEquals(text, decrypted);
                    } catch (Throwable t) {
                        failure2.set(t);
                    } finally {
                        threadLatch.countDown();
                    }
                }, error -> {
                    threadLatch.countDown();
                    failure2.set(new MLException(error));
                });
                CountDownLatch latch2 = new CountDownLatch(1);
                LatchedActionListener<String> latchedActionListener2 = new LatchedActionListener<>(actionListener2, latch2);
                encryptor.decrypt(encrypted, tenantId, latchedActionListener2);
                try {
                    Assert.assertTrue("Decryption failed", latch2.await(LATCH_WAIT_TIME, SECONDS));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                if (failure2.get() != null)
                    throw new AssertionError("Decryption failed", failure2.get());
            } catch (Throwable t) {
                failure1.set(t);
            } finally {
                threadLatch.countDown();
            }
        }, error -> {
            threadLatch.countDown();
            failure1.set(new MLException(error));
        });
        CountDownLatch latch1 = new CountDownLatch(1);
        LatchedActionListener<String> latchedActionListener1 = new LatchedActionListener<>(actionListener1, latch1);
        encryptor.encrypt(text, tenantId, latchedActionListener1);
        try {
            Assert.assertTrue("Encryption failed", latch1.await(LATCH_WAIT_TIME, SECONDS));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (failure1.get() != null)
            throw new AssertionError("Encryption failed", failure1.get());
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
}
