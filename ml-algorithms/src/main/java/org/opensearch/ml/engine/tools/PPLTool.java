package org.opensearch.ml.engine.tools;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.spi.tools.Parser;
import org.opensearch.ml.common.spi.tools.Tool;

import java.util.Map;

public class PPLTool implements Tool {
    @Override
    public <T> T run(Map<String, String> parameters) {
        return Tool.super.run(parameters);
    }

    @Override
    public <T> void run(Map<String, String> parameters, ActionListener<T> listener) {
        Tool.super.run(parameters, listener);
    }

    @Override
    public void setInputParser(Parser<?, ?> parser) {
        Tool.super.setInputParser(parser);
    }

    @Override
    public void setOutputParser(Parser<?, ?> parser) {
        Tool.super.setOutputParser(parser);
    }

    @Override
    public String getType() {
        return null;
    }

    @Override
    public String getVersion() {
        return null;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public void setName(String s) {

    }

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public void setDescription(String s) {

    }

    @Override
    public boolean validate(Map<String, String> map) {
        return false;
    }

    @Override
    public boolean end(String input, Map<String, String> toolParameters) {
        return Tool.super.end(input, toolParameters);
    }

    @Override
    public boolean useOriginalInput() {
        return Tool.super.useOriginalInput();
    }
}
