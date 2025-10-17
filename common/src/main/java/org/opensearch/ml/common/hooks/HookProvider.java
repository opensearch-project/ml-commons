/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.hooks;

public interface HookProvider {
    void registerHooks(HookRegistry registry);
}
