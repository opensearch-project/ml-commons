/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.searchpipelines.questionanswering.generative.llm;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

import java.util.List;

/**
 * Input for LLMs via HttpConnector
 */
@Log4j2
@Getter
@Setter
@AllArgsConstructor
public class OpenSearchChatCompletionInput implements  ChatCompletionInput {

    private String model;
    private String question;
    private List<String> chatHistory;
    private List<String> contexts;
}
