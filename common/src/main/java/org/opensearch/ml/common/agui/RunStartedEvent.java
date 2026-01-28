/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agui;

import java.io.IOException;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RunStartedEvent extends BaseEvent {

    public static final String TYPE = "RUN_STARTED";

    private String threadId;
    private String runId;

    public RunStartedEvent(String threadId, String runId) {
        super(TYPE, System.currentTimeMillis(), null);
        this.threadId = threadId;
        this.runId = runId;
    }

    public RunStartedEvent(StreamInput input) throws IOException {
        super(input);
        this.threadId = input.readString();
        this.runId = input.readString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(threadId);
        out.writeString(runId);
    }

    @Override
    protected void addEventSpecificFields(XContentBuilder builder, Params params) throws IOException {
        builder.field("threadId", threadId);
        builder.field("runId", runId);
    }
}
