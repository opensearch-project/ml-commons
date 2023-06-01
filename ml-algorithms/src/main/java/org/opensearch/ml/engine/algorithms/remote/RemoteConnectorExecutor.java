/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.script.ScriptService;

public interface RemoteConnectorExecutor {

    ModelTensorOutput executePredict(MLInput mlInput);

    default void setScriptService(ScriptService scriptService){}
    default void setClient(Client client){}
    default void setXContentRegistry(NamedXContentRegistry xContentRegistry){}
    default void setClusterService(ClusterService clusterService){}

}
