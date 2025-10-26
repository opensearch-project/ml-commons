/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import java.util.Map;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.spi.tools.Tool;

import lombok.extern.log4j.Log4j2;

/**
 * Placeholder tool for AG-UI frontend tools.
 * Frontend tools are not executed on the backend - they are executed in the browser.
 * This placeholder allows the LLM to see frontend tools in the unified tool list.
 */
@Log4j2
public class AGUIFrontendTool implements Tool {
    private final String toolName;
    private final String toolDescription;
    private final Map<String, Object> toolAttributes;

    public AGUIFrontendTool(String toolName, String toolDescription, Map<String, Object> toolAttributes) {
        this.toolName = toolName;
        this.toolDescription = toolDescription;
        this.toolAttributes = toolAttributes;
    }

    @Override
    public String getName() {
        return toolName;
    }

    @Override
    public void setName(String name) {}

    @Override
    public String getDescription() {
        return toolDescription;
    }

    @Override
    public void setDescription(String description) {}

    @Override
    public Map<String, Object> getAttributes() {
        return toolAttributes;
    }

    @Override
    public void setAttributes(Map<String, Object> attributes) {}

    @Override
    @SuppressWarnings("unchecked")
    public <T> void run(Map<String, String> parameters, ActionListener<T> listener) {
        log.debug("AG-UI: Frontend tool {} executed with parameters: {}", toolName, parameters);
        String errorResult = String
            .format(
                "Error: Tool '%s' is a frontend tool and should be called via function calling in the final response, "
                    + "not during ReAct execution.",
                toolName
            );
        listener.onResponse((T) errorResult);
    }

    @Override
    public boolean validate(Map<String, String> parameters) {
        return true;
    }

    @Override
    public String getType() {
        return "AGUIFrontendTool";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }
}
