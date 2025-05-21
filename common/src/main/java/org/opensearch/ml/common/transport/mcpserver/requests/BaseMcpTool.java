/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.common.transport.mcpserver.requests;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.opensearch.ml.common.CommonValue;

/**
 *  This class represents a tool that can be registered with OpenSearch. It contains information about the tool's name,
 * description, parameters, and schema.
 */
@Log4j2
@Data
public class BaseMcpTool implements ToXContentObject, Writeable {
    public static final String TOOL = "tool";
    public static final String TYPE_FIELD = "type";
    public static final String NAME_FIELD = "name";
    public static final String DESCRIPTION_FIELD = "description";
    public static final String PARAMS_FIELD = "parameters";
    public static final String ATTRIBUTES_FIELD = "attributes";
    private String type;
    private String name;
    private String description;
    private Long version;
    private Map<String, Object> parameters;
    private Map<String, Object> attributes;
    private Instant createdTime;
    private Instant lastUpdatedTime;
    public static final String TYPE_NOT_SHOWN_EXCEPTION_MESSAGE = "type field required";
    public static final String NAME_NOT_SHOWN_EXCEPTION_MESSAGE = "name field required if it's memory only register request";

    public BaseMcpTool(StreamInput streamInput) throws IOException {
        type = streamInput.readString();
        name = streamInput.readOptionalString();
        description = streamInput.readOptionalString();
        if (streamInput.readBoolean()) {
            parameters = streamInput.readMap(StreamInput::readString, StreamInput::readGenericValue);
        }
        if (streamInput.readBoolean()) {
            attributes = streamInput.readMap(StreamInput::readString, StreamInput::readGenericValue);
        }
        createdTime = streamInput.readOptionalInstant();
        lastUpdatedTime = streamInput.readOptionalInstant();
        version = streamInput.readOptionalLong();
    }

    public BaseMcpTool(String name, String type, String description, Map<String, Object> parameters, Map<String, Object> attributes, Instant createdTime, Instant lastUpdatedTime) {
        this.name = name;
        this.type = type;
        this.description = description;
        this.parameters = parameters;
        this.attributes = attributes;
        this.createdTime = createdTime;
        this.lastUpdatedTime = lastUpdatedTime;
    }

    @Override
    public void writeTo(StreamOutput streamOutput) throws IOException {
        streamOutput.writeString(type);
        streamOutput.writeOptionalString(name);
        streamOutput.writeOptionalString(description);
        if (parameters != null) {
            streamOutput.writeBoolean(true);
            streamOutput.writeMap(parameters, StreamOutput::writeString, StreamOutput::writeGenericValue);
        } else {
            streamOutput.writeBoolean(false);
        }

        if (attributes != null) {
            streamOutput.writeBoolean(true);
            streamOutput.writeMap(attributes, StreamOutput::writeString, StreamOutput::writeGenericValue);
        } else {
            streamOutput.writeBoolean(false);
        }

        streamOutput.writeOptionalInstant(createdTime);
        streamOutput.writeOptionalInstant(lastUpdatedTime);

        if (version != null) {
            streamOutput.writeBoolean(true);
            streamOutput.writeLong(version);
        } else {
            streamOutput.writeBoolean(false);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params xcontentParams) throws IOException {
        builder.startObject();
        builder.field(TYPE_FIELD, type);
        if (name != null) {
            builder.field(NAME_FIELD, name);
        }
        if (description != null) {
            builder.field(DESCRIPTION_FIELD, description);
        }
        if (parameters != null && !parameters.isEmpty()) {
            builder.field(PARAMS_FIELD, parameters);
        }
        if (attributes != null && !attributes.isEmpty()) {
            builder.field(ATTRIBUTES_FIELD, attributes);
        }
        if (createdTime != null) {
            builder.field(CommonValue.CREATE_TIME_FIELD, createdTime.toEpochMilli());
        }
        if (lastUpdatedTime != null) {
            builder.field(CommonValue.LAST_UPDATE_TIME_FIELD, lastUpdatedTime.toEpochMilli());
        }
        builder.endObject();
        return builder;
    }
}
