/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.grpc.interfaces;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.action.ActionResponse;

/**
 * Interface wrapping OpenSearch client operations needed by gRPC services.
 * This abstracts client access to avoid circular dependencies between grpc and plugin modules.
 */
public interface MLClient {

    /**
     * Executes an action through the standard transport dispatch path.
     */
    <Request extends ActionRequest, Response extends ActionResponse> void execute(
        ActionType<Response> action,
        Request request,
        ActionListener<Response> listener
    );
}
