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
import java.util.ArrayList;
import java.util.List;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

@EqualsAndHashCode
@Getter
public class Guardrail implements ToXContentObject {
    public static final String STOP_WORDS_FIELD = "stop_words";
    public static final String REGEX_FIELD = "regex";

    private List<StopWords> stopWords;
    private String[] regex;

    @Builder(toBuilder = true)
    public Guardrail(List<StopWords> stopWords, String[] regex) {
        this.stopWords = stopWords;
        this.regex = regex;
    }

    public Guardrail(StreamInput input) throws IOException {
        if (input.readBoolean()) {
            stopWords = new ArrayList<>();
            int size = input.readInt();
            for (int i=0; i<size; i++) {
                stopWords.add(new StopWords(input));
            }
        }
        regex = input.readStringArray();
    }

    public void writeTo(StreamOutput out) throws IOException {
        if (stopWords != null && stopWords.size() > 0) {
            out.writeBoolean(true);
            out.writeInt(stopWords.size());
            for (StopWords e : stopWords) {
                e.writeTo(out);
            }
        } else {
            out.writeBoolean(false);
        }
        out.writeStringArray(regex);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (stopWords != null && stopWords.size() > 0) {
            builder.field(STOP_WORDS_FIELD, stopWords);
        }
        if (regex != null) {
            builder.field(REGEX_FIELD, regex);
        }
        builder.endObject();
        return builder;
    }

    public static Guardrail parse(XContentParser parser) throws IOException {
        List<StopWords> stopWords = null;
        String[] regex = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case STOP_WORDS_FIELD:
                    stopWords = new ArrayList<>();
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        stopWords.add(StopWords.parse(parser));
                    }
                    break;
                case REGEX_FIELD:
                    regex = parser.list().toArray(new String[0]);
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return Guardrail.builder()
                .stopWords(stopWords)
                .regex(regex)
                .build();
    }
}
