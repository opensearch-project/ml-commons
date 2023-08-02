/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.utils;

import com.google.gson.Gson;
import lombok.extern.log4j.Log4j2;
import org.opensearch.ml.common.connector.MLPostProcessFunction;
import org.opensearch.ml.common.connector.MLPreProcessFunction;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.script.Script;
import org.opensearch.script.ScriptService;
import org.opensearch.script.ScriptType;
import org.opensearch.script.TemplateScript;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

@Log4j2
public class ScriptUtils {

    public static final Gson gson;

    static {
        gson = new Gson();
    }

    public static Optional<String> executePreprocessFunction(ScriptService scriptService,
                                                             String preProcessFunction,
                                                             Map<String, Object> params) {
        if (MLPreProcessFunction.contains(preProcessFunction)) {
            preProcessFunction = MLPreProcessFunction.get(preProcessFunction);
        }
        if (preProcessFunction != null) {
            return Optional.ofNullable(executeScript(scriptService, preProcessFunction, params));
        }
        return Optional.empty();
    }

    public static Optional<String> executePostprocessFunction(ScriptService scriptService,
                                                              String postProcessFunction,
                                                              String resultJson) {
        Map<String, Object> result = StringUtils.fromJson(resultJson, "result");
        if (MLPostProcessFunction.contains(postProcessFunction)) {
            postProcessFunction = MLPostProcessFunction.get(postProcessFunction);
        }
        if (postProcessFunction != null) {
            return Optional.ofNullable(executeScript(scriptService, postProcessFunction, result));
        }
        return Optional.empty();
    }

    public static String executeScript(ScriptService scriptService, String painlessScript, Map<String, Object> params) {
        long start = System.nanoTime();
        Script script = new Script(ScriptType.INLINE, "painless", painlessScript, Collections.emptyMap());
        TemplateScript templateScript = scriptService.compile(script, TemplateScript.CONTEXT).newInstance(params);
        long end = System.nanoTime();
        double durationInMs = (end - start) / 1e6;
        log.info("----------------- painless script execution time: {} ms", durationInMs);
        return templateScript.execute();
    }
}
