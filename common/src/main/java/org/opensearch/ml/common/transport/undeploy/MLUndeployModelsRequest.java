/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.undeploy;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.transport.MLTaskRequest;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class MLUndeployModelsRequest extends MLTaskRequest {

    private static final String MODEL_IDS_FIELD = "model_ids";
    private static final String NODE_IDS_FIELD = "node_ids";
    private String[] modelIds;
    private String[] nodeIds;
    boolean async;

    @Builder
    public MLUndeployModelsRequest(String[] modelIds, String[] nodeIds, boolean async, boolean dispatchTask) {
        super(dispatchTask);
        this.modelIds = modelIds;
        this.nodeIds = nodeIds;
        this.async = async;
    }

    public MLUndeployModelsRequest(String[] modelIds, String[] nodeIds) {
        this(modelIds, nodeIds, false, false);
    }

    public MLUndeployModelsRequest(StreamInput in) throws IOException {
        super(in);
        this.modelIds = in.readOptionalStringArray();
        this.nodeIds = in.readOptionalStringArray();
        this.async = in.readBoolean();
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException exception = null;
        return exception;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeOptionalStringArray(modelIds);
        out.writeOptionalStringArray(nodeIds);
        out.writeBoolean(async);
    }

    public static MLUndeployModelsRequest parse(XContentParser parser, String modelId) throws IOException {
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        List<String> modelIdList = new ArrayList<>();
        List<String> nodeIdList = new ArrayList<>();
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case MODEL_IDS_FIELD:
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        modelIdList.add(parser.text());
                    }
                    break;
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
        String[] modelIds = modelIdList == null ? null : modelIdList.toArray(new String[0]);
        String[] nodeIds = nodeIdList == null ? null : nodeIdList.toArray(new String[0]);
        return new MLUndeployModelsRequest(modelIds, nodeIds, false, true);
    }

    public static MLUndeployModelsRequest fromActionRequest(ActionRequest actionRequest) {
        if (actionRequest instanceof MLUndeployModelsRequest) {
            return (MLUndeployModelsRequest) actionRequest;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            actionRequest.writeTo(osso);
            try (StreamInput input = new InputStreamStreamInput(new ByteArrayInputStream(baos.toByteArray()))) {
                return new MLUndeployModelsRequest(input);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to parse ActionRequest into MLUndeployModelRequest", e);
        }

    }

}
