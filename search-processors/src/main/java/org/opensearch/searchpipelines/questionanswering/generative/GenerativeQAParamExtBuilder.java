/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.searchpipelines.questionanswering.generative;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.search.SearchExtBuilder;

import java.io.IOException;
import java.util.Objects;

/**
 * This is the extension builder for generative QA search pipelines.
 */
@NoArgsConstructor
public class GenerativeQAParamExtBuilder extends SearchExtBuilder {

    public static final String NAME = "generative_qa_parameters";

    @Setter
    @Getter
    private GenerativeQAParameters params;

    public GenerativeQAParamExtBuilder(StreamInput input) throws IOException {
        this.params = new GenerativeQAParameters(input);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getClass(), this.params);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof GenerativeQAParamExtBuilder)) {
            return false;
        }

        return Objects.equals(this.getParams(), ((GenerativeQAParamExtBuilder) obj).getParams());
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        this.params.writeTo(out);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        return builder.value(params);
    }

    public static GenerativeQAParamExtBuilder parse(XContentParser parser) throws IOException {
        GenerativeQAParamExtBuilder builder = new GenerativeQAParamExtBuilder();
        GenerativeQAParameters params = GenerativeQAParameters.parse(parser);
        builder.setParams(params);
        return builder;
    }
}
