/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine;

import java.util.Map;

import org.opensearch.ml.common.MLModel;

public interface MLExecutable extends Executable {

    /**
     * Init model (deploy model into memory) with ML model content and params.
     * @param model ML model
     * @param params other parameters
     */
    void initModel(MLModel model, Map<String, Object> params);

    /**
     * Close resources like deployed model.
     */
    void close();

    /**
     * Release only what this instance holds in memory, such as its predictors and loaded models, without deleting the
     * model's local files. Every deploy attempt of a model on a node shares those files, so an instance that is
     * discarded without being published to the model cache must not delete them: another attempt may still be loading
     * from them. Defaults to {@link #close()}, for implementations that keep no local model files.
     */
    default void release() {
        close();
    }
}
