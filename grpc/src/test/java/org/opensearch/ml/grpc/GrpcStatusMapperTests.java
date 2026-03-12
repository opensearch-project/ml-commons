/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package test.java.org.opensearch.ml.grpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;

import org.junit.Test;
import org.opensearch.OpenSearchException;
import org.opensearch.OpenSearchSecurityException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.exception.MLLimitExceededException;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.exception.MLValidationException;

import io.grpc.Status;

/**
 * Unit tests for GrpcStatusMapper.
 */
public class GrpcStatusMapperTests {

    @Test
    public void testSecurityExceptionMapsToPermissionDenied() {
        OpenSearchSecurityException exception = new OpenSearchSecurityException("Access denied");
        Status status = GrpcStatusMapper.toGrpcStatus(exception);

        assertNotNull(status);
        assertEquals(Status.Code.PERMISSION_DENIED, status.getCode());
    }

    @Test
    public void testResourceNotFoundMapsToNotFound() {
        MLResourceNotFoundException exception = new MLResourceNotFoundException("Model not found");
        Status status = GrpcStatusMapper.toGrpcStatus(exception);

        assertNotNull(status);
        assertEquals(Status.Code.NOT_FOUND, status.getCode());
    }

    @Test
    public void testLimitExceededMapsToResourceExhausted() {
        MLLimitExceededException exception = new MLLimitExceededException("Rate limit exceeded");
        Status status = GrpcStatusMapper.toGrpcStatus(exception);

        assertNotNull(status);
        assertEquals(Status.Code.RESOURCE_EXHAUSTED, status.getCode());
    }

    @Test
    public void testValidationExceptionMapsToInvalidArgument() {
        MLValidationException exception = new MLValidationException("Invalid input");
        Status status = GrpcStatusMapper.toGrpcStatus(exception);

        assertNotNull(status);
        assertEquals(Status.Code.INVALID_ARGUMENT, status.getCode());
    }

    @Test
    public void testOpenSearchForbiddenMapsToPermissionDenied() {
        OpenSearchException exception = new OpenSearchException("Forbidden", RestStatus.FORBIDDEN);
        Status status = GrpcStatusMapper.toGrpcStatus(exception);

        assertNotNull(status);
        assertEquals(Status.Code.PERMISSION_DENIED, status.getCode());
    }

    @Test
    public void testOpenSearchNotFoundMapsToNotFound() {
        OpenSearchException exception = new OpenSearchException("Not found", RestStatus.NOT_FOUND);
        Status status = GrpcStatusMapper.toGrpcStatus(exception);

        assertNotNull(status);
        assertEquals(Status.Code.NOT_FOUND, status.getCode());
    }

    @Test
    public void testOpenSearchTooManyRequestsMapsToResourceExhausted() {
        OpenSearchException exception = new OpenSearchException("Too many requests", RestStatus.TOO_MANY_REQUESTS);
        Status status = GrpcStatusMapper.toGrpcStatus(exception);

        assertNotNull(status);
        assertEquals(Status.Code.RESOURCE_EXHAUSTED, status.getCode());
    }

    @Test
    public void testOpenSearchBadRequestMapsToInvalidArgument() {
        OpenSearchException exception = new OpenSearchException("Bad request", RestStatus.BAD_REQUEST);
        Status status = GrpcStatusMapper.toGrpcStatus(exception);

        assertNotNull(status);
        assertEquals(Status.Code.INVALID_ARGUMENT, status.getCode());
    }

    @Test
    public void testOpenSearchServiceUnavailableMapsToUnavailable() {
        OpenSearchException exception = new OpenSearchException("Service unavailable", RestStatus.SERVICE_UNAVAILABLE);
        Status status = GrpcStatusMapper.toGrpcStatus(exception);

        assertNotNull(status);
        assertEquals(Status.Code.UNAVAILABLE, status.getCode());
    }

    @Test
    public void testOpenSearchUnauthorizedMapsToUnauthenticated() {
        OpenSearchException exception = new OpenSearchException("Unauthorized", RestStatus.UNAUTHORIZED);
        Status status = GrpcStatusMapper.toGrpcStatus(exception);

        assertNotNull(status);
        assertEquals(Status.Code.UNAUTHENTICATED, status.getCode());
    }

    @Test
    public void testIOExceptionMapsToUnavailable() {
        IOException exception = new IOException("Network error");
        Status status = GrpcStatusMapper.toGrpcStatus(exception);

        assertNotNull(status);
        assertEquals(Status.Code.UNAVAILABLE, status.getCode());
    }

    @Test
    public void testIllegalArgumentExceptionMapsToInvalidArgument() {
        IllegalArgumentException exception = new IllegalArgumentException("Invalid parameter");
        Status status = GrpcStatusMapper.toGrpcStatus(exception);

        assertNotNull(status);
        assertEquals(Status.Code.INVALID_ARGUMENT, status.getCode());
    }

    @Test
    public void testUnknownExceptionMapsToInternal() {
        RuntimeException exception = new RuntimeException("Unknown error");
        Status status = GrpcStatusMapper.toGrpcStatus(exception);

        assertNotNull(status);
        assertEquals(Status.Code.INTERNAL, status.getCode());
    }

    @Test
    public void testStatusIncludesDescription() {
        MLResourceNotFoundException exception = new MLResourceNotFoundException("Model xyz not found");
        Status status = GrpcStatusMapper.toGrpcStatus(exception);

        assertNotNull(status);
        assertNotNull(status.getDescription());
    }

    @Test
    public void testStatusIncludesCause() {
        MLResourceNotFoundException exception = new MLResourceNotFoundException("Model not found");
        Status status = GrpcStatusMapper.toGrpcStatus(exception);

        assertNotNull(status);
        assertNotNull(status.getCause());
    }
}
