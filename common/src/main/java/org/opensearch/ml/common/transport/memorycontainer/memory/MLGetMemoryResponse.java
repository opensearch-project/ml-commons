/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.opensearch.action.get.GetResponse;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.memorycontainer.MLLongTermMemory;
import org.opensearch.ml.common.memorycontainer.MLMemoryHistory;
import org.opensearch.ml.common.memorycontainer.MLMemorySession;
import org.opensearch.ml.common.memorycontainer.MLWorkingMemory;
import org.opensearch.ml.common.memorycontainer.MemoryType;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class MLGetMemoryResponse extends ActionResponse implements ToXContentObject {
    MLMemorySession session;
    MLWorkingMemory workingMemory;
    MLLongTermMemory longTermMemory;
    MLMemoryHistory memoryHistory;

    @Builder
    public MLGetMemoryResponse(
        MLMemorySession session,
        MLWorkingMemory workingMemory,
        MLLongTermMemory longTermMemory,
        MLMemoryHistory memoryHistory
    ) {
        this.session = session;
        this.workingMemory = workingMemory;
        this.longTermMemory = longTermMemory;
        this.memoryHistory = memoryHistory;
    }

    public MLGetMemoryResponse(StreamInput in) throws IOException {
        super(in);
        if (in.readBoolean()) {
            session = new MLMemorySession(in);
        }
        if (in.readBoolean()) {
            workingMemory = new MLWorkingMemory(in);
        }
        if (in.readBoolean()) {
            longTermMemory = new MLLongTermMemory(in);
        }
        if (in.readBoolean()) {
            memoryHistory = new MLMemoryHistory(in);
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        if (session != null) {
            out.writeBoolean(true);
            session.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        if (workingMemory != null) {
            out.writeBoolean(true);
            workingMemory.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        if (longTermMemory != null) {
            out.writeBoolean(true);
            longTermMemory.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        if (memoryHistory != null) {
            out.writeBoolean(true);
            memoryHistory.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
    }

    public static MLGetMemoryResponse fromGetResponse(GetResponse getResponse, MemoryType memoryType) {
        try (
            XContentParser parser = jsonXContent
                .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, getResponse.getSourceAsString())
        ) {
            ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
            switch (memoryType) {
                case SESSIONS:
                    return MLGetMemoryResponse.builder().session(MLMemorySession.parse(parser)).build();
                case WORKING:
                    return MLGetMemoryResponse.builder().workingMemory(MLWorkingMemory.parse(parser)).build();
                case LONG_TERM:
                    return MLGetMemoryResponse.builder().longTermMemory(MLLongTermMemory.parse(parser)).build();
                case HISTORY:
                    return MLGetMemoryResponse.builder().memoryHistory(MLMemoryHistory.parse(parser)).build();
                default:
                    throw new IllegalArgumentException("Invalid memory type: " + memoryType);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        if (session != null) {
            session.toXContent(builder, params);
        } else if (workingMemory != null) {
            workingMemory.toXContent(builder, params);
        } else if (longTermMemory != null) {
            longTermMemory.toXContent(builder, params);
        } else if (memoryHistory != null) {
            memoryHistory.toXContent(builder, params);
        }
        return builder;
    }

    public static MLGetMemoryResponse fromActionResponse(ActionResponse actionResponse) {
        if (actionResponse instanceof MLGetMemoryResponse) {
            return (MLGetMemoryResponse) actionResponse;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionResponse.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLGetMemoryResponse(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionResponse into MLMemoryGetResponse", e);
        }
    }
}
