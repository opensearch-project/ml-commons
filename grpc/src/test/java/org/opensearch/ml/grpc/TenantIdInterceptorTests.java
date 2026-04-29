/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.grpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.input.Constants;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;

/**
 * Unit tests for TenantIdInterceptor.
 */
public class TenantIdInterceptorTests {

    private static final Metadata.Key<String> TENANT_ID_METADATA_KEY = Metadata.Key
        .of(Constants.TENANT_ID_HEADER, Metadata.ASCII_STRING_MARSHALLER);

    private TenantIdInterceptor interceptor;
    private ServerCall<Object, Object> mockCall;
    private ServerCallHandler<Object, Object> mockHandler;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
        interceptor = new TenantIdInterceptor();
        mockCall = mock(ServerCall.class);
        mockHandler = mock(ServerCallHandler.class);
        when(mockHandler.startCall(any(), any())).thenReturn(mock(ServerCall.Listener.class));
    }

    @Test
    public void testInterceptCall_withTenantId() {
        Metadata headers = new Metadata();
        headers.put(TENANT_ID_METADATA_KEY, "tenant-123");

        AtomicReference<String> capturedTenantId = new AtomicReference<>();

        when(mockHandler.startCall(any(), any())).thenAnswer(invocation -> {
            capturedTenantId.set(TenantIdInterceptor.TENANT_ID_CONTEXT_KEY.get());
            return mock(ServerCall.Listener.class);
        });

        interceptor.interceptCall(mockCall, headers, mockHandler);

        assertEquals("tenant-123", capturedTenantId.get());
    }

    @Test
    public void testInterceptCall_withoutTenantId() {
        Metadata headers = new Metadata();

        AtomicReference<String> capturedTenantId = new AtomicReference<>("not-null");

        when(mockHandler.startCall(any(), any())).thenAnswer(invocation -> {
            capturedTenantId.set(TenantIdInterceptor.TENANT_ID_CONTEXT_KEY.get());
            return mock(ServerCall.Listener.class);
        });

        interceptor.interceptCall(mockCall, headers, mockHandler);

        assertNull("Tenant ID should be null when header is missing", capturedTenantId.get());
    }

    @Test
    public void testInterceptCall_returnsListener() {
        Metadata headers = new Metadata();

        ServerCall.Listener<Object> result = interceptor.interceptCall(mockCall, headers, mockHandler);

        assertNotNull("Should return a listener", result);
    }

    @Test
    public void testInterceptCall_emptyTenantId() {
        Metadata headers = new Metadata();
        headers.put(TENANT_ID_METADATA_KEY, "");

        AtomicReference<String> capturedTenantId = new AtomicReference<>();

        when(mockHandler.startCall(any(), any())).thenAnswer(invocation -> {
            capturedTenantId.set(TenantIdInterceptor.TENANT_ID_CONTEXT_KEY.get());
            return mock(ServerCall.Listener.class);
        });

        interceptor.interceptCall(mockCall, headers, mockHandler);

        assertEquals("", capturedTenantId.get());
    }

    @Test
    public void testContextKeyName() {
        assertNotNull(TenantIdInterceptor.TENANT_ID_CONTEXT_KEY);
    }
}
