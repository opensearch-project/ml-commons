/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils;

import static java.util.Locale.ROOT;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.opensearch.OpenSearchException;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.engine.VersionConflictEngineException;

/**
 * Utilities for validating user-specified ML resource document IDs.
 */
public final class MLResourceIdUtils {

    public static final int MAX_DOCUMENT_ID_LENGTH = 512;

    /**
     * Allowed characters for user-specified document IDs used in REST path segments.
     * Must start with a letter or digit; subsequent characters may also include '_' and '-'.
     */
    public static final Pattern CUSTOM_DOCUMENT_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_-]*$");

    private MLResourceIdUtils() {}

    /**
     * Validates a user-provided custom model ID when present.
     *
     * @param modelId model ID to validate
     * @throws IllegalArgumentException if the model ID is invalid
     */
    public static void validateCustomModelId(String modelId) {
        validateCustomDocumentId(modelId, "model id");
    }

    /**
     * Validates a user-provided custom document ID when present.
     *
     * @param documentId document ID to validate; null means the field was omitted and is not validated
     * @param resourceLabel human-readable resource label used in error messages
     * @throws IllegalArgumentException if the document ID is invalid
     */
    public static void validateCustomDocumentId(String documentId, String resourceLabel) {
        if (documentId == null) {
            return;
        }
        if (documentId.isBlank()) {
            throw new IllegalArgumentException(resourceLabel + " is invalid");
        }
        if (documentId.startsWith("_")) {
            throw new IllegalArgumentException(resourceLabel + " must not start with '_'");
        }
        if (documentId.startsWith("-")) {
            throw new IllegalArgumentException(resourceLabel + " must not start with '-'");
        }
        if (documentId.getBytes(StandardCharsets.UTF_8).length > MAX_DOCUMENT_ID_LENGTH) {
            throw new IllegalArgumentException(resourceLabel + " is too long, max length is " + MAX_DOCUMENT_ID_LENGTH);
        }
        if (!CUSTOM_DOCUMENT_ID_PATTERN.matcher(documentId).matches()) {
            throw new IllegalArgumentException(
                resourceLabel + " must contain only letters, digits, underscores, and hyphens, and must start with a letter or digit"
            );
        }
    }

    /**
     * Returns whether the failure indicates an explicit document ID already exists.
     */
    public static boolean isDocumentAlreadyExistsException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof VersionConflictEngineException) {
                return true;
            }
            if (current instanceof OpenSearchException openSearchException && openSearchException.status() == RestStatus.CONFLICT) {
                String message = current.getMessage();
                if (message != null) {
                    String lowerMessage = message.toLowerCase(ROOT);
                    if (lowerMessage.contains("document already exists") || lowerMessage.contains("version conflict")) {
                        return true;
                    }
                }
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * Converts a document-already-exists failure into a user-friendly conflict exception when a
     * custom document ID was supplied. Returns the original exception unchanged otherwise.
     */
    public static Exception toDocumentAlreadyExistsException(String documentId, String resourceLabel, Exception cause) {
        if (documentId == null || cause == null || !isDocumentAlreadyExistsException(cause)) {
            return cause;
        }
        return new OpenSearchStatusException(resourceLabel + " '" + documentId + "' already exists", RestStatus.CONFLICT);
    }
}
