/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.function_calling;

import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_INTERFACE_BEDROCK_CONVERSE_CLAUDE;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_INTERFACE_BEDROCK_CONVERSE_DEEPSEEK_R1;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_INTERFACE_OPENAI_V1_CHAT_COMPLETIONS;

import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.opensearch.ml.common.exception.MLException;

public class FunctionCallingFactory {
    public static FunctionCalling create(String llmInterface) {
        if (StringUtils.isBlank(llmInterface)) {
            return null;
        }

        switch (llmInterface.trim().toLowerCase(Locale.ROOT)) {
            case LLM_INTERFACE_BEDROCK_CONVERSE_CLAUDE:
                return new BedrockConverseFunctionCalling();
            case LLM_INTERFACE_OPENAI_V1_CHAT_COMPLETIONS:
                return new OpenaiV1ChatCompletionsFunctionCalling();
            case LLM_INTERFACE_BEDROCK_CONVERSE_DEEPSEEK_R1:
                return new BedrockConverseDeepseekR1FunctionCalling();
            default:
                throw new MLException(String.format("Invalid _llm_interface: %s", llmInterface));
        }
    }
}
