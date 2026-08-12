/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.util.Map;

import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

public class BatchJobStatusMappingTest {

    private BatchJobStatusMapping vertexMapping() {
        return new BatchJobStatusMapping("state", Map.of("JOB_STATE_SUCCEEDED", "COMPLETED", "JOB_STATE_FAILED", "FAILED"));
    }

    @Test
    public void constructor_valid_setsFields() {
        BatchJobStatusMapping m = vertexMapping();
        assertEquals("state", m.getFieldName());
        assertEquals("COMPLETED", m.getMapping().get("JOB_STATE_SUCCEEDED"));
    }

    @Test
    public void constructor_blankFieldName_throws() {
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> new BatchJobStatusMapping("  ", Map.of("X", "COMPLETED"))
        );
        assertEquals("batch_job_status.field_name must not be blank", e.getMessage());
    }

    @Test
    public void constructor_emptyMapping_throws() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> new BatchJobStatusMapping("state", Map.of()));
        assertEquals("batch_job_status.mapping must not be empty", e.getMessage());
    }

    @Test
    public void constructor_illegalTaskState_throws() {
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> new BatchJobStatusMapping("state", Map.of("JOB_STATE_SUCCEEDED", "NOT_A_STATE"))
        );
        assertEquals("batch_job_status.mapping value 'NOT_A_STATE' is not a valid MLTaskState", e.getMessage());
    }

    @Test
    public void streamRoundTrip_preservesFields() throws IOException {
        BatchJobStatusMapping original = vertexMapping();
        BytesStreamOutput out = new BytesStreamOutput();
        original.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        BatchJobStatusMapping restored = new BatchJobStatusMapping(in);
        assertEquals(original.getFieldName(), restored.getFieldName());
        assertEquals(original.getMapping(), restored.getMapping());
    }

    @Test
    public void xContentRoundTrip_preservesFields() throws IOException {
        BatchJobStatusMapping original = vertexMapping();
        XContentBuilder builder = XContentType.JSON.contentBuilder();
        original.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String json = builder.toString();

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, json);
        parser.nextToken();
        BatchJobStatusMapping restored = BatchJobStatusMapping.parse(parser);
        assertEquals(original.getFieldName(), restored.getFieldName());
        assertEquals(original.getMapping(), restored.getMapping());
    }

    @Test
    public void constructor_lowercaseTaskStateValue_throws() {
        // Mapping values are matched exactly against MLTaskState names (case-sensitive by design);
        // a lowercase value must be rejected rather than silently normalized.
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> new BatchJobStatusMapping("state", Map.of("JOB_STATE_SUCCEEDED", "completed"))
        );
        assertEquals("batch_job_status.mapping value 'completed' is not a valid MLTaskState", e.getMessage());
    }

    @Test
    public void equalsHashCode_valueSemantics() {
        BatchJobStatusMapping a = new BatchJobStatusMapping("state", Map.of("JOB_STATE_SUCCEEDED", "COMPLETED"));
        BatchJobStatusMapping b = new BatchJobStatusMapping("state", Map.of("JOB_STATE_SUCCEEDED", "COMPLETED"));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
