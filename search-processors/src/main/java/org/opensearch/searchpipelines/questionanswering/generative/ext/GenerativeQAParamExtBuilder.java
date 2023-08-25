/*
 * Copyright 2023 Aryn
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opensearch.searchpipelines.questionanswering.generative.ext;

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

    // The name of the "ext" section containing Generative QA parameters.
    public static final String PARAMETER_NAME = "generative_qa_parameters";

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
        return PARAMETER_NAME;
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
