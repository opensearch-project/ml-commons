/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

@EqualsAndHashCode
@Getter
public class Guardrails implements ToXContentObject {
    public static final String TYPE_FIELD = "type";
    public static final String INPUT_GUARDRAIL_FIELD = "input_guardrail";
    public static final String OUTPUT_GUARDRAIL_FIELD = "output_guardrail";
    public static final Set<String> types = Set.of("local_regex", "model");

    private String type;
    private Guardrail inputGuardrail;
    private Guardrail outputGuardrail;

    @Builder(toBuilder = true)
    public Guardrails(String type, Guardrail inputGuardrail, Guardrail outputGuardrail) {
        this.type = type;
        this.inputGuardrail = inputGuardrail;
        this.outputGuardrail = outputGuardrail;
    }

    public Guardrails(StreamInput input) throws IOException {
        type = input.readString();
        if (input.readBoolean()) {
            switch (type) {
                case "local_regex":
                    inputGuardrail = new LocalRegexGuardrail(input);
                    break;
                case "model":
                    break;
                default:
                    throw new IllegalArgumentException(String.format("Unsupported guardrails type: %s", type));
            }
        }
        if (input.readBoolean()) {
            switch (type) {
                case "local_regex":
                    outputGuardrail = new LocalRegexGuardrail(input);
                    break;
                case "model":
                    break;
                default:
                    throw new IllegalArgumentException(String.format("Unsupported guardrails type: %s", type));
            }
        }
    }

    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(type);
        if (inputGuardrail != null) {
            out.writeBoolean(true);
            inputGuardrail.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        if (outputGuardrail != null) {
            out.writeBoolean(true);
            outputGuardrail.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (type != null) {
            builder.field(TYPE_FIELD, type);
        }
        if (inputGuardrail != null) {
            builder.field(INPUT_GUARDRAIL_FIELD, inputGuardrail);
        }
        if (outputGuardrail != null) {
            builder.field(OUTPUT_GUARDRAIL_FIELD, outputGuardrail);
        }
        builder.endObject();
        return builder;
    }

    public static Guardrails parse(XContentParser parser) throws IOException {
        String type = null;
        Map<String, Object> inputGuardrailMap = null;
        Map<String, Object> outputGuardrailMap = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case TYPE_FIELD:
                    type = parser.text();
                    break;
                case INPUT_GUARDRAIL_FIELD:
                    inputGuardrailMap = parser.map();
                    break;
                case OUTPUT_GUARDRAIL_FIELD:
                    outputGuardrailMap = parser.map();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        if (!validateType(type)) {
            throw new IllegalArgumentException("The type of guardrails is required, can not be null.");
        }

        return Guardrails.builder()
                .type(type)
                .inputGuardrail(createGuardrail(type, inputGuardrailMap))
                .outputGuardrail(createGuardrail(type, outputGuardrailMap))
                .build();
    }

    private static Boolean validateType(String type) {
        if (types.contains(type)) {
            return true;
        }
        return false;
    }

    private static Guardrail createGuardrail(String type, Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return null;
        }

        switch (type) {
            case "local_regex":
                return new LocalRegexGuardrail(params);
            case "model":
                return new ModelGuardrail(params);
            default:
                throw new IllegalArgumentException(String.format("Unsupported guardrails type: %s", type));
        }
    }
}
