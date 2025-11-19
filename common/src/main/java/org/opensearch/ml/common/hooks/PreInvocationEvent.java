/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.hooks;

import java.util.Map;

import org.opensearch.ml.common.input.Input;

public class PreInvocationEvent extends HookEvent {
    private final Input input;

    public PreInvocationEvent(Input input, Map<String, Object> invocationState) {
        super(invocationState);
        this.input = input;
    }

    public Input getInput() {
        return input;
    }
}
