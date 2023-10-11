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

import java.util.List;
import java.util.Map;

import lombok.Builder;
import org.opensearch.ml.common.conversation.Interaction;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

/**
 * Input for LLMs via HttpConnector
 */
@Log4j2
@Getter
@Setter
@AllArgsConstructor
public class ChatCompletionInput {

    private String model;
    private String question;
    private List<Interaction> chatHistory;
    private List<String> contexts;
    private int timeoutInSeconds;
    private String systemPrompt;
    private String userInstructions;
    private Llm.ModelProvider modelProvider;
}
