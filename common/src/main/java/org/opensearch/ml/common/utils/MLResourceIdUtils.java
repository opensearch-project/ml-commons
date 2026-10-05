/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils;

import static java.util.Locale.ROOT;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Pattern;

import org.opensearch.OpenSearchException;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.engine.VersionConflictEngineException;

/**
 * Utilities for validating user-specified ML resource document IDs.
 */
public final class MLResourceIdUtils {

    /**
     * Maximum length of a user-specified resource id, in UTF-8 bytes.
     *
     * Kept at 256 to match the `ignore_above: 256` default that OpenSearch applies to dynamically mapped `keyword`
     * subfields (and that the ML index mappings use for id-like fields). Ids longer than that would not be indexed into
     * the `.keyword` subfield at all, so exact-match reference guards such as the "is this connector still used by a
     * model?" check in DeleteConnectorTransportAction would silently return zero hits and fail open.
     *
     * Note the units differ: this limit is checked in UTF-8 bytes, while `ignore_above` is compared against the
     * character count. The two coincide only because {@link #CUSTOM_DOCUMENT_ID_PATTERN} restricts ids to ASCII. If that
     * pattern is ever widened to non-ASCII characters, this bound must be re-derived in characters.
     *
     * Raising this value requires revisiting those guards. OpenSearch independently rejects any document id over 512
     * bytes, which is where the original limit came from.
     */
    public static final int MAX_DOCUMENT_ID_LENGTH = 256;

    /**
     * Allowed characters for user-specified document IDs used in REST path segments.
     * Must start with a letter or digit; subsequent characters may also include '_' and '-'.
     */
    public static final Pattern CUSTOM_DOCUMENT_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_-]*$");

    /**
     * Document ids the plugin writes at a fixed, non-random id of its own.
     *
     * Those writes are plain index requests: they do not set opType(CREATE), so they replace whatever document
     * already occupies the id. METRICS_CORRELATION is written that way both as a model document
     * ({@code MLModelManager.configureModelMetaIndexRequest}) and as a model group document
     * ({@code MetricsCorrelation#initModel}), so a resource that had taken the id is lost.
     *
     * Reserving the id is what keeps those writes from colliding, and it wants to land with custom ids rather
     * than after them: no stored resource can be using one yet, while adding the reservation later would
     * reject ids a previous version accepted.
     *
     * Reserved for every resource type rather than only the two that can actually collide. One list is far
     * harder to get wrong than a per-type list, and it keeps the rule explainable - a reserved id is reserved,
     * rather than being valid for an agent and invalid for a model.
     *
     * Matched case-insensitively. Only the exact upper-case form can collide, since document ids are
     * case-sensitive, but rejecting the case variants too avoids the confusing near-miss where
     * "Metrics_Correlation" is accepted and "METRICS_CORRELATION" is not.
     */
    private static final Set<String> RESERVED_DOCUMENT_IDS = Set.of("METRICS_CORRELATION");

    /**
     * Model chunk documents share {@code .plugins-ml-model} and its id namespace with model metadata
     * documents, under the id {@code <modelId>_<chunkNumber>} ({@code MLModelManager#getModelChunkId}).
     * Those writes set no opType either, so a custom model id of this shape and a chunk of an unrelated model
     * resolve to the same document.
     *
     * Applied to model ids only: chunk documents exist in the model index alone, so restricting connector,
     * agent, model group or memory container ids would be a gratuitous limitation.
     */
    private static final Pattern MODEL_CHUNK_ID_PATTERN = Pattern.compile("^.*_\\d+$");

    private MLResourceIdUtils() {}

    /**
     * Validates a user-provided custom model ID when present.
     *
     * @param modelId model ID to validate
     * @throws IllegalArgumentException if the model ID is invalid
     */
    public static void validateCustomModelId(String modelId) {
        validateCustomDocumentId(modelId, "model id");
        if (modelId != null && MODEL_CHUNK_ID_PATTERN.matcher(modelId).matches()) {
            throw new IllegalArgumentException("model id must not end with '_<number>'; that form is reserved for model chunk documents");
        }
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
        if (RESERVED_DOCUMENT_IDS.contains(documentId.toUpperCase(ROOT))) {
            throw new IllegalArgumentException(resourceLabel + " must not be a reserved id: " + documentId);
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
