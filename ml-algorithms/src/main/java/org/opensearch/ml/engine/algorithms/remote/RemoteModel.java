/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import java.util.Map;

import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.util.TokenBucket;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.MLGuard;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.engine.MLEngineClassLoader;
import org.opensearch.ml.engine.Predictable;
import org.opensearch.ml.engine.annotation.Function;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.script.ScriptService;

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
    public void asyncPredict(MLInput mlInput, ActionListener<MLTaskResponse> actionListener) {
        if (!isModelReady()) {
            actionListener
                .onFailure(
                    new IllegalArgumentException("Model not ready yet. Please run this first: POST /_plugins/_ml/models/<model_id>/_deploy")
                );
            return;
        }
        try {
            connectorExecutor.executePredict(mlInput, actionListener);
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
    public void initModel(MLModel model, Map<String, Object> params, Encryptor encryptor) {
        try {
            Connector connector = model.getConnector().cloneConnector();
            connector.decrypt((credential) -> encryptor.decrypt(credential));
            this.connectorExecutor = MLEngineClassLoader.initInstance(connector.getProtocol(), connector, Connector.class);
            this.connectorExecutor.setScriptService((ScriptService) params.get(SCRIPT_SERVICE));
            this.connectorExecutor.setClusterService((ClusterService) params.get(CLUSTER_SERVICE));
            this.connectorExecutor.setClient((Client) params.get(CLIENT));
            this.connectorExecutor.setXContentRegistry((NamedXContentRegistry) params.get(XCONTENT_REGISTRY));
            this.connectorExecutor.setRateLimiter((TokenBucket) params.get(RATE_LIMITER));
            this.connectorExecutor.setUserRateLimiterMap((Map<String, TokenBucket>) params.get(USER_RATE_LIMITER_MAP));
            this.connectorExecutor.setMlGuard((MLGuard) params.get(GUARDRAILS));
        } catch (RuntimeException e) {
            log.error("Failed to init remote model.", e);
            throw e;
        } catch (Throwable e) {
            log.error("Failed to init remote model.", e);
            throw new MLException(e);
        }
    }

}
