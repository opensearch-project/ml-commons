/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.skill;

import static org.opensearch.action.ValidateActions.addValidationError;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.MLSkill;

import lombok.Getter;

/**
 * Request to update an agent skill.
 */
@Getter
public class MLUpdateSkillRequest extends ActionRequest {

    private final String skillId;  // This is the skill name (used as document ID)
    private final MLSkill updateContent;

    public MLUpdateSkillRequest(String skillId, MLSkill updateContent) {
        this.skillId = skillId;
        this.updateContent = updateContent;
    }

    public MLUpdateSkillRequest(StreamInput in) throws IOException {
        super(in);
        this.skillId = in.readString();
        this.updateContent = new MLSkill(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(skillId);
        updateContent.writeTo(out);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (skillId == null || skillId.trim().isEmpty()) {
            exception = addValidationError("Skill ID cannot be null or empty", exception);
        }
        if (updateContent == null) {
            exception = addValidationError("Update content cannot be null", exception);
        }
        return exception;
    }

    public static MLUpdateSkillRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLUpdateSkillRequest) {
            return (MLUpdateSkillRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLUpdateSkillRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionRequest into MLUpdateSkillRequest", e);
        }
    }
}
