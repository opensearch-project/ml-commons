package org.opensearch.ml.engine.tools;

import lombok.Getter;
import lombok.Setter;
import org.opensearch.ml.common.spi.tools.Parser;
import org.opensearch.ml.common.spi.tools.Tool;

import java.util.Map;

public abstract class AbstractTool implements Tool {

    /**
     * Name of the tool to be used in prompt.
     */
    @Setter
    @Getter
    private String name;

    /**
     * Default description of the tool. This description will be used by LLM to select next tool to execute.
     */
    @Getter
    @Setter
    private String description;

    /**
     * Tool type mapping to the corresponding run function. Tool type will be used by agent framework to identify the tool.
     */
    @Getter
    private String type;

    /**
     * Current tool version.
     */
    @Getter
    protected String version;

    /**
     * Parser used to read tool input.
     */
    @Setter
    protected Parser inputParser;

    /**
     * Parser used to write tool output.
     */
    @Setter
    protected Parser outputParser;

    /**
     * Default tool constructor.
     *
     * @param type
     * @param name
     * @param description
     */
    protected AbstractTool(String type, String name, String description) {
        this.type = type;
        this.name = name;
        this.description = description;
    }

    protected AbstractTool(String type, String description) {
        this(type, type, description);
    }

    /**
     * Validate tool input and check if request could be processed by the tool.
     *
     * @param parameters
     * @return
     */
    @Override
    public abstract boolean validate(Map<String, String> parameters);

}

