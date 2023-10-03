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

import java.util.Optional;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.search.SearchExtBuilder;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Utility class for extracting generative QA search pipeline parameters from search requests.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class GenerativeQAParamUtil {

    public static GenerativeQAParameters getGenerativeQAParameters(SearchRequest request) {
        GenerativeQAParamExtBuilder builder = null;
        if (request.source() != null && request.source().ext() != null && !request.source().ext().isEmpty()) {
            Optional<SearchExtBuilder> b = request
                .source()
                .ext()
                .stream()
                .filter(bldr -> GenerativeQAParamExtBuilder.PARAMETER_NAME.equals(bldr.getWriteableName()))
                .findFirst();
            if (b.isPresent()) {
                builder = (GenerativeQAParamExtBuilder) b.get();
            }
        }

        GenerativeQAParameters params = null;
        if (builder != null) {
            params = builder.getParams();
        }

        return params;
    }
}
