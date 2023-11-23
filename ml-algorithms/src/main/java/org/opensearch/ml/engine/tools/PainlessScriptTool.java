/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.opensearch.ml.common.utils.StringUtils.gson;

import java.util.List;
import java.util.Map;

import org.opensearch.client.Client;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.tools.Parser;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.spi.tools.ToolAnnotation;
import org.opensearch.ml.engine.utils.ScriptUtils;
import org.opensearch.script.ScriptService;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

@Log4j2
@ToolAnnotation(PainlessScriptTool.TYPE)
public class PainlessScriptTool implements Tool {
    public static final String TYPE = "PainlessScriptTool";

    @Setter
    @Getter
    private String name = TYPE;
    private static String DEFAULT_DESCRIPTION = "Use this tool to get index information.";
    @Getter
    @Setter
    private String description = DEFAULT_DESCRIPTION;
    private Client client;
    private String modelId;
    @Setter
    private Parser inputParser;
    @Setter
    private Parser outputParser;
    private ScriptService scriptService;

    public PainlessScriptTool(Client client, ScriptService scriptService) {
        this.client = client;
        this.scriptService = scriptService;

        outputParser = new Parser() {
            @Override
            public Object parse(Object o) {
                List<ModelTensors> mlModelOutputs = (List<ModelTensors>) o;
                return mlModelOutputs.get(0).getMlModelTensors().get(0).getDataAsMap().get("response");
            }
        };
    }

    @Override
    public <T> void run(Map<String, String> parameters, ActionListener<T> listener) {
        String painlessScript = parameters.get("script");
        Map<String, Object> params = gson.fromJson(parameters.get("script_params"), Map.class);
        String s = ScriptUtils.executeScript(scriptService, painlessScript, params) + "";
        listener.onResponse((T) s);
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
        if (parameters == null || parameters.size() == 0) {
            return false;
        }
        return true;
    }

    public static class Factory implements Tool.Factory<PainlessScriptTool> {
        private Client client;
        private ScriptService scriptService;

        private static Factory INSTANCE;

        public static Factory getInstance() {
            if (INSTANCE != null) {
                return INSTANCE;
            }
            synchronized (PainlessScriptTool.class) {
                if (INSTANCE != null) {
                    return INSTANCE;
                }
                INSTANCE = new Factory();
                return INSTANCE;
            }
        }

        public void init(Client client, ScriptService scriptService) {
            this.client = client;
            this.scriptService = scriptService;
        }

        @Override
        public PainlessScriptTool create(Map<String, Object> map) {
            return new PainlessScriptTool(client, scriptService);
        }

        @Override
        public String getDefaultDescription() {
            return DEFAULT_DESCRIPTION;
        }
    }
}
