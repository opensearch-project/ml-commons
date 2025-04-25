package org.opensearch.ml.rest.mcpserver;

import java.util.Map;

import org.opensearch.ml.common.spi.tools.Tool;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ToolFactoryWrapper {

    private Map<String, Tool.Factory> toolsFactories;
}
