/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.encryptor;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.opensearch.ml.common.CommonValue.MASTER_KEY;
import static org.opensearch.ml.common.CommonValue.ML_CONFIG_INDEX;
import static org.opensearch.ml.common.MLConfig.CREATE_TIME_FIELD;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.spec.SecretKeySpec;

import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.get.GetResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.index.engine.VersionConflictEngineException;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.sdk.GetDataObjectRequest;
import org.opensearch.sdk.GetDataObjectResponse;
import org.opensearch.sdk.PutDataObjectRequest;
import org.opensearch.sdk.SdkClient;
import org.opensearch.sdk.SdkClientUtils;
import org.opensearch.search.fetch.subphase.FetchSourceContext;

import com.amazonaws.encryptionsdk.AwsCrypto;
import com.amazonaws.encryptionsdk.CommitmentPolicy;
import com.amazonaws.encryptionsdk.CryptoResult;
import com.amazonaws.encryptionsdk.jce.JceMasterKey;
import com.google.common.collect.ImmutableMap;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class EncryptorImpl implements Encryptor {

    public static final String MASTER_KEY_NOT_READY_ERROR =
        "The ML encryption master key has not been initialized yet. Please retry after waiting for 10 seconds.";
    private ClusterService clusterService;
    private Client client;
    private SdkClient sdkClient;
    private final Map<String, String> tenantMasterKeys;
    private MLIndicesHandler mlIndicesHandler;

    // concurrent map can't have null as a key. This is to support single tenancy
    // assigning some random string so that it can't be duplicate
    public static final String DEFAULT_TENANT_ID = "03000200-0400-0500-0006-000700080009";

    public EncryptorImpl(ClusterService clusterService, Client client, SdkClient sdkClient, MLIndicesHandler mlIndicesHandler) {
        this.tenantMasterKeys = new ConcurrentHashMap<>();
        this.clusterService = clusterService;
        this.client = client;
        this.sdkClient = sdkClient;
        this.mlIndicesHandler = mlIndicesHandler;
    }

    public EncryptorImpl(String tenantId, String masterKey) {
        this.tenantMasterKeys = new ConcurrentHashMap<>();
        this.tenantMasterKeys.put(Objects.requireNonNullElse(tenantId, DEFAULT_TENANT_ID), masterKey);
    }

    @Override
    public void setMasterKey(String tenantId, String masterKey) {
        this.tenantMasterKeys.put(Objects.requireNonNullElse(tenantId, DEFAULT_TENANT_ID), masterKey);
    }

    @Override
    public String getMasterKey(String tenantId) {
        return tenantMasterKeys.get(Objects.requireNonNullElse(tenantId, DEFAULT_TENANT_ID));
    }

    @Override
    public String encrypt(String plainText, String tenantId) {
        initMasterKey(tenantId);
        final AwsCrypto crypto = AwsCrypto.builder().withCommitmentPolicy(CommitmentPolicy.RequireEncryptRequireDecrypt).build();
        JceMasterKey jceMasterKey = createJceMasterKey(tenantId);

        final CryptoResult<byte[], JceMasterKey> encryptResult = crypto
            .encryptData(jceMasterKey, plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encryptResult.getResult());
    }

    @Override
    public String decrypt(String encryptedText, String tenantId) {
        initMasterKey(tenantId);
        final AwsCrypto crypto = AwsCrypto.builder().withCommitmentPolicy(CommitmentPolicy.RequireEncryptRequireDecrypt).build();
        JceMasterKey jceMasterKey = createJceMasterKey(tenantId);

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

    private JceMasterKey createJceMasterKey(String tenantId) {
        byte[] bytes = Base64.getDecoder().decode(tenantMasterKeys.get(Objects.requireNonNullElse(tenantId, DEFAULT_TENANT_ID)));
        return JceMasterKey.getInstance(new SecretKeySpec(bytes, "AES"), "Custom", "", "AES/GCM/NOPADDING");
    }

    private void initMasterKey(String tenantId) {
        if (tenantMasterKeys.containsKey(Objects.requireNonNullElse(tenantId, DEFAULT_TENANT_ID))) {
            return;
        }
        AtomicReference<Exception> exceptionRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        mlIndicesHandler.initMLConfigIndex(createInitMLConfigIndexListener(exceptionRef, latch, tenantId));

        waitForLatch(latch, exceptionRef);
        checkMasterKeyInitialization(tenantId, exceptionRef);
    }

    private ActionListener<Boolean> createInitMLConfigIndexListener(
        AtomicReference<Exception> exceptionRef,
        CountDownLatch latch,
        String tenantId
    ) {
        return ActionListener
            .wrap(
                r -> handleInitMLConfigIndexSuccess(exceptionRef, latch, tenantId),
                e -> handleInitMLConfigIndexFailure(exceptionRef, latch, e)
            );
    }

    private void handleInitMLConfigIndexSuccess(AtomicReference<Exception> exceptionRef, CountDownLatch latch, String tenantId) {
        FetchSourceContext fetchSourceContext = new FetchSourceContext(true, Strings.EMPTY_ARRAY, Strings.EMPTY_ARRAY);
        GetDataObjectRequest getDataObjectRequest = createGetDataObjectRequest(tenantId, fetchSourceContext);

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            sdkClient
                .getDataObjectAsync(getDataObjectRequest, client.threadPool().executor("opensearch_ml_general"))
                .whenComplete(
                    (response, throwable) -> handleGetDataObjectResponse(tenantId, context, response, throwable, exceptionRef, latch)
                );
        }
    }

    private GetDataObjectRequest createGetDataObjectRequest(String tenantId, FetchSourceContext fetchSourceContext) {
        return GetDataObjectRequest
            .builder()
            .index(ML_CONFIG_INDEX)
            .id(MASTER_KEY)
            .tenantId(tenantId)
            .fetchSourceContext(fetchSourceContext)
            .build();
    }

    private void handleInitMLConfigIndexFailure(AtomicReference<Exception> exceptionRef, CountDownLatch latch, Exception e) {
        log.debug("Failed to init ML config index", e);
        exceptionRef.set(e);
        latch.countDown();
    }

    private void handleGetDataObjectResponse(
        String tenantId,
        ThreadContext.StoredContext context,
        GetDataObjectResponse response,
        Throwable throwable,
        AtomicReference<Exception> exceptionRef,
        CountDownLatch latch
    ) {
        context.restore();
        log.debug("Completed Get MASTER_KEY Request, for tenant id:{}", tenantId);

        if (throwable != null) {
            handleGetDataObjectFailure(throwable, exceptionRef, latch);
        } else {
            handleGetDataObjectSuccess(response, tenantId, exceptionRef, latch, context);
        }
    }

    private void handleGetDataObjectFailure(Throwable throwable, AtomicReference<Exception> exceptionRef, CountDownLatch latch) {
        Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
        log.debug("Failed to get ML encryption master key from config index", cause);
        exceptionRef.set(cause);
        latch.countDown();
    }

    private void handleGetDataObjectSuccess(
        GetDataObjectResponse response,
        String tenantId,
        AtomicReference<Exception> exceptionRef,
        CountDownLatch latch,
        ThreadContext.StoredContext context
    ) {
        try {
            GetResponse getMasterKeyResponse = response.parser() == null ? null : GetResponse.fromXContent(response.parser());
            if (getMasterKeyResponse != null && getMasterKeyResponse.isExists()) {
                this.tenantMasterKeys
                    .put(Objects.requireNonNullElse(tenantId, DEFAULT_TENANT_ID), (String) response.source().get(MASTER_KEY));
                log.info("ML encryption master key already initialized, no action needed");
                latch.countDown();
            } else {
                initializeNewMasterKey(tenantId, exceptionRef, latch, context);
            }
        } catch (Exception e) {
            log.debug("Failed to get ML encryption master key from config index", e);
            exceptionRef.set(e);
            latch.countDown();
        }
    }

    private void initializeNewMasterKey(
        String tenantId,
        AtomicReference<Exception> exceptionRef,
        CountDownLatch latch,
        ThreadContext.StoredContext context
    ) {
        final String generatedMasterKey = generateMasterKey();
        sdkClient
            .putDataObjectAsync(
                createPutDataObjectRequest(tenantId, generatedMasterKey),
                client.threadPool().executor("opensearch_ml_general")
            )
            .whenComplete(
                (putDataObjectResponse, throwable1) -> handlePutDataObjectResponse(
                    tenantId,
                    context,
                    throwable1,
                    exceptionRef,
                    latch,
                    generatedMasterKey
                )
            );
    }

    private PutDataObjectRequest createPutDataObjectRequest(String tenantId, String generatedMasterKey) {
        return PutDataObjectRequest
            .builder()
            .tenantId(tenantId)
            .index(ML_CONFIG_INDEX)
            .id(MASTER_KEY)
            .overwriteIfExists(false)
            .dataObject(ImmutableMap.of(MASTER_KEY, generatedMasterKey, CREATE_TIME_FIELD, Instant.now().toEpochMilli()))
            .build();
    }

    private void handlePutDataObjectResponse(
        String tenantId,
        ThreadContext.StoredContext context,
        Throwable throwable,
        AtomicReference<Exception> exceptionRef,
        CountDownLatch latch,
        String generatedMasterKey
    ) {
        context.restore();

        if (throwable != null) {
            handlePutDataObjectFailure(tenantId, context, throwable, exceptionRef, latch);
        } else {
            this.tenantMasterKeys.put(Objects.requireNonNullElse(tenantId, DEFAULT_TENANT_ID), generatedMasterKey);
            log.info("ML encryption master key initialized successfully");
            latch.countDown();
        }
    }

    private void handlePutDataObjectFailure(
        String tenantId,
        ThreadContext.StoredContext context,
        Throwable throwable,
        AtomicReference<Exception> exceptionRef,
        CountDownLatch latch
    ) {
        Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
        if (cause instanceof VersionConflictEngineException) {
            handleVersionConflict(tenantId, context, exceptionRef, latch);
        } else {
            log.debug("Failed to index ML encryption master key to config index", cause);
            exceptionRef.set(cause);
            latch.countDown();
        }
    }

    private void handleVersionConflict(
        String tenantId,
        ThreadContext.StoredContext context,
        AtomicReference<Exception> exceptionRef,
        CountDownLatch latch
    ) {
        sdkClient
            .getDataObjectAsync(
                createGetDataObjectRequest(tenantId, new FetchSourceContext(true, Strings.EMPTY_ARRAY, Strings.EMPTY_ARRAY)),
                client.threadPool().executor("opensearch_ml_general")
            )
            .whenComplete((response, throwable) -> handleVersionConflictResponse(context, response, throwable, exceptionRef, latch));
    }

    private void handleVersionConflictResponse(
        ThreadContext.StoredContext context,
        GetDataObjectResponse response1,
        Throwable throwable2,
        AtomicReference<Exception> exceptionRef,
        CountDownLatch latch
    ) {
        context.restore();
        log.debug("Completed Get config item");

        if (throwable2 != null) {
            Exception cause1 = SdkClientUtils.unwrapAndConvertToException(throwable2);
            log.debug("Failed to get ML encryption master key from config index", cause1);
            exceptionRef.set(cause1);
            latch.countDown();
        } else {
            handleGetDataObjectSuccess(response1, null, exceptionRef, latch, context); // Tenant ID is not used here
        }
    }

    private void waitForLatch(CountDownLatch latch, AtomicReference<Exception> exceptionRef) {
        try {
            // TODO: we need to find a better way to depend on the listener rather than waiting for a fixed time
            // sometimes it may be take more than 1 seconds in multi-tenancy case where we need to
            // create index, create a master key and then perform the prediction.
            latch.await(2, SECONDS);
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
        if (tenantMasterKeys.get(Objects.requireNonNullElse(tenantId, DEFAULT_TENANT_ID)) == null) {
            throw new ResourceNotFoundException(MASTER_KEY_NOT_READY_ERROR);
        }
    }
}
