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

import lombok.Getter;

@Getter
public class MLCreateSkillResponse extends ActionResponse implements ToXContentObject {

    private static final String SKILL_ID_FIELD = "skill_id";
    private static final String STATUS_FIELD = "status";

    private String skillId;
    private String status;

    public MLCreateSkillResponse(String skillId, String status) {
        this.skillId = skillId;
        this.status = status;
    }

    public MLCreateSkillResponse(StreamInput in) throws IOException {
        super(in);
        this.skillId = in.readString();
        this.status = in.readString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(skillId);
        out.writeString(status);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(SKILL_ID_FIELD, skillId);
        builder.field(STATUS_FIELD, status);
        builder.endObject();
        return builder;
    }
}
