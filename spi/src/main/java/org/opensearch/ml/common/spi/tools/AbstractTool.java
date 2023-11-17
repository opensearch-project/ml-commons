package org.opensearch.ml.common.spi.tools;

import lombok.Getter;
import lombok.Setter;

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
    private Parser inputParser;

    /**
     * Parser used to write tool output.
     */
    @Setter
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
        this.version = version;
    }

    public Parser getInputParser() {
        return this.inputParser;
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

