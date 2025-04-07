/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.function_calling;

public interface LLMMessage {
    public String getRole();

    public Object getContent();
}
