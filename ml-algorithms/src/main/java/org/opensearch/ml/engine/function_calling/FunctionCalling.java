package org.opensearch.ml.engine.function_calling;

import org.opensearch.ml.common.output.model.ModelTensorOutput;

import java.util.List;
import java.util.Map;

/**
 * A general LLM function calling interface.
 */
public interface FunctionCalling {

    /**
     * Configure all parameters related to function calling.
     * @param params the parameters used to configure a request to LLM
     */
    void configure(Map<String, String> params);

    /**
     * Handle the response from LLM to get the function calling context.
     * @param modelTensorOutput the response from LLM
     * @param parameters some parameters
     * @return a list of tools with something like name, input, etc.
     */
    List<Map<String, String>> handle(ModelTensorOutput modelTensorOutput, Map<String, String> parameters);

    /**
     * According to results of tools to render a LLMMessage provided to LLM
     * @param toolResults results from tools
     * @return a LLMMessage containing tool results.
     */
    LLMMessage supply(List<Map<String, String>> toolResults);
}
