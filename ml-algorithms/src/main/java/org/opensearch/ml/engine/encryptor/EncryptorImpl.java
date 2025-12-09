/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.encryptor;

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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
import com.google.common.annotations.VisibleForTesting;
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
    }

    // Package-private constructor for testing with custom TTL
    @VisibleForTesting
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
    public void encrypt(List<String> plainTexts, String tenantId, ActionListener<List<String>> listener) {
        ActionListener<String> masterKeyInitiatedListener = ActionListener.wrap(r -> {
            if (Strings.isNullOrEmpty(r)) {
                listener.onFailure(new OpenSearchStatusException(MASTER_KEY_NOT_READY_ERROR, RestStatus.INTERNAL_SERVER_ERROR));
            } else {
                final AwsCrypto crypto = AwsCrypto.builder().withCommitmentPolicy(CommitmentPolicy.RequireEncryptRequireDecrypt).build();
                JceMasterKey jceMasterKey = createJceMasterKey(tenantId);
                List<String> encryptedResults = new ArrayList<>();
                for (String plainText : plainTexts) {
                    final CryptoResult<byte[], JceMasterKey> encryptResult = crypto
                        .encryptData(jceMasterKey, plainText.getBytes(StandardCharsets.UTF_8));
                    encryptedResults.add(Base64.getEncoder().encodeToString(encryptResult.getResult()));
                }
                listener.onResponse(encryptedResults);
            }
        }, e -> {
            log.error("Failed to encrypt the credentials in connector body!", e);
            if (e instanceof RuntimeException) {
                listener.onFailure(e);
            } else {
                listener.onFailure(new MLException(e));
            }
        });
        initMasterKey(tenantId, masterKeyInitiatedListener);
    }

    @Override
    public void decrypt(List<String> decryptTexts, String tenantId, ActionListener<List<String>> listener) {
        ActionListener<String> masterKeyInitiatedListener = ActionListener.wrap(r -> {
            if (Strings.isNullOrEmpty(r)) {
                listener.onFailure(new OpenSearchStatusException(MASTER_KEY_NOT_READY_ERROR, RestStatus.INTERNAL_SERVER_ERROR));
            } else {
                final AwsCrypto crypto = AwsCrypto.builder().withCommitmentPolicy(CommitmentPolicy.RequireEncryptRequireDecrypt).build();
                JceMasterKey jceMasterKey = createJceMasterKey(tenantId);
                List<String> decryptedTextList = new ArrayList<>();
                for (String decryptText : decryptTexts) {
                    final CryptoResult<byte[], JceMasterKey> decryptedResult = crypto
                        .decryptData(jceMasterKey, Base64.getDecoder().decode(decryptText));
                    decryptedTextList.add(new String(decryptedResult.getResult()));
                }
                listener.onResponse(decryptedTextList);
            }
        }, e -> {
            log.error("Failed to decrypt the credentials in connector body!", e);
            if (e instanceof RuntimeException) {
                listener.onFailure(e);
            } else {
                listener.onFailure(new MLException(e));
            }
        });
        initMasterKey(tenantId, masterKeyInitiatedListener);
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

    private void initMasterKey(String tenantId, ActionListener<String> listener) {
        String masterKey = tenantMasterKeys.get(Objects.requireNonNullElse(tenantId, DEFAULT_TENANT_ID));
        if (masterKey != null && !Strings.isNullOrEmpty(masterKey)) {
            log.debug("Fetched master key from cache, tenantId is: {}", tenantId);
            listener.onResponse(masterKey);
            return;
        }
        String masterKeyId = Optional.ofNullable(tenantId).map(x -> MASTER_KEY + "_" + hashString(x)).orElse(MASTER_KEY);
        ActionListener<Boolean> mlConfigIndexInitiatedListener = ActionListener.wrap(r -> {
            FetchSourceContext fetchSourceContext = new FetchSourceContext(true, Strings.EMPTY_ARRAY, Strings.EMPTY_ARRAY);
            GetDataObjectRequest getDataObjectRequest = createGetDataObjectRequest(tenantId, fetchSourceContext);

            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                sdkClient
                    .getDataObjectAsync(getDataObjectRequest)
                    .whenComplete(
                        (response, throwable) -> handleGetDataObjectResponse(tenantId, masterKeyId, context, response, throwable, listener)
                    );
            }
        }, e -> {
            log.error("Failed to init ML config index for tenant {}", tenantId, e);
            listener.onFailure(new RuntimeException("No response to create ML Config index"));
        });
        mlIndicesHandler.initMLConfigIndex(mlConfigIndexInitiatedListener);
    }

    private void handleGetDataObjectResponse(
        String tenantId,
        String masterKeyId,
        ThreadContext.StoredContext context,
        GetDataObjectResponse response,
        Throwable throwable,
        ActionListener<String> listener
    ) {
        log.debug("Completed Get MASTER_KEY Request, for tenant id:{}", tenantId);

        if (throwable != null) {
            handleGetDataObjectFailure(throwable, listener);
        } else {
            handleGetDataObjectSuccess(response, tenantId, masterKeyId, context, listener);
        }
        context.restore();
    }

    private void handleGetDataObjectFailure(Throwable throwable, ActionListener<String> listener) {
        Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable, OpenSearchStatusException.class);
        log.debug("Failed to get ML encryption master key from config index", cause);
        listener.onFailure(cause);
    }

    private void handleGetDataObjectSuccess(
        GetDataObjectResponse response,
        String tenantId,
        String masterKeyId,
        ThreadContext.StoredContext context,
        ActionListener<String> listener
    ) {
        try {
            GetResponse getMasterKeyResponse = response.parser() == null ? null : GetResponse.fromXContent(response.parser());
            if (getMasterKeyResponse != null && getMasterKeyResponse.isExists()) {
                Map<String, Object> source = getMasterKeyResponse.getSourceAsMap();
                if (source != null) {
                    Object keyValue = source.get(MASTER_KEY);
                    if (keyValue instanceof String) {
                        this.tenantMasterKeys.put(Objects.requireNonNullElse(tenantId, DEFAULT_TENANT_ID), (String) keyValue);
                        listener.onResponse((String) keyValue);
                        log.info("ML encryption master key already initialized, no action needed");
                    } else {
                        log.error(getErrorMsg(tenantId, masterKeyId));
                        listener.onFailure(new ResourceNotFoundException(MASTER_KEY_NOT_READY_ERROR));
                    }
                } else {
                    log.error(getErrorMsg(tenantId, masterKeyId));
                    listener.onFailure(new ResourceNotFoundException(MASTER_KEY_NOT_READY_ERROR));
                }
            } else {
                log.debug("Starting to initialize master key for tenant: {}!", tenantId);
                initializeNewMasterKey(tenantId, masterKeyId, context, listener);
            }
        } catch (Exception e) {
            log.debug("Failed to get ML encryption master key from config index", e);
            listener.onFailure(e);
        }
    }

    private void initializeNewMasterKey(
        String tenantId,
        String masterKeyId,
        ThreadContext.StoredContext context,
        ActionListener<String> listener
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
                        generatedMasterKey,
                        listener
                    );
                } catch (IOException e) {
                    log.debug("Failed to index ML encryption master key to config index", e);
                    listener.onFailure(e);
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
        String generatedMasterKey,
        ActionListener<String> listener
    ) throws IOException {
        context.restore();

        if (throwable != null) {
            handlePutDataObjectFailure(tenantId, masterKeyId, context, throwable, listener);
        } else {
            System.out.println("put data object response received!");
            IndexResponse indexResponse = IndexResponse.fromXContent(putDataObjectResponse.parser());
            log.info("Master key creation result: {}, Master key id: {}", indexResponse.getResult(), indexResponse.getId());
            this.tenantMasterKeys.put(Objects.requireNonNullElse(tenantId, DEFAULT_TENANT_ID), generatedMasterKey);
            log.info("ML encryption master key initialized successfully");
            listener.onResponse(generatedMasterKey);
        }
    }

    private void handlePutDataObjectFailure(
        String tenantId,
        String masterKeyId,
        ThreadContext.StoredContext context,
        Throwable throwable,
        ActionListener<String> listener
    ) {
        Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable, OpenSearchStatusException.class);
        if (cause instanceof VersionConflictEngineException
            || (cause instanceof OpenSearchException && ((OpenSearchException) cause).status() == RestStatus.CONFLICT)) {
            handleVersionConflict(tenantId, masterKeyId, context, listener);
        } else {
            log.debug("Failed to index ML encryption master key to config index", cause);
            listener.onFailure(cause);
        }
    }

    private void handleVersionConflict(
        String tenantId,
        String masterKeyId,
        ThreadContext.StoredContext context,
        ActionListener<String> listener
    ) {
        sdkClient
            .getDataObjectAsync(
                createGetDataObjectRequest(tenantId, new FetchSourceContext(true, Strings.EMPTY_ARRAY, Strings.EMPTY_ARRAY))
            )
            .whenComplete((response, throwable) -> {
                try {
                    handleVersionConflictResponse(tenantId, masterKeyId, context, response, throwable, listener);
                } catch (IOException e) {
                    log.debug("Failed to get ML encryption master key from config index", e);
                    listener.onFailure(e);
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
        ActionListener<String> listener
    ) throws IOException {
        context.restore();
        log.debug("Completed Get config item");

        if (throwable2 != null) {
            Exception cause1 = SdkClientUtils.unwrapAndConvertToException(throwable2, OpenSearchStatusException.class);
            log.debug("Failed to get ML encryption master key from config index", throwable2);
            listener.onFailure(new ResourceNotFoundException(MASTER_KEY_NOT_READY_ERROR));
        } else {
            GetResponse getMasterKeyResponse = response1.parser() == null ? null : GetResponse.fromXContent(response1.parser());
            if (getMasterKeyResponse != null && getMasterKeyResponse.isExists()) {
                Map<String, Object> source = getMasterKeyResponse.getSourceAsMap();
                if (source != null) {
                    Object keyValue = source.get(MASTER_KEY);
                    if (keyValue instanceof String) {
                        this.tenantMasterKeys.put(Objects.requireNonNullElse(tenantId, DEFAULT_TENANT_ID), (String) keyValue);
                        log.info("ML encryption master key already initialized, no action needed");
                        listener.onResponse((String) keyValue);
                    } else {
                        log.error(getErrorMsg(tenantId, masterKeyId));
                        listener.onFailure(new ResourceNotFoundException(MASTER_KEY_NOT_READY_ERROR));
                    }
                } else {
                    log.error(getErrorMsg(tenantId, masterKeyId));
                    listener.onFailure(new ResourceNotFoundException(MASTER_KEY_NOT_READY_ERROR));
                }
            } else {
                exceptionRef.set(new ResourceNotFoundException(MASTER_KEY_NOT_READY_ERROR));
                latch.countDown();
            }
        }
    }

    private String getErrorMsg(String tenantId, String masterKeyId) {
        return String.format(Locale.ROOT, "Master key not found or not a string for tenantId: %s, masterKeyId: %s", tenantId, masterKeyId);
    }
}
