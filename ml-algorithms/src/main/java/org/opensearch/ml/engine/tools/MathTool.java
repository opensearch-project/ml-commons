/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import lombok.Getter;
import lombok.Setter;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;
import org.opensearch.ml.repackage.com.google.common.collect.ImmutableMap;
import org.opensearch.script.ScriptService;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.opensearch.ml.engine.utils.ScriptUtils.executeScript;

@ToolAnnotation(MathTool.TYPE)
public class MathTool implements Tool {
    public static final String TYPE = "MathTool";

    @Setter @Getter
    private String name = TYPE;

    @Setter
    private ScriptService scriptService;

    private static String DEFAULT_DESCRIPTION = "Use this tool to calculate any math problem.";
    @Getter @Setter
    private String description = DEFAULT_DESCRIPTION;

    public MathTool(ScriptService scriptService) {
        this.scriptService = scriptService;
    }

    @Override
    public <T> void run(Map<String, String> parameters, ActionListener<T> listener) {
        String input = parameters.get("input");

        input = input.replaceAll(",", "");
        if (input.contains("/")) {
            String patternStr = "\\d+(\\.\\d+)?";
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(input);
            if (matcher.find()) {
                String match = matcher.group(0);
                double result = Double.parseDouble(match);
                input = input.replaceFirst(patternStr, result+"");
            }
        }
        String result = executeScript(scriptService, input + "+ \"\"", ImmutableMap.of());
        listener.onResponse((T)result);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public String getVersion() {
        return null;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void setName(String s) {
        this.name = s;
    }

    @Override
    public boolean validate(Map<String, String> parameters) {
        try {
            run(parameters);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public static class Factory implements Tool.Factory<MathTool> {
        private ScriptService scriptService;

        private static Factory INSTANCE;
        public static Factory getInstance() {
            if (INSTANCE != null) {
                return INSTANCE;
            }
            synchronized (MathTool.class) {
                if (INSTANCE != null) {
                    return INSTANCE;
                }
                INSTANCE = new Factory();
                return INSTANCE;
            }
        }

        public void init(ScriptService scriptService) {
            this.scriptService = scriptService;
        }

        @Override
        public MathTool create(Map<String, Object> map) {
            return new MathTool(scriptService);
        }

        @Override
        public String getDefaultDescription() {
            return DEFAULT_DESCRIPTION;
        }
    }
}