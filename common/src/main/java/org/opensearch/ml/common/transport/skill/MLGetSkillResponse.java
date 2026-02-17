/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.skill;

import java.io.IOException;

import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.MLSkill;

import lombok.Getter;

@Getter
public class MLGetSkillResponse extends ActionResponse implements ToXContentObject {

    private MLSkill skill;

    public MLGetSkillResponse(MLSkill skill) {
        this.skill = skill;
    }

    public MLGetSkillResponse(StreamInput in) throws IOException {
        super(in);
        this.skill = new MLSkill(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        skill.writeTo(out);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        return skill.toXContent(builder, params);
    }
}
