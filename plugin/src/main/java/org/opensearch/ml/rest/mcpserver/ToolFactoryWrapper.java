/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest.mcpserver;

import java.util.Map;

import org.opensearch.ml.common.spi.tools.Tool;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ToolFactoryWrapper {

    private Map<String, Tool.Factory> toolsFactories;
}
