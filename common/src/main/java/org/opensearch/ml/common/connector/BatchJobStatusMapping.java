/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.MLTaskState;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Optional, per-connector declaration of how to interpret a batch job's remote status.
 * <p>
 * {@code field_name} is the flat, top-level key to read from the remote status response;
 * {@code mapping} is an exact-match (not substring/regex) map from the remote status value to an
 * {@link MLTaskState} name. When a connector declares this, batch-status interpretation is resolved
 * exclusively from the declaration and the cluster-global status field-list/regex loop is skipped.
 */
@Getter
@EqualsAndHashCode
public class BatchJobStatusMapping implements ToXContentObject, Writeable {

    public static final String FIELD_NAME_FIELD = "field_name";
    public static final String MAPPING_FIELD = "mapping";

    private final String fieldName;
    private final Map<String, String> mapping;

    public BatchJobStatusMapping(String fieldName, Map<String, String> mapping) {
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("batch_job_status.field_name must not be blank");
        }
        if (mapping == null || mapping.isEmpty()) {
            throw new IllegalArgumentException("batch_job_status.mapping must not be empty");
        }
        for (String value : mapping.values()) {
            try {
                MLTaskState.valueOf(value);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("batch_job_status.mapping value '" + value + "' is not a valid MLTaskState");
            }
        }
        this.fieldName = fieldName;
        this.mapping = mapping;
    }

    public BatchJobStatusMapping(StreamInput input) throws IOException {
        this.fieldName = input.readString();
        this.mapping = input.readMap(StreamInput::readString, StreamInput::readString);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(fieldName);
        out.writeMap(mapping, StreamOutput::writeString, StreamOutput::writeString);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(FIELD_NAME_FIELD, fieldName);
        builder.field(MAPPING_FIELD, mapping);
        builder.endObject();
        return builder;
    }

    public static BatchJobStatusMapping parse(XContentParser parser) throws IOException {
        String fieldName = null;
        Map<String, String> mapping = new HashMap<>();

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String field = parser.currentName();
            parser.nextToken();
            switch (field) {
                case FIELD_NAME_FIELD:
                    fieldName = parser.text();
                    break;
                case MAPPING_FIELD:
                    mapping.putAll(parser.mapStrings());
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new BatchJobStatusMapping(fieldName, mapping);
    }
}
