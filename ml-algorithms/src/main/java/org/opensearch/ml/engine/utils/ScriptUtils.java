/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.utils;

import static org.opensearch.ml.common.utils.StringUtils.addDefaultMethod;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.script.Script;
import org.opensearch.script.ScriptService;
import org.opensearch.script.ScriptType;
import org.opensearch.script.TemplateScript;

import com.google.common.collect.ImmutableMap;

public class ScriptUtils {

    public static Optional<String> executePreprocessFunction(
        ScriptService scriptService,
        String preProcessFunction,
        List<String> inputSentences
    ) {
        return Optional.ofNullable(executeScript(scriptService, preProcessFunction, ImmutableMap.of("text_docs", inputSentences)));
    }

    public static Optional<String> executePostProcessFunction(ScriptService scriptService, String postProcessFunction, String resultJson) {
        Map<String, Object> result = StringUtils.fromJson(resultJson, "result");
        if (postProcessFunction != null) {
            return Optional.ofNullable(executeScript(scriptService, addDefaultMethod(postProcessFunction), result));
        }
        return Optional.empty();
    }

    public static String executeScript(ScriptService scriptService, String painlessScript, Map<String, Object> params) {
        Script script = new Script(ScriptType.INLINE, "painless", painlessScript, Collections.emptyMap());
        TemplateScript templateScript = scriptService.compile(script, TemplateScript.CONTEXT).newInstance(params);
        return templateScript.execute();
    }
}
