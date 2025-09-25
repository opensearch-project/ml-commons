/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import org.opensearch.ml.common.memorycontainer.MemoryType;

/**
 * Helper class to track memory info for response building
 */
public class MemoryInfo {
    private String memoryId;
    private final String content;
    private final MemoryType type;
    private final boolean includeInResponse;

    public MemoryInfo(String memoryId, String content, MemoryType type, boolean includeInResponse) {
        this.memoryId = memoryId;
        this.content = content;
        this.type = type;
        this.includeInResponse = includeInResponse;
    }

    public String getMemoryId() {
        return memoryId;
    }

    public void setMemoryId(String memoryId) {
        this.memoryId = memoryId;
    }

    public String getContent() {
        return content;
    }

    public MemoryType getType() {
        return type;
    }

    public boolean isIncludeInResponse() {
        return includeInResponse;
    }
}
