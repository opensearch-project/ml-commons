/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import lombok.Setter;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.spi.tools.AbstractTool;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;
import org.opensearch.ml.repackage.com.google.common.collect.ImmutableMap;
import org.opensearch.script.ScriptService;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.opensearch.ml.engine.utils.ScriptUtils.executeScript;

@ToolAnnotation(MathTool.TYPE)
public class MathTool extends AbstractTool {
    public static final String TYPE = "MathTool";
    private static String DEFAULT_DESCRIPTION = "Use this tool to calculate any math problem.";

    @Setter
    private ScriptService scriptService;

    public MathTool(ScriptService scriptService) {
        super(TYPE, DEFAULT_DESCRIPTION);
        this.scriptService = scriptService;
    }

    @Override
    public <T> void run(Map<String, String> toolSpec, Map<String, String> parameters, ActionListener<T> listener) {
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
    public boolean validate(Map<String, String> toolSpec, Map<String, String> parameters) {
        try {
            run(parameters);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

}