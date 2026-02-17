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

import lombok.Builder;
import lombok.Getter;

@Getter
public class MLCreateSkillRequest extends ActionRequest {

    private final MLSkill skill;

    @Builder
    public MLCreateSkillRequest(MLSkill skill) {
        this.skill = skill;
    }

    public MLCreateSkillRequest(StreamInput in) throws IOException {
        super(in);
        this.skill = new MLSkill(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        this.skill.writeTo(out);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;

        if (skill == null) {
            exception = addValidationError("Skill cannot be null", exception);
            return exception;
        }

        if (skill.getName() == null || skill.getName().trim().isEmpty()) {
            exception = addValidationError("Skill name cannot be null or empty", exception);
        } else if (skill.getName().length() > 64) {
            exception = addValidationError("Skill name must be at most 64 characters", exception);
        } else if (!skill.getName().matches("^[a-z0-9]+(-[a-z0-9]+)*$")) {
            exception = addValidationError(
                "Skill name must contain only lowercase letters, numbers, and hyphens, and cannot start or end with a hyphen",
                exception
            );
        }

        if (skill.getDescription() == null || skill.getDescription().trim().isEmpty()) {
            exception = addValidationError("Skill description cannot be null or empty", exception);
        } else if (skill.getDescription().length() > 1024) {
            exception = addValidationError("Skill description must be at most 1024 characters", exception);
        }

        if (skill.getCompatibility() != null && skill.getCompatibility().length() > 500) {
            exception = addValidationError("Skill compatibility must be at most 500 characters", exception);
        }

        if (skill.getInstructions() == null || skill.getInstructions().trim().isEmpty()) {
            exception = addValidationError("Skill instructions cannot be null or empty", exception);
        }

        return exception;
    }

    public static MLCreateSkillRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLCreateSkillRequest) {
            return (MLCreateSkillRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLCreateSkillRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse ActionRequest into MLCreateSkillRequest", e);
        }
    }
}
