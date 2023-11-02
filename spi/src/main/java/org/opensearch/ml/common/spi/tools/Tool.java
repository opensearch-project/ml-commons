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
     * Run tool and return response.
     * @param parameters input parameters
     * @return the tool's output
     * @param <T> The output type
     */
    default <T> T run(Map<String, String> parameters) {
        return null;
    };

    /**
     * Run tool and return response asynchronously.
     * @param parameters input parameters
     * @param listener an action listener for the response
     * @param <T> The output type
     */
    default <T> void run(Map<String, String> parameters, ActionListener<T> listener) {};

    /**
     * Set input parser.
     * @param parser
     */
    default void setInputParser(Parser<?, ?> parser) {};

    /**
     * Set output parser.
     * @param parser
     */
    default void setOutputParser(Parser<?, ?> parser) {};

    /**
     * Get tool type mapping to the run function.
     * @return
     */
    String getType();

    /**
     * Get tool version.
     * @return
     */
    String getVersion();

    /**
     * Get tool name which is displayed in prompt.
     * @return
     */
    String getName();

    /**
     * Set tool name which is displayed in prompt.
     * @param name
     */
    void setName(String name);

    /**
     * Get tool description.
     * @return
     */
    String getDescription();

    /**
     * Set tool description.
     * @param description
     */
    void setDescription(String description);

    /**
     * Validate if the input is good.
     * @param parameters input parameters
     * @return
     */
    boolean validate(Map<String, String> parameters);

    /**
     * Check if should end the whole CoT immediately.
     * For example, if some critical error detected like high memory pressure,
     * the tool may end the whole CoT process by returning true.
     * @param input
     * @param toolParameters
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
     * Tool factory which can create instance of {@link Tool}.
     * @param <T> The subclass this factory produces
     */
    interface Factory<T extends Tool> {
        T create(Map<String, Object> params);
        String getDefaultDescription();
    }
}
