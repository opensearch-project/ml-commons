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
package org.opensearch.searchpipelines.questionanswering.generative.llm;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

import java.util.List;
import java.util.Locale;

/**
 * Output from LLMs via HttpConnector
 */
@Log4j2
@Getter
@Setter
public class ChatCompletionOutput {

    private List<Object> answers;
    private List<String> errors;

    private boolean errorOccurred;

    public ChatCompletionOutput(List<Object> answers, List<String> errors) {

        if (answers == null && errors == null) {
            throw new IllegalArgumentException("answers and errors can't both be null.");
        }

        if (answers == null) {
            if (errors.isEmpty()) {
                throw new IllegalArgumentException("If answers is not provided, one or more errors must be provided.");
            }
            this.errorOccurred = true;
        } else if (errors == null) {
            if (answers.isEmpty()) {
                throw new IllegalArgumentException("If errors is not provided, one or more answers must be provided.");
            }
        }

        this.answers = answers;
        this.errors = errors;
    }
}
