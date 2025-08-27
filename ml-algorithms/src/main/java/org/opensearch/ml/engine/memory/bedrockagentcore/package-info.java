/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * Bedrock AgentCore memory integration for OpenSearch ML Commons.
 * 
 * This package provides memory storage using AWS Bedrock AgentCore service,
 * allowing OpenSearch ML agents to store and retrieve conversation history
 * in Bedrock's managed memory service.
 * 
 * Key components:
 * - BedrockAgentCoreMemory: Main memory implementation
 * - BedrockAgentCoreMemoryRecord: Memory record data structure
 * - BedrockAgentCoreClient: AWS SDK wrapper
 * - BedrockAgentCoreAdapter: Format conversion utilities
 * - BedrockAgentCoreCredentialManager: AWS credential management
 */
package org.opensearch.ml.engine.memory.bedrockagentcore;
