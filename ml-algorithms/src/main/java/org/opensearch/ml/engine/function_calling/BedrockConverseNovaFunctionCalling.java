/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.function_calling;

/**
 * Function calling implementation for Amazon Nova models via Bedrock Converse API.
 *
 * Nova models use the same Bedrock Converse API format as Claude, so this class
 * extends {@link BedrockConverseFunctionCalling} and inherits all its behavior.
 * The tool definitions, tool call responses, and tool result formats are identical.
 *
 * This separate class exists to:
 * 1. Provide a distinct LLM interface identifier ("bedrock/converse/nova")
 * 2. Allow Nova-specific customizations in the future if needed
 */
public class BedrockConverseNovaFunctionCalling extends BedrockConverseFunctionCalling {}
