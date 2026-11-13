/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools.selection;

import java.nio.charset.StandardCharsets;

import org.opensearch.common.hash.MessageDigests;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * A tool as the selector sees it: the name to return, and the text that gets embedded.
 */
@Getter
@EqualsAndHashCode
public final class ToolDescriptor {

    private final String name;
    private final String description;

    private ToolDescriptor(String name, String description) {
        this.name = name;
        this.description = description == null ? "" : description;
    }

    public static ToolDescriptor of(String name, String description) {
        return new ToolDescriptor(name, description);
    }

    /**
     * The text sent to the embedding model. Falls back to the tool name when the description is
     * blank, so description-less tools don't all share one cache key and vector.
     */
    public String embeddingText() {
        return description.isBlank() ? String.valueOf(name) : description;
    }

    /**
     * Cache key over embedding model, tenant and embedding text. Each field is length-prefixed, so no
     * field content, including a newline, can shift a boundary between fields.
     */
    public String cacheKey(String embeddingModelId, String tenantId) {
        String material = lengthPrefixed(embeddingModelId) + lengthPrefixed(tenantId) + lengthPrefixed(embeddingText());
        return MessageDigests.toHexString(MessageDigests.sha256().digest(material.getBytes(StandardCharsets.UTF_8)));
    }

    private static String lengthPrefixed(String value) {
        String text = value == null ? "" : value;
        return text.length() + ":" + text;
    }
}
