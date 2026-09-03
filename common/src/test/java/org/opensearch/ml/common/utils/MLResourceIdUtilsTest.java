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
