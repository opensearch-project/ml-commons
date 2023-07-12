/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.log4j.Log4j2;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.ActionListener;
import org.opensearch.action.LatchedActionListener;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.engine.MLEngineClassLoader;
import org.opensearch.ml.engine.Predictable;
import org.opensearch.ml.engine.annotation.Function;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.script.ScriptService;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.opensearch.ml.common.CommonValue.MASTER_KEY;
import static org.opensearch.ml.common.CommonValue.ML_CONFIG_INDEX;

@Log4j2
@Function(FunctionName.REMOTE)
public class RemoteModel implements Predictable {

    public static final String CLUSTER_SERVICE = "cluster_service";
    public static final String SCRIPT_SERVICE = "script_service";
    public static final String CLIENT = "client";
    public static final String XCONTENT_REGISTRY = "xcontent_registry";

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
    public MLOutput predict(MLInput mlInput) {
        if (!isModelReady()) {
            throw new IllegalArgumentException("Model not ready yet. Please run this first: POST /_plugins/_ml/models/<model_id>/_deploy");
        }
        try {
            return connectorExecutor.executePredict(mlInput);
        } catch (RuntimeException e) {
            log.error("Failed to call remote model", e);
            throw e;
        } catch (Throwable e) {
            log.error("Failed to call remote model", e);
            throw new MLException(e);
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

            ClusterService clusterService = (ClusterService) params.get(CLUSTER_SERVICE);
            Client client = (Client) params.get(CLIENT);
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Exception> exceptionRef = new AtomicReference<>();
            if (encryptor.getMasterKey() == null) {
                if (clusterService.state().metadata().hasIndex(ML_CONFIG_INDEX)) {
                    GetRequest getRequest = new GetRequest(ML_CONFIG_INDEX).id(MASTER_KEY);
                    client.get(getRequest, new LatchedActionListener(ActionListener.< GetResponse >wrap(r-> {
                        if (r.isExists()) {
                            String masterKey = (String)r.getSourceAsMap().get(MASTER_KEY);
                            encryptor.setMasterKey(masterKey);
                        } else {
                            exceptionRef.set(new ResourceNotFoundException("ML encryption master key not initialized yet"));
                        }
                    }, e-> {
                        log.error("Failed to get ML encryption master key", e);
                        exceptionRef.set(e);
                    }), latch));
                } else {
                    exceptionRef.set(new ResourceNotFoundException("ML encryption master key not initialized yet"));
                }
            }

            if (exceptionRef.get() != null) {
                throw exceptionRef.get();
            }
            if (encryptor.getMasterKey() != null) {
                connector.decrypt((credential) -> encryptor.decrypt(credential));
            } else {
                throw new MLException("ML encryptor not initialized");
            }
            this.connectorExecutor = MLEngineClassLoader.initInstance(connector.getProtocol(), connector, Connector.class);
            this.connectorExecutor.setScriptService((ScriptService) params.get(SCRIPT_SERVICE));
            this.connectorExecutor.setClusterService(clusterService);
            this.connectorExecutor.setClient(client);
            this.connectorExecutor.setXContentRegistry((NamedXContentRegistry) params.get(XCONTENT_REGISTRY));
        } catch (RuntimeException e) {
            log.error("Failed to init remote model", e);
            throw e;
        } catch (Throwable e) {
            log.error("Failed to init remote model", e);
            throw new MLException(e);
        }
    }

}
