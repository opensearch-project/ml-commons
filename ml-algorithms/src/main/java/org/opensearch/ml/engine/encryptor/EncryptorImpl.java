/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.encryptor;

import static org.opensearch.ml.common.CommonValue.MASTER_KEY;
import static org.opensearch.ml.common.CommonValue.ML_CONFIG_INDEX;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.MLConfig.CREATE_TIME_FIELD;
import static org.opensearch.ml.common.utils.StringUtils.hashString;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

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

import lombok.extern.log4j.Log4j2;

@Log4j2
public class EncryptorImpl implements Encryptor {

    public static final String MASTER_KEY_NOT_READY_ERROR =
        "The ML encryption master key has not been initialized yet. Please retry after waiting for 10 seconds.";
    private ClusterService clusterService;
    private Client client;
    private SdkClient sdkClient;
    private final Map<String, String> tenantMasterKeys;
    private final Map<String, List<ActionListener<Boolean>>> tenantWaitingListenerMap;
    private MLIndicesHandler mlIndicesHandler;

    // concurrent map can't have null as a key. This is to support single tenancy
    // assigning some random string so that it can't be duplicate
    public static final String DEFAULT_TENANT_ID = "03000200-0400-0500-0006-000700080009";

    public EncryptorImpl(ClusterService clusterService, Client client, SdkClient sdkClient, MLIndicesHandler mlIndicesHandler) {
        this.tenantMasterKeys = new ConcurrentHashMap<>();
        this.tenantWaitingListenerMap = new ConcurrentHashMap<>();
        this.clusterService = clusterService;
        this.client = client;
        this.sdkClient = sdkClient;
        this.mlIndicesHandler = mlIndicesHandler;
    }

    public EncryptorImpl(String tenantId, String masterKey) {
        this.tenantMasterKeys = new ConcurrentHashMap<>();
        this.tenantWaitingListenerMap = new ConcurrentHashMap<>();
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
    public void encrypt(String plainText, String tenantId, ActionListener<String> listener) {
        ActionListener<Boolean> initListener = ActionListener.wrap(result -> {
            final AwsCrypto crypto = AwsCrypto.builder().withCommitmentPolicy(CommitmentPolicy.RequireEncryptRequireDecrypt).build();
            JceMasterKey jceMasterKey = createJceMasterKey(tenantId);
            final CryptoResult<byte[], JceMasterKey> encryptResult = crypto
                .encryptData(jceMasterKey, plainText.getBytes(StandardCharsets.UTF_8));
            listener.onResponse(Base64.getEncoder().encodeToString(encryptResult.getResult()));
        }, listener::onFailure);
        initMasterKey(tenantId, initListener);
    }

    @Override
    public void decrypt(String encryptedText, String tenantId, ActionListener<String> listener) {
        ActionListener<Boolean> initListener = ActionListener.wrap(result -> {
            final AwsCrypto crypto = AwsCrypto.builder().withCommitmentPolicy(CommitmentPolicy.RequireEncryptRequireDecrypt).build();
            JceMasterKey jceMasterKey = createJceMasterKey(tenantId);
            final CryptoResult<byte[], JceMasterKey> decryptedResult = crypto
                .decryptData(jceMasterKey, Base64.getDecoder().decode(encryptedText));
            listener.onResponse(new String(decryptedResult.getResult()));
        }, listener::onFailure);
        initMasterKey(tenantId, initListener);
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

    private void initMasterKey(String tenantId, ActionListener<Boolean> listener) {
        tenantId = Objects.requireNonNullElse(tenantId, DEFAULT_TENANT_ID);
        if (tenantMasterKeys.containsKey(tenantId)) {
            log.info("Master key is already available for tenant {}", tenantId);
            listener.onResponse(true);
            return;
        }

        List<ActionListener<Boolean>> waitingListeners = tenantWaitingListenerMap.computeIfAbsent(tenantId, k -> new ArrayList<>());
        synchronized (waitingListeners) {
            if (tenantMasterKeys.containsKey(tenantId)) {
                log.info("Master key generation is handled by other thread for tenant {}", tenantId);
                listener.onResponse(true);
                return;
            }
            boolean isFirstThread = waitingListeners.isEmpty();
            waitingListeners.add(listener);
            if (!isFirstThread) {
                log.info("Master key generation is already initiated by other thread for tenant {}", tenantId);
                return;
            }
        }

        log.info("Generating master key for tenant : {}", tenantId);
        String masterKeyId = MASTER_KEY + "_" + hashString(tenantId);
        mlIndicesHandler.initMLConfigIndex(createInitMLConfigIndexListener(tenantId, masterKeyId));
    }

    private void handleSuccess(String tenantId, String masterKey) {
        this.tenantMasterKeys.put(tenantId, masterKey);
        log.info("ML encryption master key initialized for tenant {}, no action needed", tenantId);
        List<ActionListener<Boolean>> waitingListeners = tenantWaitingListenerMap.remove(tenantId);
        synchronized (waitingListeners) {
            waitingListeners.forEach(listener -> listener.onResponse(true));
            waitingListeners.clear();
        }
    }

    private void handleError(String tenantId, Exception exception) {
        List<ActionListener<Boolean>> waitingListeners = tenantWaitingListenerMap.remove(tenantId);
        synchronized (waitingListeners) {
            if (exception != null) {
                log.debug("Failed to init master key for tenant {}", tenantId, exception);
                if (exception instanceof RuntimeException) {
                    waitingListeners.forEach(listener -> listener.onFailure(exception));
                } else {
                    waitingListeners.forEach(listener -> listener.onFailure(new MLException(exception)));
                }
            }
            waitingListeners.clear();
        }
    }

    private ActionListener<Boolean> createInitMLConfigIndexListener(
        String tenantId,
        String masterKeyId

    ) {
        return ActionListener
            .wrap(
                r -> handleInitMLConfigIndexSuccess(tenantId, masterKeyId),
                e -> handleInitMLConfigIndexFailure(tenantId, masterKeyId, e)
            );
    }

    private void handleInitMLConfigIndexSuccess(String tenantId, String masterKeyId) {
        FetchSourceContext fetchSourceContext = new FetchSourceContext(true, Strings.EMPTY_ARRAY, Strings.EMPTY_ARRAY);
        GetDataObjectRequest getDataObjectRequest = createGetDataObjectRequest(tenantId, fetchSourceContext);

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            sdkClient
                .getDataObjectAsync(getDataObjectRequest)
                .whenComplete((response, throwable) -> handleGetDataObjectResponse(tenantId, masterKeyId, context, response, throwable));
        }
    }

    private void handleInitMLConfigIndexFailure(String tenantId, String masterKeyId, Exception e) {
        log.debug("Failed to init ML config index", e);
        handleError(tenantId, new RuntimeException("No response to create ML Config index"));
    }

    private void handleGetDataObjectResponse(
        String tenantId,
        String masterKeyId,
        ThreadContext.StoredContext context,
        GetDataObjectResponse response,
        Throwable throwable
    ) {
        log.debug("Completed Get MASTER_KEY Request, for tenant id:{}", tenantId);

        if (throwable != null) {
            handleGetDataObjectFailure(tenantId, throwable);
        } else {
            handleGetDataObjectSuccess(response, tenantId, masterKeyId, context);
        }
        context.restore();
    }

    private void handleGetDataObjectFailure(String tenantId, Throwable throwable) {
        Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable, OpenSearchStatusException.class);
        log.debug("Failed to get ML encryption master key from config index", cause);
        handleError(tenantId, cause);
    }

    private void handleGetDataObjectSuccess(
        GetDataObjectResponse response,
        String tenantId,
        String masterKeyId,
        ThreadContext.StoredContext context
    ) {
        try {
            GetResponse getMasterKeyResponse = response.parser() == null ? null : GetResponse.fromXContent(response.parser());
            if (getMasterKeyResponse != null && getMasterKeyResponse.isExists()) {
                Map<String, Object> source = getMasterKeyResponse.getSourceAsMap();
                if (source != null) {
                    Object keyValue = source.get(MASTER_KEY);
                    if (keyValue instanceof String) {
                        handleSuccess(tenantId, (String) keyValue);
                    } else {
                        log.error("Master key not found or not a string for tenantId: {}, masterKeyId: {}", tenantId, masterKeyId);
                        handleError(tenantId, new ResourceNotFoundException(MASTER_KEY_NOT_READY_ERROR));
                    }
                } else {
                    log.error("Master key not found or not a string for tenantId: {}, masterKeyId: {}", tenantId, masterKeyId);
                    handleError(tenantId, new ResourceNotFoundException(MASTER_KEY_NOT_READY_ERROR));
                }
            } else {
                initializeNewMasterKey(tenantId, masterKeyId, context);
            }
        } catch (Exception e) {
            log.debug("Failed to get ML encryption master key from config index", e);
            handleError(tenantId, e);
        }
    }

    private void initializeNewMasterKey(String tenantId, String masterKeyId, ThreadContext.StoredContext context) {
        final String generatedMasterKey = generateMasterKey();
        sdkClient
            .putDataObjectAsync(createPutDataObjectRequest(tenantId, masterKeyId, generatedMasterKey))
            .whenComplete((putDataObjectResponse, throwable1) -> {
                try {
                    handlePutDataObjectResponse(tenantId, masterKeyId, context, putDataObjectResponse, throwable1, generatedMasterKey);
                } catch (IOException e) {
                    log.debug("Failed to index ML encryption master key to config index", e);
                    handleError(tenantId, e);
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
            .dataObject(Map.of(MASTER_KEY, generatedMasterKey, CREATE_TIME_FIELD, Instant.now().toEpochMilli(), TENANT_ID_FIELD, tenantId))
            .build();
    }

    private void handlePutDataObjectResponse(
        String tenantId,
        String masterKeyId,
        ThreadContext.StoredContext context,
        PutDataObjectResponse putDataObjectResponse,
        Throwable throwable,
        String generatedMasterKey
    ) throws IOException {
        context.restore();

        if (throwable != null) {
            handlePutDataObjectFailure(tenantId, masterKeyId, context, throwable);
        } else {
            IndexResponse indexResponse = IndexResponse.fromXContent(putDataObjectResponse.parser());
            log.info("Master key creation result: {}, Master key id: {}", indexResponse.getResult(), indexResponse.getId());
            handleSuccess(tenantId, generatedMasterKey);
        }
    }

    private void handlePutDataObjectFailure(String tenantId, String masterKeyId, ThreadContext.StoredContext context, Throwable throwable) {
        Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable, OpenSearchStatusException.class);
        if (cause instanceof VersionConflictEngineException
            || (cause instanceof OpenSearchException && ((OpenSearchException) cause).status() == RestStatus.CONFLICT)) {
            handleVersionConflict(tenantId, masterKeyId, context);
        } else {
            log.debug("Failed to index ML encryption master key to config index", cause);
            handleError(tenantId, cause);
        }
    }

    private void handleVersionConflict(String tenantId, String masterKeyId, ThreadContext.StoredContext context) {
        sdkClient
            .getDataObjectAsync(
                createGetDataObjectRequest(tenantId, new FetchSourceContext(true, Strings.EMPTY_ARRAY, Strings.EMPTY_ARRAY))
            )
            .whenComplete((response, throwable) -> {
                try {
                    handleVersionConflictResponse(tenantId, masterKeyId, context, response, throwable);
                } catch (IOException e) {
                    log.debug("Failed to get ML encryption master key from config index", e);
                    handleError(tenantId, e);
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
        Throwable throwable2
    ) throws IOException {
        context.restore();
        log.debug("Completed Get config item");

        if (throwable2 != null) {
            Exception cause1 = SdkClientUtils.unwrapAndConvertToException(throwable2, OpenSearchStatusException.class);
            log.debug("Failed to get ML encryption master key from config index", cause1);
            handleError(tenantId, new ResourceNotFoundException(MASTER_KEY_NOT_READY_ERROR));
        } else {
            GetResponse getMasterKeyResponse = response1.parser() == null ? null : GetResponse.fromXContent(response1.parser());
            if (getMasterKeyResponse != null && getMasterKeyResponse.isExists()) {
                Map<String, Object> source = getMasterKeyResponse.getSourceAsMap();
                if (source != null) {
                    Object keyValue = source.get(MASTER_KEY);
                    if (keyValue instanceof String) {
                        handleSuccess(tenantId, (String) keyValue);
                    } else {
                        log.error("Master key not found or not a string for tenantId: {}, masterKeyId: {}", tenantId, masterKeyId);
                        handleError(tenantId, new ResourceNotFoundException(MASTER_KEY_NOT_READY_ERROR));
                    }
                } else {
                    log.error("Master key not found or not a string for tenantId: {}, masterKeyId: {}", tenantId, masterKeyId);
                    handleError(tenantId, new ResourceNotFoundException(MASTER_KEY_NOT_READY_ERROR));
                }
            } else {
                handleError(tenantId, new ResourceNotFoundException(MASTER_KEY_NOT_READY_ERROR));
            }
        }
    }
}
