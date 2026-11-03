/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.execute.tool;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.util.Map;

import org.opensearch.core.ParseField;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.utils.StringUtils;

import lombok.Getter;
import lombok.Setter;

@org.opensearch.ml.common.annotation.MLInput(functionNames = { FunctionName.TOOL })
public class ToolMLInput extends MLInput {
    public static final String TOOL_NAME_FIELD = "tool_name";
    public static final String PARAMETERS_FIELD = "parameters";

    @Getter
    @Setter
    private String toolName;

    public ToolMLInput(StreamInput in) throws IOException {
        super(in);
        this.toolName = in.readString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(toolName);
    }

    public static final NamedXContentRegistry.Entry XCONTENT_REGISTRY = new NamedXContentRegistry.Entry(
        Input.class,
        new ParseField(FunctionName.TOOL.name()),
        it -> parse(it)
    );

    public static ToolMLInput parse(XContentParser parser) throws IOException {
        return new ToolMLInput(parser, FunctionName.TOOL);
    }

    public ToolMLInput(XContentParser parser, FunctionName functionName) throws IOException {
        this.algorithm = functionName;
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case TOOL_NAME_FIELD:
                    toolName = parser.text();
                    break;
                case PARAMETERS_FIELD:
                    Map<String, String> parameters = StringUtils.getParameterMap(parser.map());
                    inputDataset = new RemoteInferenceInputDataSet(parameters);
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
    }
}
