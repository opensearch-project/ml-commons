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

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

@EqualsAndHashCode
@Getter
public class Guardrails implements ToXContentObject {
    public static final String TYPE_FIELD = "type";
    public static final String ENGLISH_DETECTION_ENABLED_FIELD = "english_detection_enabled";
    public static final String INPUT_GUARDRAIL_FIELD = "input_guardrail";
    public static final String OUTPUT_GUARDRAIL_FIELD = "output_guardrail";

    private String type;
    private Boolean engDetectionEnabled;
    private Guardrail inputGuardrail;
    private Guardrail outputGuardrail;

    @Builder(toBuilder = true)
    public Guardrails(String type, Boolean engDetectionEnabled, Guardrail inputGuardrail, Guardrail outputGuardrail) {
        this.type = type;
        this.engDetectionEnabled = engDetectionEnabled;
        this.inputGuardrail = inputGuardrail;
        this.outputGuardrail = outputGuardrail;
    }

    public Guardrails(StreamInput input) throws IOException {
        type = input.readString();
        engDetectionEnabled = input.readBoolean();
        if (input.readBoolean()) {
            inputGuardrail = new Guardrail(input);
        }
        if (input.readBoolean()) {
            outputGuardrail = new Guardrail(input);
        }
    }

    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(type);
        out.writeBoolean(engDetectionEnabled);
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
        if (engDetectionEnabled != null) {
            builder.field(ENGLISH_DETECTION_ENABLED_FIELD, engDetectionEnabled);
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
        Boolean engDetectionEnabled = null;
        Guardrail inputGuardrail = null;
        Guardrail outputGuardrail = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case TYPE_FIELD:
                    type = parser.text();
                    break;
                case ENGLISH_DETECTION_ENABLED_FIELD:
                    engDetectionEnabled = parser.booleanValue();
                    break;
                case INPUT_GUARDRAIL_FIELD:
                    inputGuardrail = Guardrail.parse(parser);
                    break;
                case OUTPUT_GUARDRAIL_FIELD:
                    outputGuardrail = Guardrail.parse(parser);
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return Guardrails.builder()
                .type(type)
                .engDetectionEnabled(engDetectionEnabled)
                .inputGuardrail(inputGuardrail)
                .outputGuardrail(outputGuardrail)
                .build();
    }
}
