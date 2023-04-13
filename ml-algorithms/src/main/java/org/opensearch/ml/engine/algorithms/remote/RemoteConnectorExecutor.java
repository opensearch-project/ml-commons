/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.script.ScriptService;

public interface RemoteConnectorExecutor {

    ModelTensorOutput execute(MLInput mlInput);

    default void setScriptService(ScriptService scriptService){};
}
