/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.engine.VersionConflictEngineException;

public class MLResourceIdUtilsTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    // ---- reserved ids -------------------------------------------------------

    /**
     * METRICS_CORRELATION is written at a fixed id with no opType(CREATE), as both a model and a model group,
     * so a resource holding that id would be replaced by one of those writes.
     */
    @Test
    public void validateCustomDocumentId_rejectsReservedMetricsCorrelationId() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model group id must not be a reserved id");
        MLResourceIdUtils.validateCustomDocumentId("METRICS_CORRELATION", "model group id");
    }

    @Test
    public void validateCustomModelId_rejectsReservedMetricsCorrelationId() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model id must not be a reserved id");
        MLResourceIdUtils.validateCustomModelId("METRICS_CORRELATION");
    }

    /** Only the exact upper-case form can collide, but the case variants are rejected too. */
    @Test
    public void validateCustomModelId_rejectsReservedIdCaseInsensitively() {
        exceptionRule.expect(IllegalArgumentException.class);
        MLResourceIdUtils.validateCustomModelId("metrics_correlation");
    }

    @Test
    public void validateCustomDocumentId_acceptsIdThatMerelyContainsAReservedName() {
        MLResourceIdUtils.validateCustomDocumentId("my_METRICS_CORRELATION_copy", "agent id");
    }

    // ---- model chunk id namespace -------------------------------------------

    /**
     * Chunk documents live in the model index under "<modelId>_<chunkNumber>" with no opType, so a custom model
     * id of that shape and a chunk of an unrelated model resolve to the same document.
     */
    @Test
    public void validateCustomModelId_rejectsChunkIdShape() {
        for (String id : new String[] { "foo_0", "foo_1", "foo_12", "a_0", "foo_bar_7" }) {
            try {
                MLResourceIdUtils.validateCustomModelId(id);
                throw new AssertionError("expected rejection for model id: " + id);
            } catch (IllegalArgumentException e) {
                assertEquals("model id must not end with '_<number>'; that form is reserved for model chunk documents", e.getMessage());
            }
        }
    }

    @Test
    public void validateCustomModelId_acceptsIdsThatOnlyLookLikeChunkIds() {
        // A trailing digit is fine; only "_<digits>" at the very end is reserved.
        for (String id : new String[] { "text_embedding_v1", "model0", "foo_0bar", "foo-0", "foo_", "0_a" }) {
            MLResourceIdUtils.validateCustomModelId(id);
        }
    }

    /** The chunk-id restriction is specific to the model index; other resource types keep the shape. */
    @Test
    public void validateCustomDocumentId_allowsChunkIdShapeForNonModelResources() {
        MLResourceIdUtils.validateCustomDocumentId("foo_0", "connector id");
        MLResourceIdUtils.validateCustomDocumentId("foo_0", "agent id");
        MLResourceIdUtils.validateCustomDocumentId("foo_0", "model group id");
        MLResourceIdUtils.validateCustomDocumentId("foo_0", "memory container id");
    }

    @Test
    public void validateCustomDocumentId_acceptsNull() {
        MLResourceIdUtils.validateCustomDocumentId(null, "connector id");
    }

    @Test
    public void validateCustomDocumentId_rejectsInvalidConnectorId() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("connector id must not start with '_'");
        MLResourceIdUtils.validateCustomDocumentId("_bad", "connector id");
    }

    @Test
    public void validateCustomModelId_rejectsBlankId() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model id is invalid");
        MLResourceIdUtils.validateCustomModelId("   ");
    }

    @Test
    public void validateCustomModelId_acceptsNull() {
        MLResourceIdUtils.validateCustomModelId(null);
    }

    @Test
    public void validateCustomModelId_rejectsReservedPrefix() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model id must not start with '_'");
        MLResourceIdUtils.validateCustomModelId("_reserved");
    }

    @Test
    public void validateCustomModelId_rejectsTooLongId() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("model id is too long");
        MLResourceIdUtils.validateCustomModelId("a".repeat(MLResourceIdUtils.MAX_DOCUMENT_ID_LENGTH + 1));
    }

    /**
     * Guards https://github.com/opensearch-project/ml-commons/issues/5032: exact-match reference guards such as the
     * "is this connector still used by a model?" check query the `.keyword` subfield, which carries the
     * `ignore_above: 256` default. An id longer than that would not be indexed there at all, so the guard would return
     * zero hits and fail open. Raising this limit past 256 therefore requires revisiting those guards.
     */
    @Test
    public void maxDocumentIdLength_matchesKeywordIgnoreAboveDefault() {
        assertEquals(256, MLResourceIdUtils.MAX_DOCUMENT_ID_LENGTH);
    }

    @Test
    public void validateCustomModelId_acceptsMaxLengthId() {
        MLResourceIdUtils.validateCustomModelId("a".repeat(MLResourceIdUtils.MAX_DOCUMENT_ID_LENGTH));
    }

    @Test
    public void validateCustomModelId_acceptsValidId() {
        MLResourceIdUtils.validateCustomModelId("text_embedding_v1");
        MLResourceIdUtils.validateCustomModelId("my-gpt-model-id");
        MLResourceIdUtils.validateCustomModelId("model123");
    }

    @Test
    public void validateCustomModelId_rejectsSpecialCharacters() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule
            .expectMessage("model id must contain only letters, digits, underscores, and hyphens, and must start with a letter or digit");
        MLResourceIdUtils.validateCustomModelId("my_gpt_model_id@#$%%");
    }

    @Test
    public void validateCustomDocumentId_rejectsLeadingHyphen() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("connector id must not start with '-'");
        MLResourceIdUtils.validateCustomDocumentId("-bad-id", "connector id");
    }

    @Test
    public void toDocumentAlreadyExistsException_returnsFriendlyMessage() {
        VersionConflictEngineException cause = new VersionConflictEngineException(
            new ShardId("index", "uuid", 0),
            "my-model",
            "document already exists"
        );
        OpenSearchStatusException friendly = (OpenSearchStatusException) MLResourceIdUtils
            .toDocumentAlreadyExistsException("my-model", "model id", cause);
        assertEquals("model id 'my-model' already exists", friendly.getMessage());
        assertEquals(RestStatus.CONFLICT, friendly.status());
    }

    @Test
    public void toDocumentAlreadyExistsException_returnsFriendlyMessageForAllResourceTypes() {
        VersionConflictEngineException cause = new VersionConflictEngineException(
            new ShardId("index", "uuid", 0),
            "my-resource",
            "document already exists"
        );
        assertEquals(
            "model id 'my-model' already exists",
            ((OpenSearchStatusException) MLResourceIdUtils.toDocumentAlreadyExistsException("my-model", "model id", cause)).getMessage()
        );
        assertEquals(
            "model group id 'my-group' already exists",
            ((OpenSearchStatusException) MLResourceIdUtils.toDocumentAlreadyExistsException("my-group", "model group id", cause))
                .getMessage()
        );
        assertEquals(
            "connector id 'my-connector' already exists",
            ((OpenSearchStatusException) MLResourceIdUtils.toDocumentAlreadyExistsException("my-connector", "connector id", cause))
                .getMessage()
        );
        assertEquals(
            "agent id 'my-agent' already exists",
            ((OpenSearchStatusException) MLResourceIdUtils.toDocumentAlreadyExistsException("my-agent", "agent id", cause)).getMessage()
        );
        assertEquals(
            "memory container id 'my-container' already exists",
            ((OpenSearchStatusException) MLResourceIdUtils.toDocumentAlreadyExistsException("my-container", "memory container id", cause))
                .getMessage()
        );
    }

    @Test
    public void toDocumentAlreadyExistsException_returnsOriginalWhenDocumentIdNotProvided() {
        VersionConflictEngineException cause = new VersionConflictEngineException(
            new ShardId("index", "uuid", 0),
            "my-model",
            "document already exists"
        );
        assertSame(cause, MLResourceIdUtils.toDocumentAlreadyExistsException(null, "model id", cause));
    }

    @Test
    public void toDocumentAlreadyExistsException_returnsOriginalForUnrelatedFailure() {
        RuntimeException cause = new RuntimeException("something else");
        assertSame(cause, MLResourceIdUtils.toDocumentAlreadyExistsException("my-model", "model id", cause));
    }
}
