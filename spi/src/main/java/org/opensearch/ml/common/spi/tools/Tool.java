/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.spi.tools;

import org.opensearch.core.action.ActionListener;
import java.util.Map;

/**
 * General tool interface.
 */
public interface Tool {

    /**
     * Run tool and return response asynchronously.
     * @param parameters input parameters
     * @param listener an action listener for the response
     * @param <T> The output type
     */
    default <T> void run(Map<String, String> parameters, ActionListener<T> listener) {};

    /**
     * Set input parser.
     * @param parser the parser to set
     */
    default void setInputParser(Parser<?, ?> parser) {};

    /**
     * Set output parser.
     * @param parser the parser to set
     */
    default void setOutputParser(Parser<?, ?> parser) {};

    /**
     * Get tool type.
     * Agent uses the type to find the tool.
     * @return the tool type
     */
    String getType();

    /**
     * Get tool version.
     * @return the tool version
     */
    String getVersion();

    /**
     * Get tool name which is displayed in prompt.
     * @return the tool name
     */
    String getName();

    /**
     * Set tool name which is displayed in prompt.
     * @param name the tool name
     */
    void setName(String name);

    /**
     * Get tool description.
     * @return the tool description
     */
    String getDescription();

    /**
     * Set tool description.
     * @param description the description to set
     */
    void setDescription(String description);

    /**
     * Validate if the input is good.
     * @param parameters input parameters
     * @return true if the input is valid
     */
    boolean validate(Map<String, String> parameters);

    /**
     * Check if should end the whole CoT immediately.
     * For example, if some critical error detected like high memory pressure,
     * the tool may end the whole CoT process by returning true.
     * @param input tool input string
     * @param toolParameters map of input parameters
     * @return true as a signal to CoT to end the chain, false to continue CoT
     */
    default boolean end(String input, Map<String, String> toolParameters) {
        return false;
    }

    /**
     * The tool runs against the original human input.
     * @return
     */
    default boolean useOriginalInput() {
        return false;
    }

    /**
     * Whether the tool needs the history of the conversation.
     * @return if true, agent runner should pass history information as input parameter to this tool
     */
    default boolean needHistory() {
        return false;
    }

    /**
     * Tool factory which can create instance of {@link Tool}.
     * @param <T> The subclass this factory produces
     */
    interface Factory<T extends Tool> {
        /**
         * Create an instance of this tool.
         *
         * @param params Parameters for the tool
         * @return an instance of this tool
         */
        T create(Map<String, Object> params);

        /**
         * Get the default description of this tool.
         * @return the default description
         */
        String getDefaultDescription();

        /**
         * Get the default type of this tool.
         * @return the default tool type
         */
        String getDefaultType();

        /**
         * Get the default version of this tool
         * @return the default tool version
         */
        String getDefaultVersion();
    }
}
