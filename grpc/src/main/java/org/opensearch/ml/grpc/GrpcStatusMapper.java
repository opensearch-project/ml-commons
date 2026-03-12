/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.grpc;

import java.io.IOException;

import org.opensearch.OpenSearchException;
import org.opensearch.OpenSearchSecurityException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.exception.MLLimitExceededException;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.exception.MLValidationException;

import io.grpc.Status;

/**
 * Maps OpenSearch and ML Commons exceptions to gRPC Status codes.
 *
 * <p>This mapper ensures consistent error reporting across the gRPC streaming API.
 * It translates internal OpenSearch exceptions to appropriate gRPC status codes
 * that clients can handle uniformly.
 */
public class GrpcStatusMapper {

    /**
     * Converts an exception to a gRPC Status.
     *
     * @param exception the exception to convert
     * @return gRPC Status object
     */
    public static Status toGrpcStatus(Exception exception) {
        // Handle OpenSearch security exceptions
        if (exception instanceof OpenSearchSecurityException) {
            return Status.PERMISSION_DENIED.withDescription("Access denied: " + exception.getMessage()).withCause(exception);
        }

        // Handle ML-specific exceptions
        if (exception instanceof MLResourceNotFoundException) {
            return Status.NOT_FOUND.withDescription("Resource not found: " + exception.getMessage()).withCause(exception);
        }

        if (exception instanceof MLLimitExceededException) {
            return Status.RESOURCE_EXHAUSTED.withDescription("Rate limit exceeded: " + exception.getMessage()).withCause(exception);
        }

        if (exception instanceof MLValidationException) {
            return Status.INVALID_ARGUMENT.withDescription("Invalid request: " + exception.getMessage()).withCause(exception);
        }

        // Handle OpenSearch exceptions with status codes
        if (exception instanceof OpenSearchException) {
            OpenSearchException osException = (OpenSearchException) exception;
            RestStatus status = osException.status();

            switch (status) {
                case FORBIDDEN:
                    return Status.PERMISSION_DENIED.withDescription("Access forbidden: " + exception.getMessage()).withCause(exception);
                case NOT_FOUND:
                    return Status.NOT_FOUND.withDescription("Resource not found: " + exception.getMessage()).withCause(exception);
                case TOO_MANY_REQUESTS:
                    return Status.RESOURCE_EXHAUSTED.withDescription("Too many requests: " + exception.getMessage()).withCause(exception);
                case BAD_REQUEST:
                    return Status.INVALID_ARGUMENT.withDescription("Bad request: " + exception.getMessage()).withCause(exception);
                case SERVICE_UNAVAILABLE:
                    return Status.UNAVAILABLE.withDescription("Service unavailable: " + exception.getMessage()).withCause(exception);
                case UNAUTHORIZED:
                    return Status.UNAUTHENTICATED
                        .withDescription("Authentication required: " + exception.getMessage())
                        .withCause(exception);
                default:
                    return Status.INTERNAL.withDescription("Internal error: " + exception.getMessage()).withCause(exception);
            }
        }

        // Handle I/O exceptions
        if (exception instanceof IOException) {
            return Status.UNAVAILABLE.withDescription("Service unavailable: " + exception.getMessage()).withCause(exception);
        }

        // Handle illegal argument exceptions
        if (exception instanceof IllegalArgumentException) {
            return Status.INVALID_ARGUMENT.withDescription("Invalid argument: " + exception.getMessage()).withCause(exception);
        }

        // Default to INTERNAL for unknown exceptions
        return Status.INTERNAL.withDescription("Internal error: " + exception.getMessage()).withCause(exception);
    }
}
