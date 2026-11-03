/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.hooks;

/**
 * Interface for providers that register hook callbacks with the HookRegistry.
 * Implementations of this interface define which hooks they want to listen to
 * and provide the callback implementations.
 */
public interface HookProvider {

    /**
     * Register hook callbacks with the provided registry
     * @param registry The HookRegistry to register callbacks with
     */
    void registerHooks(HookRegistry registry);
}
