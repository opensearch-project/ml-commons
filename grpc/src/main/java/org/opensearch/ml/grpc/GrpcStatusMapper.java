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
        Status status = switch (exception) {
            case OpenSearchSecurityException e -> Status.PERMISSION_DENIED;
            case MLResourceNotFoundException e -> Status.NOT_FOUND;
            case MLLimitExceededException e -> Status.RESOURCE_EXHAUSTED;
            case MLValidationException e -> Status.INVALID_ARGUMENT;
            case OpenSearchException e -> STATUS_MAP.getOrDefault(e.status(), Status.INTERNAL);
            case IOException e -> Status.UNAVAILABLE;
            case IllegalArgumentException e -> Status.INVALID_ARGUMENT;
            default -> Status.INTERNAL;
        };
        return status.withDescription(exception.getMessage()).withCause(exception);
    }
}
