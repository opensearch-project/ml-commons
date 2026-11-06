/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools.selection;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

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
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format(Locale.ROOT, "%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // Every conformant JVM is required to provide SHA-256, so this is unreachable.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String lengthPrefixed(String value) {
        String text = value == null ? "" : value;
        return text.length() + ":" + text;
    }
}
