package org.opensearch.ml.engine.tools;

import lombok.NonNull;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.script.ScriptService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Factory class to initiate and fetch ML common tools.
 */
public class ToolsFactory {
    private static final Map<String, Tool> AGENT_TOOLS = new HashMap<>();

    public ToolsFactory(Client client, ScriptService scriptService, ClusterService clusterService,
                        NamedXContentRegistry xContentRegistry, Map<String, Tool> externalTools) {
        if (externalTools != null && !externalTools.isEmpty()) {
            AGENT_TOOLS.putAll(externalTools);
        }
        AGENT_TOOLS.put(MLModelTool.TYPE, new MLModelTool(client));
        AGENT_TOOLS.put(MathTool.TYPE, new MathTool(scriptService));
        AGENT_TOOLS.put(CatIndexTool.TYPE, new CatIndexTool(client, clusterService));
        AGENT_TOOLS.put(VectorDBTool.TYPE, new VectorDBTool(client, xContentRegistry));
        AGENT_TOOLS.put(PainlessScriptTool.TYPE, new PainlessScriptTool(client, scriptService));
        AGENT_TOOLS.put(SearchAlertsTool.TYPE, new SearchAlertsTool(client));
        AGENT_TOOLS.put(VisualizationsTool.TYPE, new VisualizationsTool(client));

    }

    public Tool getTool(@NonNull final String toolType) {
        return Optional.ofNullable(AGENT_TOOLS.get(toolType))
                .orElseThrow(() -> new IllegalArgumentException("Unsupported tool: " + toolType));
    }

    public List<Tool> getAllTools() {
        return new ArrayList<>(AGENT_TOOLS.values());
    }

}