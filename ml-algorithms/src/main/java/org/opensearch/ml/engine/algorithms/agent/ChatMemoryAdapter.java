/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import java.util.List;

import org.opensearch.core.action.ActionListener;

/**
 * Common interface for modern memory types supporting ChatMessage-based interactions.
 * 
 * <p>This interface provides a unified abstraction for different memory backend implementations,
 * enabling consistent interaction patterns across various memory storage systems. It supports
 * both conversation management and detailed trace data storage for comprehensive agent behavior
 * tracking.</p>
 * 
 * <h3>Supported Memory Types:</h3>
 * <ul>
 *   <li><strong>Agentic Memory</strong> - Local cluster-based intelligent memory system</li>
 *   <li><strong>Remote Agentic Memory</strong> - Distributed agentic memory implementation</li>
 *   <li><strong>Bedrock AgentCore Memory</strong> - AWS Bedrock agent memory integration</li>
 *   <li><strong>Future memory types</strong> - Extensible for additional implementations</li>
 * </ul>
 * 
 * <h3>Core Capabilities:</h3>
 * <ul>
 *   <li>Message retrieval in standardized ChatMessage format</li>
 *   <li>Conversation and session management</li>
 *   <li>Interaction persistence with metadata support</li>
 *   <li>Tool execution trace data storage</li>
 *   <li>Dynamic interaction updates</li>
 * </ul>
 * 
 * <p><strong>Note:</strong> ConversationIndex uses a separate legacy pipeline for backward compatibility
 * and is not part of this modern interface hierarchy.</p>
 * 
 * @see ChatMessage
 * @see AgenticMemoryAdapter
 */
public interface ChatMemoryAdapter {
    /**
     * Retrieve conversation messages in ChatMessage format
     * @param listener ActionListener to handle the response
     */
    void getMessages(ActionListener<List<ChatMessage>> listener);

    /**
     * Get the conversation/session identifier
     * @return conversation ID or session ID
     */
    String getConversationId();

    /**
     * This is the main memory container ID used to identify the memory container
     * in the memory management system.
     * @return
     */
    String getMemoryContainerId();

    /**
     * Save interaction to memory (optional implementation)
     * @param question User question
     * @param response AI response  
     * @param parentId Parent interaction ID
     * @param traceNum Trace number
     * @param action Action performed
     * @param listener ActionListener to handle the response
     */
    default void saveInteraction(
        String question,
        String response,
        String parentId,
        Integer traceNum,
        String action,
        ActionListener<String> listener
    ) {
        listener.onFailure(new UnsupportedOperationException("Save not implemented"));
    }

    /**
     * Update existing interaction with additional information
     * @param interactionId Interaction ID to update
     * @param updateFields Fields to update (e.g., final answer, additional info)
     * @param listener ActionListener to handle the response
     */
    default void updateInteraction(String interactionId, java.util.Map<String, Object> updateFields, ActionListener<Void> listener) {
        listener.onFailure(new UnsupportedOperationException("Update interaction not implemented"));
    }

    /**
     * Save trace data as tool invocation data in working memory.
     * 
     * <p>This method provides a standardized way to store detailed information about
     * tool executions, enabling comprehensive tracking and analysis of agent behavior.
     * Implementations should store this data in a structured format that supports
     * later retrieval and analysis.</p>
     * 
     * <p>Default implementation throws UnsupportedOperationException. Memory adapters
     * that support trace data storage should override this method.</p>
     * 
     * @param toolName Name of the tool that was executed (required)
     * @param toolInput Input parameters passed to the tool (may be null)
     * @param toolOutput Output/response from the tool execution (may be null)
     * @param parentMemoryId Parent memory ID to associate this trace with (may be null)
     * @param traceNum Trace sequence number for ordering (may be null)
     * @param action Action/origin identifier for categorization (may be null)
     * @param listener ActionListener to handle the response with created trace ID
     * @throws UnsupportedOperationException if the implementation doesn't support trace data storage
     */
    default void saveTraceData(
        String toolName,
        String toolInput,
        String toolOutput,
        String parentMemoryId,
        Integer traceNum,
        String action,
        ActionListener<String> listener
    ) {
        listener.onFailure(new UnsupportedOperationException("Save trace data not implemented"));
    }
}
