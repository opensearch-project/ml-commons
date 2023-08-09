/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.searchpipelines.questionanswering.generative.llm;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

/**
 * Output from LLMs via HttpConnector
 */
@Log4j2
@Getter
@Setter
@AllArgsConstructor
public class OpenSearchChatCompletionOutput implements  ChatCompletionOutput {

    private String answer;
}
