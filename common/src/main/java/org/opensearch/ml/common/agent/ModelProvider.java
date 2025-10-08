/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import java.util.Map;

import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;

/**
 * Abstract base class for model providers
 */
public abstract class ModelProvider {
    
    /**
     * Creates a connector for this model provider
     * @param modelName the model name (e.g., "us.anthropic.claude-3-7-sonnet-20250219-v1:0")
     * @param credential credential map for the connector
     * @param modelParameters additional model parameters
     * @return configured Connector
     */
    public abstract Connector createConnector(String modelName, Map<String, String> credential, Map<String, String> modelParameters);
    
    /**
     * Creates MLRegisterModelInput for this model provider
     * @param modelName the model name
     * @param connector the connector to use
     * @param modelParameters additional model parameters
     * @return configured MLRegisterModelInput
     */
    public abstract MLRegisterModelInput createModelInput(String modelName, Connector connector, Map<String, String> modelParameters);
    
    /**
     * Get the protocol for this provider
     */
    public abstract String getProtocol();
    
    /**
     * Get the service name for this provider
     */
    public abstract String getServiceName();
    
    /**
     * Gets the LLM interface for function calling
     * @return the LLM interface string, or null if not supported
     */
    public abstract String getLLMInterface();
}