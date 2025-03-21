package org.opensearch.ml.engine.function_calling;

import org.apache.commons.lang3.StringUtils;
import org.opensearch.ml.common.exception.MLException;

import java.util.Locale;

import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_INTERFACE_BEDROCK_CONVERSE_CLAUDE;

public class FunctionCallingFactory {
    public FunctionCalling create(String llmInterface) {
        if (StringUtils.isBlank(llmInterface)) {
            return null;
        }

        switch (llmInterface.trim().toLowerCase(Locale.ROOT)) {
            case LLM_INTERFACE_BEDROCK_CONVERSE_CLAUDE:
                return new BedrockConverseFunctionCalling();
            default:
                throw new MLException(String.format("Unsupported llm interface: {}.", llmInterface));
        }
    }
}
