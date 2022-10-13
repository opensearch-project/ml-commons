/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model.load;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.InputStreamStreamInput;
import org.opensearch.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.ml.common.transport.MLTaskRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import static org.opensearch.action.ValidateActions.addValidationError;
import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class MLLoadModelRequest extends MLTaskRequest {

    private static final String NODE_IDS_FIELD = "node_ids";
    private String modelId;
    private String[] modelNodeIds;
    boolean async;

    @Builder
    public MLLoadModelRequest(String modelId, String[] modelNodeIds, boolean async, boolean dispatchTask) {
        super(dispatchTask);
        this.modelId = modelId;
        this.modelNodeIds = modelNodeIds;
        this.async = async;
    }

    public MLLoadModelRequest(String modelId, boolean async) {
        this(modelId, null, async, true);
    }

    public MLLoadModelRequest(StreamInput in) throws IOException {
        super(in);
        this.modelId = in.readString();
        this.modelNodeIds = in.readOptionalStringArray();
        this.async = in.readBoolean();
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        if (modelId == null) {
            exception = addValidationError("ML model id can't be null", exception);
        }

        return exception;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(modelId);
        out.writeOptionalStringArray(modelNodeIds);
        out.writeBoolean(async);
    }

    public static MLLoadModelRequest parse(XContentParser parser, String modelId) throws IOException {
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        List<String> nodeIdList = new ArrayList<>();
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case NODE_IDS_FIELD:
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        nodeIdList.add(parser.text());
                    }
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        String[] nodeIds = nodeIdList == null ? null : nodeIdList.toArray(new String[0]);
        return new MLLoadModelRequest(modelId, nodeIds, false, true);
    }

    public static MLLoadModelRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLLoadModelRequest) {
            return (MLLoadModelRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLLoadModelRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLLoadModelRequest", e);
        }

    }

}
