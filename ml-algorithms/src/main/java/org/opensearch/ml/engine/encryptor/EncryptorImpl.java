/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.encryptor;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.opensearch.ml.common.CommonValue.MASTER_KEY;
import static org.opensearch.ml.common.CommonValue.ML_CONFIG_INDEX;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.MLConfig.CREATE_TIME_FIELD;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MASTER_KEY_CACHE_TTL_MINUTES;
import static org.opensearch.ml.common.utils.StringUtils.hashString;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.spec.SecretKeySpec;

import org.opensearch.OpenSearchException;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.engine.VersionConflictEngineException;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.GetDataObjectResponse;
import org.opensearch.remote.metadata.client.PutDataObjectRequest;
import org.opensearch.remote.metadata.client.PutDataObjectResponse;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.transport.client.Client;

import com.amazonaws.encryptionsdk.AwsCrypto;
import com.amazonaws.encryptionsdk.CommitmentPolicy;
import com.amazonaws.encryptionsdk.CryptoResult;
import com.amazonaws.encryptionsdk.jce.JceMasterKey;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class EncryptorImpl implements Encryptor {

    public static final String MASTER_KEY_NOT_READY_ERROR =
        "The ML encryption master key has not been initialized yet. Please retry after waiting for 10 seconds.";
    private ClusterService clusterService;
    private Client client;
    private SdkClient sdkClient;
    private final Cache<String, String> tenantMasterKeys;
    private MLIndicesHandler mlIndicesHandler;
    private final Object lock = new Object();
    private volatile long masterKeyCacheTtlMinutes;

    // concurrent map can't have null as a key. This is to support single tenancy
    // assigning some random string so that it can't be duplicate
    public static final String DEFAULT_TENANT_ID = "03000200-0400-0500-0006-000700080009";

    public EncryptorImpl(ClusterService clusterService, Client client, SdkClient sdkClient, MLIndicesHandler mlIndicesHandler) {
        this.masterKeyCacheTtlMinutes = ML_COMMONS_MASTER_KEY_CACHE_TTL_MINUTES.get(clusterService.getSettings());
        this.tenantMasterKeys = CacheBuilder
            .newBuilder()
            .expireAfterWrite(masterKeyCacheTtlMinutes, TimeUnit.MINUTES)
            .removalListener(new RemovalListener<String, String>() {
                @Override
                public void onRemoval(RemovalNotification<String, String> notification) {
                    log.info("Master key cache entry removed - Tenant: {}, Cause: {}", notification.getKey(), notification.getCause());
                }
            })
            .build();
        this.clusterService = clusterService;
        this.client = client;
        this.sdkClient = sdkClient;
        this.mlIndicesHandler = mlIndicesHandler;

        // Register listener for dynamic setting updates
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_MASTER_KEY_CACHE_TTL_MINUTES, this::updateCacheTtl);
    }

    /**
     * Updates the cache TTL when the cluster setting changes.
     * Note: This only affects new cache entries. Existing entries will expire based on their original TTL.
     */
    private void updateCacheTtl(Integer newTtlMinutes) {
        this.masterKeyCacheTtlMinutes = newTtlMinutes;
        log.info("Master key cache TTL updated to {} minutes. Note: Existing cache entries retain their original TTL.", newTtlMinutes);
    }

    // Package-private constructor for testing with custom TTL
    EncryptorImpl(
        ClusterService clusterService,
        Client client,
        SdkClient sdkClient,
        MLIndicesHandler mlIndicesHandler,
        long cacheTtl,
        TimeUnit timeUnit
    ) {
        this.tenantMasterKeys = CacheBuilder
            .newBuilder()
            .expireAfterWrite(cacheTtl, timeUnit)
            .removalListener(new RemovalListener<String, String>() {
                @Override
                public void onRemoval(RemovalNotification<String, String> notification) {
                    log.info("Master key cache entry removed - Tenant: {}, Cause: {}", notification.getKey(), notification.getCause());
                }
            })
            .build();
        this.clusterService = clusterService;
        this.client = client;
        this.sdkClient = sdkClient;
        this.mlIndicesHandler = mlIndicesHandler;
    }

    public EncryptorImpl(String tenantId, String masterKey) {
        this(tenantId, masterKey, 5, TimeUnit.MINUTES);  // Default 5 minutes for testing
    }

    // Package-private constructor for testing with custom TTL
    EncryptorImpl(String tenantId, String masterKey, long cacheTtl, TimeUnit timeUnit) {
        this.tenantMasterKeys = CacheBuilder
            .newBuilder()
            .expireAfterWrite(cacheTtl, timeUnit)
            .removalListener(new RemovalListener<String, String>() {
                @Override
                public void onRemoval(RemovalNotification<String, String> notification) {
                    log.info("Master key cache entry removed - Tenant: {}, Cause: {}", notification.getKey(), notification.getCause());
                }
            })
            .build();
        this.tenantMasterKeys.put(Objects.requireNonNullElse(tenantId, DEFAULT_TENANT_ID), masterKey);
    }

    @Override
    public void setMasterKey(String tenantId, String masterKey) {
        this.tenantMasterKeys.put(Objects.requireNonNullElse(tenantId, DEFAULT_TENANT_ID), masterKey);
    }

    @Override
    public String getMasterKey(String tenantId) {
        return tenantMasterKeys.getIfPresent(Objects.requireNonNullElse(tenantId, DEFAULT_TENANT_ID));
    }

    @Override
    public String encrypt(String plainText, String tenantId) {
        String masterKey = getOrInitMasterKey(tenantId);
        final AwsCrypto crypto = AwsCrypto.builder().withCommitmentPolicy(CommitmentPolicy.RequireEncryptRequireDecrypt).build();
        JceMasterKey jceMasterKey = createJceMasterKey(masterKey);

        final CryptoResult<byte[], JceMasterKey> encryptResult = crypto
            .encryptData(jceMasterKey, plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encryptResult.getResult());
    }

    @Override
    public String decrypt(String encryptedText, String tenantId) {
        String masterKey = getOrInitMasterKey(tenantId);
        final AwsCrypto crypto = AwsCrypto.builder().withCommitmentPolicy(CommitmentPolicy.RequireEncryptRequireDecrypt).build();
        JceMasterKey jceMasterKey = createJceMasterKey(masterKey);

        final CryptoResult<byte[], JceMasterKey> decryptedResult = crypto
            .decryptData(jceMasterKey, Base64.getDecoder().decode(encryptedText));
        return new String(decryptedResult.getResult());
    }

    @Override
    public String generateMasterKey() {
        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        return Base64.getEncoder().encodeToString(keyBytes);
    }

    private JceMasterKey createJceMasterKey(String masterKey) {
        byte[] bytes = Base64.getDecoder().decode(masterKey);
        return JceMasterKey.getInstance(new SecretKeySpec(bytes, "AES"), "Custom", "", "AES/GCM/NOPADDING");
    }

    private String getOrInitMasterKey(String tenantId) {
        String effectiveTenantId = Objects.requireNonNullElse(tenantId, DEFAULT_TENANT_ID);
        String masterKey = tenantMasterKeys.getIfPresent(effectiveTenantId);
        if (masterKey != null) {
            return masterKey;
        }

        // Double-checked locking to prevent duplicate index writes under high concurrency
        synchronized (lock) {
            masterKey = tenantMasterKeys.getIfPresent(effectiveTenantId);
            if (masterKey == null) {
                initMasterKey(tenantId);
                masterKey = tenantMasterKeys.getIfPresent(effectiveTenantId);
                if (masterKey == null) {
                    throw new ResourceNotFoundException(MASTER_KEY_NOT_READY_ERROR);
                }
            }
        }
        return masterKey;
    }

    private void initMasterKey(String tenantId) {
        if (tenantMasterKeys.getIfPresent(Objects.requireNonNullElse(tenantId, DEFAULT_TENANT_ID)) != null) {
            return;
        }
        String masterKeyId = MASTER_KEY;
        if (tenantId != null) {
            masterKeyId = MASTER_KEY + "_" + hashString(tenantId);
        }
        AtomicReference<Exception> exceptionRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        mlIndicesHandler.initMLConfigIndex(createInitMLConfigIndexListener(exceptionRef, latch, tenantId, masterKeyId));
        waitForLatch(latch);
        checkMasterKeyInitialization(tenantId, exceptionRef);
    }

    private void waitForLatch(CountDownLatch latch) {
        try {
            // TODO: we need to find a better way to depend on the listener rather than waiting for a fixed time
            // sometimes it may be take more than 1 seconds in multi-tenancy case where we need to
            // create index, create a master key and then perform the prediction.
            boolean completed = latch.await(3, SECONDS);
            if (!completed) {
                throw new MLException("Fetching master key timed out.");
            }
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private void checkMasterKeyInitialization(String tenantId, AtomicReference<Exception> exceptionRef) {
        if (exceptionRef.get() != null) {
            log.debug("Failed to init master key for tenant {}", tenantId, exceptionRef.get());
            if (exceptionRef.get() instanceof RuntimeException) {
                throw (RuntimeException) exceptionRef.get();
            } else {
                throw new MLException(exceptionRef.get());
            }
        }
        if (tenantMasterKeys.getIfPresent(Objects.requireNonNullElse(tenantId, DEFAULT_TENANT_ID)) == null) {
            throw new ResourceNotFoundException(MASTER_KEY_NOT_READY_ERROR);
        }
    }

    private ActionListener<Boolean> createInitMLConfigIndexListener(
        AtomicReference<Exception> exceptionRef,
        CountDownLatch latch,
        String tenantId,
        String masterKeyId
    ) {
        return ActionListener
            .wrap(
                r -> handleInitMLConfigIndexSuccess(exceptionRef, latch, tenantId, masterKeyId),
                e -> handleInitMLConfigIndexFailure(exceptionRef, latch, masterKeyId, e)
            );
    }

    private void handleInitMLConfigIndexSuccess(
        AtomicReference<Exception> exceptionRef,
        CountDownLatch latch,
        String tenantId,
        String masterKeyId
    ) {
        FetchSourceContext fetchSourceContext = new FetchSourceContext(true, Strings.EMPTY_ARRAY, Strings.EMPTY_ARRAY);
        GetDataObjectRequest getDataObjectRequest = createGetDataObjectRequest(tenantId, fetchSourceContext);

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            sdkClient
                .getDataObjectAsync(getDataObjectRequest)
                .whenComplete(
                    (response, throwable) -> handleGetDataObjectResponse(
                        tenantId,
                        masterKeyId,
                        context,
                        response,
                        throwable,
                        exceptionRef,
                        latch
                    )
                );
        }
    }

    private void handleInitMLConfigIndexFailure(
        AtomicReference<Exception> exceptionRef,
        CountDownLatch latch,
        String masterKeyId,
        Exception e
    ) {
        log.debug("Failed to init ML config index", e);
        exceptionRef.set(new RuntimeException("No response to create ML Config index"));
        latch.countDown();
    }

    private void handleGetDataObjectResponse(
        String tenantId,
        String masterKeyId,
        ThreadContext.StoredContext context,
        GetDataObjectResponse response,
        Throwable throwable,
        AtomicReference<Exception> exceptionRef,
        CountDownLatch latch
    ) {
        log.debug("Completed Get MASTER_KEY Request, for tenant id:{}", tenantId);

        if (throwable != null) {
            handleGetDataObjectFailure(throwable, exceptionRef, latch);
        } else {
            handleGetDataObjectSuccess(response, tenantId, masterKeyId, exceptionRef, latch, context);
        }
        context.restore();
    }

    private void handleGetDataObjectFailure(Throwable throwable, AtomicReference<Exception> exceptionRef, CountDownLatch latch) {
        Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable, OpenSearchStatusException.class);
        log.debug("Failed to get ML encryption master key from config index", cause);
        exceptionRef.set(cause);
        latch.countDown();
    }

    private void handleGetDataObjectSuccess(
        GetDataObjectResponse response,
        String tenantId,
        String masterKeyId,
        AtomicReference<Exception> exceptionRef,
        CountDownLatch latch,
        ThreadContext.StoredContext context
    ) {
        try {
            GetResponse getMasterKeyResponse = response.parser() == null ? null : GetResponse.fromXContent(response.parser());
            if (getMasterKeyResponse != null && getMasterKeyResponse.isExists()) {
                Map<String, Object> source = getMasterKeyResponse.getSourceAsMap();
                if (source != null) {
                    Object keyValue = source.get(MASTER_KEY);
                    if (keyValue instanceof String) {
                        this.tenantMasterKeys.put(Objects.requireNonNullElse(tenantId, DEFAULT_TENANT_ID), (String) keyValue);
                        log.info("ML encryption master key already initialized, no action needed");
                    } else {
                        log.error("Master key not found or not a string for tenantId: {}, masterKeyId: {}", tenantId, masterKeyId);
                        exceptionRef.set(new ResourceNotFoundException(MASTER_KEY_NOT_READY_ERROR));
                    }
                } else {
                    log.error("Master key not found or not a string for tenantId: {}, masterKeyId: {}", tenantId, masterKeyId);
                    exceptionRef.set(new ResourceNotFoundException(MASTER_KEY_NOT_READY_ERROR));
                }
                latch.countDown();
            } else {
                initializeNewMasterKey(tenantId, masterKeyId, exceptionRef, latch, context);
            }
        } catch (Exception e) {
            log.debug("Failed to get ML encryption master key from config index", e);
            exceptionRef.set(e);
            latch.countDown();
        }
    }

    private void initializeNewMasterKey(
        String tenantId,
        String masterKeyId,
        AtomicReference<Exception> exceptionRef,
        CountDownLatch latch,
        ThreadContext.StoredContext context
    ) {
        final String generatedMasterKey = generateMasterKey();
        sdkClient
            .putDataObjectAsync(createPutDataObjectRequest(tenantId, masterKeyId, generatedMasterKey))
            .whenComplete((putDataObjectResponse, throwable1) -> {
                try {
                    handlePutDataObjectResponse(
                        tenantId,
                        masterKeyId,
                        context,
                        putDataObjectResponse,
                        throwable1,
                        exceptionRef,
                        latch,
                        generatedMasterKey
                    );
                } catch (IOException e) {
                    log.debug("Failed to index ML encryption master key to config index", e);
                    exceptionRef.set(e);
                    latch.countDown();
                }
            });
    }

    private PutDataObjectRequest createPutDataObjectRequest(String tenantId, String masterKeyId, String generatedMasterKey) {
        return PutDataObjectRequest
            .builder()
            .tenantId(tenantId)
            .index(ML_CONFIG_INDEX)
            .id(masterKeyId)
            .overwriteIfExists(false)
            .dataObject(
                Map
                    .of(
                        MASTER_KEY,
                        generatedMasterKey,
                        CREATE_TIME_FIELD,
                        Instant.now().toEpochMilli(),
                        TENANT_ID_FIELD,
                        Objects.requireNonNullElse(tenantId, DEFAULT_TENANT_ID)
                    )
            )
            .build();
    }

    private void handlePutDataObjectResponse(
        String tenantId,
        String masterKeyId,
        ThreadContext.StoredContext context,
        PutDataObjectResponse putDataObjectResponse,
        Throwable throwable,
        AtomicReference<Exception> exceptionRef,
        CountDownLatch latch,
        String generatedMasterKey
    ) throws IOException {
        context.restore();

        if (throwable != null) {
            handlePutDataObjectFailure(tenantId, masterKeyId, context, throwable, exceptionRef, latch);
        } else {
            IndexResponse indexResponse = IndexResponse.fromXContent(putDataObjectResponse.parser());
            log.info("Master key creation result: {}, Master key id: {}", indexResponse.getResult(), indexResponse.getId());
            this.tenantMasterKeys.put(Objects.requireNonNullElse(tenantId, DEFAULT_TENANT_ID), generatedMasterKey);
            log.info("ML encryption master key initialized successfully");
            latch.countDown();
        }
    }

    private void handlePutDataObjectFailure(
        String tenantId,
        String masterKeyId,
        ThreadContext.StoredContext context,
        Throwable throwable,
        AtomicReference<Exception> exceptionRef,
        CountDownLatch latch
    ) {
        Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable, OpenSearchStatusException.class);
        if (cause instanceof VersionConflictEngineException
            || (cause instanceof OpenSearchException && ((OpenSearchException) cause).status() == RestStatus.CONFLICT)) {
            handleVersionConflict(tenantId, masterKeyId, context, exceptionRef, latch);
        } else {
            log.debug("Failed to index ML encryption master key to config index", cause);
            exceptionRef.set(cause);
            latch.countDown();
        }
    }

    private void handleVersionConflict(
        String tenantId,
        String masterKeyId,
        ThreadContext.StoredContext context,
        AtomicReference<Exception> exceptionRef,
        CountDownLatch latch
    ) {
        sdkClient
            .getDataObjectAsync(
                createGetDataObjectRequest(tenantId, new FetchSourceContext(true, Strings.EMPTY_ARRAY, Strings.EMPTY_ARRAY))
            )
            .whenComplete((response, throwable) -> {
                try {
                    handleVersionConflictResponse(tenantId, masterKeyId, context, response, throwable, exceptionRef, latch);
                } catch (IOException e) {
                    log.debug("Failed to get ML encryption master key from config index", e);
                    exceptionRef.set(e);
                    latch.countDown();
                }
            });
    }

    private GetDataObjectRequest createGetDataObjectRequest(String tenantId, FetchSourceContext fetchSourceContext) {
        String masterKeyId = MASTER_KEY;
        if (tenantId != null) {
            masterKeyId = MASTER_KEY + "_" + hashString(tenantId);
        }
        return GetDataObjectRequest
            .builder()
            .index(ML_CONFIG_INDEX)
            .id(masterKeyId)
            .tenantId(tenantId)
            .fetchSourceContext(fetchSourceContext)
            .build();
    }

    private void handleVersionConflictResponse(
        String tenantId,
        String masterKeyId,
        ThreadContext.StoredContext context,
        GetDataObjectResponse response1,
        Throwable throwable2,
        AtomicReference<Exception> exceptionRef,
        CountDownLatch latch
    ) throws IOException {
        context.restore();
        log.debug("Completed Get config item");

        if (throwable2 != null) {
            Exception cause1 = SdkClientUtils.unwrapAndConvertToException(throwable2, OpenSearchStatusException.class);
            log.debug("Failed to get ML encryption master key from config index", cause1);
            exceptionRef.set(new ResourceNotFoundException(MASTER_KEY_NOT_READY_ERROR));
            latch.countDown();
        } else {
            GetResponse getMasterKeyResponse = response1.parser() == null ? null : GetResponse.fromXContent(response1.parser());
            if (getMasterKeyResponse != null && getMasterKeyResponse.isExists()) {
                Map<String, Object> source = getMasterKeyResponse.getSourceAsMap();
                if (source != null) {
                    Object keyValue = source.get(MASTER_KEY);
                    if (keyValue instanceof String) {
                        this.tenantMasterKeys.put(Objects.requireNonNullElse(tenantId, DEFAULT_TENANT_ID), (String) keyValue);
                        log.info("ML encryption master key already initialized, no action needed");
                    } else {
                        log.error("Master key not found or not a string for tenantId: {}, masterKeyId: {}", tenantId, masterKeyId);
                        exceptionRef.set(new ResourceNotFoundException(MASTER_KEY_NOT_READY_ERROR));
                    }
                } else {
                    log.error("Master key not found or not a string for tenantId: {}, masterKeyId: {}", tenantId, masterKeyId);
                    exceptionRef.set(new ResourceNotFoundException(MASTER_KEY_NOT_READY_ERROR));
                }
                latch.countDown();
            } else {
                exceptionRef.set(new ResourceNotFoundException(MASTER_KEY_NOT_READY_ERROR));
                latch.countDown();
            }
        }
    }
}
