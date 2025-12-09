/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.opensearch.ml.common.connector.ConnectorAction.ActionType.PREDICT;
import static org.opensearch.ml.common.settings.MLCommonsSettings.REMOTE_METADATA_GLOBAL_TENANT_ID;

import java.util.Map;

import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.TokenBucket;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLIndex;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction.ActionType;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.MLGuard;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.engine.MLEngineClassLoader;
import org.opensearch.ml.engine.Predictable;
import org.opensearch.ml.engine.annotation.Function;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.script.ScriptService;
import org.opensearch.transport.TransportChannel;
import org.opensearch.transport.client.Client;

import com.google.common.annotations.VisibleForTesting;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Function(FunctionName.REMOTE)
public class RemoteModel implements Predictable {

    public static final String CLUSTER_SERVICE = "cluster_service";
    public static final String SCRIPT_SERVICE = "script_service";
    public static final String CLIENT = "client";
    public static final String XCONTENT_REGISTRY = "xcontent_registry";
    public static final String RATE_LIMITER = "rate_limiter";
    public static final String USER_RATE_LIMITER_MAP = "user_rate_limiter_map";
    public static final String GUARDRAILS = "guardrails";
    public static final String CONNECTOR_PRIVATE_IP_ENABLED = "connectorPrivateIpEnabled";
    public static final String SDK_CLIENT = "sdk_client";
    public static final String SETTINGS = "settings";

    private RemoteConnectorExecutor connectorExecutor;

    @VisibleForTesting
    RemoteConnectorExecutor getConnectorExecutor() {
        return this.connectorExecutor;
    }

    @Override
    public MLOutput predict(MLInput mlInput, MLModel model) {
        throw new IllegalArgumentException(
            "Model not ready yet. Please run this first: POST /_plugins/_ml/models/" + model.getModelId() + "/_deploy"
        );
    }

    @Override
    public void asyncPredict(MLInput mlInput, ActionListener<MLTaskResponse> actionListener, TransportChannel channel) {
        if (!isModelReady()) {
            actionListener
                .onFailure(
                    new IllegalArgumentException("Model not ready yet. Please run this first: POST /_plugins/_ml/models/<model_id>/_deploy")
                );
            return;
        }
        try {
            ActionType actionType = null;
            if (mlInput.getInputDataset() instanceof RemoteInferenceInputDataSet) {
                actionType = ((RemoteInferenceInputDataSet) mlInput.getInputDataset()).getActionType();
            }
            actionType = actionType == null ? ActionType.PREDICT : actionType;
            connectorExecutor.executeAction(actionType.toString(), mlInput, actionListener, channel);
        } catch (RuntimeException e) {
            log.error("Failed to call remote model.", e);
            actionListener.onFailure(e);
        } catch (Throwable e) {
            log.error("Failed to call remote model.", e);
            actionListener.onFailure(new MLException(e));
        }
    }

    @Override
    public void close() {
        this.connectorExecutor = null;
    }

    @Override
    public boolean isModelReady() {
        return connectorExecutor != null;
    }

    @Override
    public void initModelAsync(MLModel model, Map<String, Object> params, Encryptor encryptor, ActionListener<Predictable> listener) {
        SdkClient sdkClient = (SdkClient) params.get(SDK_CLIENT);
        sdkClient.isGlobalResource(MLIndex.MODEL.getIndexName(), model.getModelId()).whenComplete((isGlobalResource, throwable) -> {
            if (throwable != null) {
                log.error("Failed to init remote model.", throwable);
                listener.onFailure(new MLException(throwable));
            } else {
                innerInitModelAsync(model, params, encryptor, isGlobalResource, listener);
            }
        });
    }

    private void innerInitModelAsync(
        MLModel model,
        Map<String, Object> params,
        Encryptor encryptor,
        Boolean isGlobalResource,
        ActionListener<Predictable> listener
    ) {
        try {
            String decryptTenantId = Boolean.TRUE.equals(isGlobalResource)
                ? REMOTE_METADATA_GLOBAL_TENANT_ID.get((Settings) params.get(SETTINGS))
                : model.getTenantId();
            Connector connector = model.getConnector().cloneConnector();
            ActionListener<Boolean> decryptSuccessfulListener = ActionListener.wrap(r -> {
                // This situation can only happen for inline connector where we don't provide tenant id.
                if (connector.getTenantId() == null && model.getTenantId() != null) {
                    connector.setTenantId(model.getTenantId());
                }
                this.connectorExecutor = MLEngineClassLoader.initInstance(connector.getProtocol(), connector, Connector.class);
                this.connectorExecutor.setScriptService((ScriptService) params.get(SCRIPT_SERVICE));
                this.connectorExecutor.setClusterService((ClusterService) params.get(CLUSTER_SERVICE));
                this.connectorExecutor.setClient((Client) params.get(CLIENT));
                this.connectorExecutor.setXContentRegistry((NamedXContentRegistry) params.get(XCONTENT_REGISTRY));
                this.connectorExecutor.setRateLimiter((TokenBucket) params.get(RATE_LIMITER));
                this.connectorExecutor.setUserRateLimiterMap((Map<String, TokenBucket>) params.get(USER_RATE_LIMITER_MAP));
                this.connectorExecutor.setMlGuard((MLGuard) params.get(GUARDRAILS));
                this.connectorExecutor.setConnectorPrivateIpEnabled((boolean) params.getOrDefault(CONNECTOR_PRIVATE_IP_ENABLED, false));
                listener.onResponse(this);
            }, e -> {
                log.error("Failed to init remote model.", e);
                listener.onFailure(new MLException(e));
            });
            connector.decrypt(PREDICT.name(), encryptor::decrypt, decryptTenantId, decryptSuccessfulListener);
        } catch (Throwable e) {
            log.error("Failed to init remote model", e);
            listener.onFailure(new MLException(e));
        }
    }
}
