/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.sdk;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

public abstract class AbstractSdkClient implements SdkClientDelegate {

    @SuppressWarnings({ "deprecation", "removal" })
    protected <T> CompletionStage<T> executePrivilegedAsync(PrivilegedAction<T> action, Executor executor) {
        return CompletableFuture.supplyAsync(() -> AccessController.doPrivileged(action), executor);
    }
}
