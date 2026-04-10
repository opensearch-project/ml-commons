/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.grpc;

import java.io.IOException;
import java.util.Map;

import org.opensearch.OpenSearchException;
import org.opensearch.OpenSearchSecurityException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.exception.MLLimitExceededException;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.exception.MLValidationException;

import io.grpc.Status;

/**
 * Maps OpenSearch and ML Commons exceptions to gRPC Status codes.
 */
public class GrpcStatusMapper {
    private static final Map<RestStatus, Status> STATUS_MAP = Map
        .of(
            RestStatus.FORBIDDEN,
            Status.PERMISSION_DENIED,
            RestStatus.NOT_FOUND,
            Status.NOT_FOUND,
            RestStatus.TOO_MANY_REQUESTS,
            Status.RESOURCE_EXHAUSTED,
            RestStatus.BAD_REQUEST,
            Status.INVALID_ARGUMENT,
            RestStatus.SERVICE_UNAVAILABLE,
            Status.UNAVAILABLE,
            RestStatus.UNAUTHORIZED,
            Status.UNAUTHENTICATED
        );

    /**
     * Converts an exception to a gRPC Status.
     *
     * @param exception the exception to convert
     * @return gRPC Status object
     */
    public static Status toGrpcStatus(Exception exception) {
        // Handle OpenSearch security exceptions
        if (exception instanceof OpenSearchSecurityException) {
            return Status.PERMISSION_DENIED.withDescription(exception.getMessage()).withCause(exception);
        }

        // Handle ML-specific exceptions
        if (exception instanceof MLResourceNotFoundException) {
            return Status.NOT_FOUND.withDescription(exception.getMessage()).withCause(exception);
        }

        if (exception instanceof MLLimitExceededException) {
            return Status.RESOURCE_EXHAUSTED.withDescription(exception.getMessage()).withCause(exception);
        }

        if (exception instanceof MLValidationException) {
            return Status.INVALID_ARGUMENT.withDescription(exception.getMessage()).withCause(exception);
        }

        // Handle OpenSearch exceptions with status codes
        if (exception instanceof OpenSearchException osException) {
            Status grpcStatus = STATUS_MAP.getOrDefault(osException.status(), Status.INTERNAL);
            return grpcStatus.withDescription(exception.getMessage()).withCause(exception);
        }

        // Handle I/O exceptions
        if (exception instanceof IOException) {
            return Status.UNAVAILABLE.withDescription(exception.getMessage()).withCause(exception);
        }

        // Handle illegal argument exceptions
        if (exception instanceof IllegalArgumentException) {
            return Status.INVALID_ARGUMENT.withDescription(exception.getMessage()).withCause(exception);
        }

        // Default to INTERNAL for unknown exceptions
        return Status.INTERNAL.withDescription(exception.getMessage()).withCause(exception);
    }
}
