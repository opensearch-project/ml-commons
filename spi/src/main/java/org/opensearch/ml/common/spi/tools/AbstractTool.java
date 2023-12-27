package org.opensearch.ml.common.spi.tools;

import java.util.Map;

/**
 * Abstract tool used to simplify tool creation.
 */
public abstract class AbstractTool implements Tool {

    /**
     * Name of the tool to be used in prompt.
     */
    private String name;

    /**
     * Default description of the tool. This description will be used by LLM to select next tool to execute.
     */
    private String description;

    /**
     * Tool type mapping to the corresponding run function. Tool type will be used by agent framework to identify the tool.
     */
    private String type;

    /**
     * Current tool version.
     */
    private String version;

    /**
     * Parser used to read tool input.
     */
    private Parser inputParser;

    /**
     * Parser used to write tool output.
     */
    private Parser outputParser;

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

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    @Override
    public String getType() {
        return this.type;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    @Override
    public String getVersion() {
        return this.version;
    }

    @Override
    public void setInputParser(Parser inputParser) {
        this.inputParser = inputParser;
    }

    public Parser getInputParser() {
        return this.inputParser;
    }

    @Override
    public void setOutputParser(Parser outputParser) {
        this.outputParser = outputParser;
    }

    public Parser getOutputParser() {
        return this.outputParser;
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
