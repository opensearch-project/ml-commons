/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.grpc.interfaces;

import org.opensearch.commons.authuser.User;

/**
 * Interface for extracting user context from OpenSearch security.
 * This abstracts user context extraction to avoid circular dependencies between grpc and plugin modules.
 */
public interface MLUserContextProvider {

    /**
     * Extracts the current user from the security context.
     *
     * @return the current user, or null if not authenticated
     */
    User getUserContext();
}
