/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.grpc.interfaces;

import org.opensearch.common.util.concurrent.ThreadContext;

/**
 * Interface wrapping OpenSearch client operations needed by gRPC services.
 * This abstracts client access to avoid circular dependencies between grpc and plugin modules.
 */
public interface MLClient {

    /**
     * Gets the thread context for storing and retrieving request-scoped data.
     * Used for stashing security context during async operations.
     *
     * @return the thread context
     */
    ThreadContext getThreadContext();
}
