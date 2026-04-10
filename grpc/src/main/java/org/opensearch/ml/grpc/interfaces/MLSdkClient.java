/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.grpc.interfaces;

/**
 * Interface wrapping SDK client operations needed by gRPC services.
 * This abstracts SDK client to avoid circular dependencies between grpc and plugin modules.
 *
 * This is a marker interface as the SDK client is currently only passed through
 * to access control validation. Future methods can be added as needed.
 */
public interface MLSdkClient {
    // Marker interface - SDK client is passed through to validation
}
