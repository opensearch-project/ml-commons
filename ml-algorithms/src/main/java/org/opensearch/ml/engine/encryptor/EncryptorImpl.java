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
    private volatile String masterKey;
    private MLIndicesHandler mlIndicesHandler;

    public EncryptorImpl(ClusterService clusterService, Client client, MLIndicesHandler mlIndicesHandler) {
        this.masterKey = null;
        this.clusterService = clusterService;
        this.client = client;
        this.mlIndicesHandler = mlIndicesHandler;
    }

    public EncryptorImpl(String masterKey) {
        this.masterKey = masterKey;
    }

    @Override
    public void setMasterKey(String masterKey) {
        this.masterKey = masterKey;
    }

    @Override
    public String getMasterKey() {
        return masterKey;
    }

    @Override
    public String encrypt(String plainText) {
        initMasterKey();
        final AwsCrypto crypto = AwsCrypto.builder().withCommitmentPolicy(CommitmentPolicy.RequireEncryptRequireDecrypt).build();
        byte[] bytes = Base64.getDecoder().decode(masterKey);
        // https://github.com/aws/aws-encryption-sdk-java/issues/1879
        JceMasterKey jceMasterKey = JceMasterKey.getInstance(new SecretKeySpec(bytes, "AES"), "Custom", "", "AES/GCM/NOPADDING");

        final CryptoResult<byte[], JceMasterKey> encryptResult = crypto
            .encryptData(jceMasterKey, plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encryptResult.getResult());
    }

    @Override
    public String decrypt(String encryptedText) {
        initMasterKey();
        final AwsCrypto crypto = AwsCrypto.builder().withCommitmentPolicy(CommitmentPolicy.RequireEncryptRequireDecrypt).build();

        byte[] bytes = Base64.getDecoder().decode(masterKey);
        JceMasterKey jceMasterKey = JceMasterKey.getInstance(new SecretKeySpec(bytes, "AES"), "Custom", "", "AES/GCM/NOPADDING");

        final CryptoResult<byte[], JceMasterKey> decryptedResult = crypto
            .decryptData(jceMasterKey, Base64.getDecoder().decode(encryptedText));
        return new String(decryptedResult.getResult());
    }

    @Override
    public String generateMasterKey() {
        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        String base64Key = Base64.getEncoder().encodeToString(keyBytes);
        return base64Key;
    }

    private void initMasterKey() {
        if (masterKey != null) {
            return;
        }
        AtomicReference<Exception> exceptionRef = new AtomicReference<>();

        CountDownLatch latch = new CountDownLatch(1);
        mlIndicesHandler.initMLConfigIndex(ActionListener.wrap(r -> {
            if (!r) {
                exceptionRef.set(new RuntimeException("No response to create ML Config index"));
                latch.countDown();
            } else {
                GetRequest getRequest = new GetRequest(ML_CONFIG_INDEX).id(MASTER_KEY);
                try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                    client.get(getRequest, ActionListener.wrap(getResponse -> {
                        if (getResponse == null || !getResponse.isExists()) {
                            IndexRequest indexRequest = new IndexRequest(ML_CONFIG_INDEX).id(MASTER_KEY);
                            final String generatedMasterKey = generateMasterKey();
                            indexRequest
                                .source(ImmutableMap.of(MASTER_KEY, generatedMasterKey, CREATE_TIME_FIELD, Instant.now().toEpochMilli()));
                            indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
                            indexRequest.opType(DocWriteRequest.OpType.CREATE);
                            client.index(indexRequest, ActionListener.wrap(indexResponse -> {
                                this.masterKey = generatedMasterKey;
                                log.info("ML encryption master key initialized successfully");
                                latch.countDown();
                            }, e -> {

                                if (ExceptionUtils.getRootCause(e) instanceof VersionConflictEngineException) {
                                    GetRequest getMasterKeyRequest = new GetRequest(ML_CONFIG_INDEX).id(MASTER_KEY);
                                    try (
                                        ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()
                                    ) {
                                        client.get(getMasterKeyRequest, ActionListener.wrap(getMasterKeyResponse -> {
                                            if (getMasterKeyResponse != null && getMasterKeyResponse.isExists()) {
                                                final String masterKey = (String) getMasterKeyResponse.getSourceAsMap().get(MASTER_KEY);
                                                this.masterKey = masterKey;
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
                            final String masterKey = (String) getResponse.getSourceAsMap().get(MASTER_KEY);
                            this.masterKey = masterKey;
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
        if (masterKey == null) {
            throw new ResourceNotFoundException(MASTER_KEY_NOT_READY_ERROR);
        }
    }

}
