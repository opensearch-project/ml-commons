/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.hooks;

import java.util.HashMap;
import java.util.Map;

public abstract class HookEvent {
    private final Map<String, Object> invocationState;

    protected HookEvent(Map<String, Object> invocationState) {
        this.invocationState = invocationState != null ? invocationState : new HashMap<>();
    }

    public Map<String, Object> getInvocationState() {
        return invocationState;
    }
}
