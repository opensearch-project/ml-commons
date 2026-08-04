/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.grpc;

import org.opensearch.ml.common.input.Constants;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

/**
 * gRPC server interceptor that extracts tenant ID from request metadata
 * and attaches it to the gRPC context for downstream service methods to access.
 */
public class TenantIdInterceptor implements ServerInterceptor {

    // Context key for storing tenant ID
    public static final Context.Key<String> TENANT_ID_CONTEXT_KEY = Context.key(Constants.TENANT_ID_HEADER);

    // Metadata key for extracting tenant ID from request headers
    private static final Metadata.Key<String> TENANT_ID_METADATA_KEY = Metadata.Key
        .of(Constants.TENANT_ID_HEADER, Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
        ServerCall<ReqT, RespT> call,
        Metadata headers,
        ServerCallHandler<ReqT, RespT> next
    ) {
        // Extract tenant ID from request metadata
        String tenantId = headers.get(TENANT_ID_METADATA_KEY);

        // Attach tenant ID to context (null if not present)
        Context context = Context.current().withValue(TENANT_ID_CONTEXT_KEY, tenantId);

        // Continue with the modified context
        return Contexts.interceptCall(context, call, headers, next);
    }
}
