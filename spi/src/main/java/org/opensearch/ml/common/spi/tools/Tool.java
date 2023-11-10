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
     * Get tool name.
     * @return
     */
    String getName();

    /**
     * Get tool alias.
     * @return
     */
    String getAlias();

    /**
     * Set tool alias.
     * @param alias
     */
    void setAlias(String alias);

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
     * Tool factory which can create instance of {@link Tool}.
     * @param <T> The subclass this factory produces
     */
    interface Factory<T extends Tool> {
        T create(Map<String, Object> params);
        String getDefaultDescription();
    }
}
