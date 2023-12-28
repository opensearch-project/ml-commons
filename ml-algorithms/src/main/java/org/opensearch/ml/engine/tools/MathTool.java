/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.opensearch.ml.engine.utils.ScriptUtils.executeScript;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.spi.tools.AbstractTool;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;
import org.opensearch.ml.repackage.com.google.common.collect.ImmutableMap;
import org.opensearch.script.ScriptService;

import lombok.NonNull;
import lombok.Setter;

@ToolAnnotation(MathTool.TYPE)
public class MathTool extends AbstractTool {
    public static final String TYPE = "MathTool";
    public static final String INPUT = "input";
    @Setter
    private ScriptService scriptService;

    private static String DEFAULT_DESCRIPTION = "Use this tool to calculate any math problem.";

    public MathTool(@NonNull ScriptService scriptService) {
        super(TYPE, DEFAULT_DESCRIPTION);
        this.scriptService = scriptService;
    }

    @Override
    public <T> void run(Map<String, String> parameters, ActionListener<T> listener) {
        if (parameters.get(INPUT) == null) {
            listener.onFailure(new IllegalArgumentException("Parameter input is required for MathTool tool"));
            return;
        }
        String input = parameters.get(INPUT);

        input = input.replaceAll(",", "");
        if (input.contains("/")) {
            String patternStr = "\\d+(\\.\\d+)?";
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(input);
            if (matcher.find()) {
                String match = matcher.group(0);
                double result = Double.parseDouble(match);
                input = input.replaceFirst(patternStr, result + "");
            }
        }
        String result = executeScript(scriptService, input + "+ \"\"", ImmutableMap.of());
        listener.onResponse((T) result);
    }

    @Override
    public boolean validate(Map<String, String> parameters) {
        if (parameters.get(INPUT) == null) {
            return false;
        }

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

        public void init(@NonNull ScriptService scriptService) {
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
