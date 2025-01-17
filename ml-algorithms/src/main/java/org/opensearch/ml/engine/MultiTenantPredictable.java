package org.opensearch.ml.engine;

import java.util.Map;

import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.engine.encryptor.Encryptor;

public interface MultiTenantPredictable extends Predictable {

    /**
     * Init model (load model into memory) with ML model content and params.
     * @param model ML model
     * @param params other parameters
     * @param encryptor encryptor
     * @param tenantId tenantId
     */
    void initModel(MLModel model, Map<String, Object> params, Encryptor encryptor, String tenantId);
}
