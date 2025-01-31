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
import static org.opensearch.ml.common.utils.StringUtils.hashString;

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

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.DocWriteRequest;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.engine.VersionConflictEngineException;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.engine.indices.MLIndicesHandler;

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
    private final Map<String, String> tenantMasterKeys;
    private MLIndicesHandler mlIndicesHandler;

    // concurrent map can't have null as a key. This is to support single tenancy
    // assigning some random string so that it can't be duplicate
    public static final String DEFAULT_TENANT_ID = "03000200-0400-0500-0006-000700080009";

    public EncryptorImpl(ClusterService clusterService, Client client, MLIndicesHandler mlIndicesHandler) {
        this.tenantMasterKeys = new ConcurrentHashMap<>();
        this.clusterService = clusterService;
        this.client = client;

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
        mlIndicesHandler.initMLConfigIndex(ActionListener.wrap(r -> {
            if (!r) {
                exceptionRef.set(new RuntimeException("No response to create ML Config index"));
                latch.countDown();
            } else {
                String masterKeyId = MASTER_KEY;
                if (tenantId != null) {
                    masterKeyId = MASTER_KEY + "_" + hashString(tenantId);
                }
                final String MASTER_KEY_ID = masterKeyId;
                GetRequest getRequest = new GetRequest(ML_CONFIG_INDEX).id(MASTER_KEY_ID);
                try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                    client.get(getRequest, ActionListener.wrap(getResponse -> {
                        if (getResponse == null || !getResponse.isExists()) {
                            IndexRequest indexRequest = new IndexRequest(ML_CONFIG_INDEX).id(MASTER_KEY_ID);
                            final String generatedMasterKey = generateMasterKey();

                            ImmutableMap.Builder<String, Object> mapBuilder = ImmutableMap.builder();
                            mapBuilder.put(MASTER_KEY_ID, generatedMasterKey);
                            mapBuilder.put(CREATE_TIME_FIELD, Instant.now().toEpochMilli());
                            if (tenantId != null) {
                                mapBuilder.put(TENANT_ID_FIELD, tenantId);
                            }
                            indexRequest.source(mapBuilder.build());
                            indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
                            indexRequest.opType(DocWriteRequest.OpType.CREATE);
                            client.index(indexRequest, ActionListener.wrap(indexResponse -> {
                                this.tenantMasterKeys.put(Objects.requireNonNullElse(tenantId, DEFAULT_TENANT_ID), generatedMasterKey);
                                log.info("ML encryption master key initialized successfully");
                                latch.countDown();
                            }, e -> {

                                if (ExceptionUtils.getRootCause(e) instanceof VersionConflictEngineException) {
                                    GetRequest getMasterKeyRequest = new GetRequest(ML_CONFIG_INDEX).id(MASTER_KEY_ID);
                                    try (
                                        ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()
                                    ) {
                                        client.get(getMasterKeyRequest, ActionListener.wrap(getMasterKeyResponse -> {
                                            if (getMasterKeyResponse != null && getMasterKeyResponse.isExists()) {
                                                this.tenantMasterKeys
                                                    .put(
                                                        Objects.requireNonNullElse(tenantId, DEFAULT_TENANT_ID),
                                                        (String) getMasterKeyResponse.getSourceAsMap().get(MASTER_KEY_ID)
                                                    );
                                                log.info("ML encryption master key already initialized, no action needed");
                                                latch.countDown();
                                            } else {
                                                exceptionRef.set(new ResourceNotFoundException(MASTER_KEY_NOT_READY_ERROR));
                                                latch.countDown();
                                            }
                                        }, error -> {
                                            log.debug("Failed to get ML encryption master key", e);
                                            exceptionRef.set(error);
                                            latch.countDown();
                                        }));
                                    }
                                } else {
                                    log.debug("Failed to index ML encryption master key", e);
                                    exceptionRef.set(e);
                                    latch.countDown();
                                }
                            }));
                        } else {
                            final String masterKey = (String) getResponse.getSourceAsMap().get(MASTER_KEY_ID);
                            this.tenantMasterKeys.put(Objects.requireNonNullElse(tenantId, DEFAULT_TENANT_ID), masterKey);
                            log.info("ML encryption master key already initialized, no action needed");
                            latch.countDown();
                        }
                    }, e -> {
                        log.debug("Failed to get ML encryption master key from config index", e);
                        exceptionRef.set(e);
                        latch.countDown();
                    }));
                }
            }
        }, e -> {
            log.debug("Failed to init ML config index", e);
            exceptionRef.set(e);
            latch.countDown();
        }));

        try {
            boolean completed = latch.await(3, SECONDS);
            if (!completed) {
                throw new MLException("Fetching master key timed out.");
            }
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }

        if (exceptionRef.get() != null) {
            log.debug("Failed to init master key", exceptionRef.get());
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
