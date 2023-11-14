/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.opensearch.client.Client;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.tools.AbstractTool;
import org.opensearch.ml.common.spi.tools.Parser;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;
import org.opensearch.ml.engine.utils.ScriptUtils;
import org.opensearch.script.ScriptService;

import java.util.List;
import java.util.Map;

import static org.opensearch.ml.common.utils.StringUtils.gson;


@Log4j2
@ToolAnnotation(PainlessScriptTool.TYPE)
public class PainlessScriptTool extends AbstractTool {
    public static final String TYPE = "PainlessScriptTool";
    private static String DEFAULT_DESCRIPTION = "Use this tool to get index information.";

    private Client client;
    private ScriptService scriptService;

    public PainlessScriptTool(Client client, ScriptService scriptService) {
        super(TYPE, DEFAULT_DESCRIPTION);
        this.client = client;
        this.scriptService = scriptService;

        this.setOutputParser(new Parser() {
            @Override
            public Object parse(Object o) {
                List<ModelTensors> mlModelOutputs = (List<ModelTensors>) o;
                return mlModelOutputs.get(0).getMlModelTensors().get(0).getDataAsMap().get("response");
            }
        });
    }



    @Override
    public <T> void run(Map<String, String> toolSpec, Map<String, String> parameters, ActionListener<T> listener) {
        String painlessScript = parameters.get("script");
        Map<String, Object> params = gson.fromJson(parameters.get("script_params"), Map.class);
        String s = ScriptUtils.executeScript(scriptService, painlessScript, params) + "";
        listener.onResponse((T)s);
    }

    @Override
    public boolean validate(Map<String, String> toolSpec, Map<String, String> parameters) {
        if (parameters == null || parameters.size() == 0) {
            return false;
        }
        return true;
    }

}