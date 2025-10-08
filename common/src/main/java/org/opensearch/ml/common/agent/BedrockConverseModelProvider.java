/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import static org.opensearch.ml.common.connector.AbstractConnector.ACCESS_KEY_FIELD;
import static org.opensearch.ml.common.connector.AbstractConnector.SECRET_KEY_FIELD;
import static org.opensearch.ml.common.connector.AbstractConnector.SESSION_TOKEN_FIELD;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.connector.AwsConnector;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.ConnectorProtocols;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;

/**
 * Model provider for Bedrock Converse API
 */
// ToDo: further simplify and make provider only give the necessary constants and have the core provider actual create the rest
public class BedrockConverseModelProvider extends ModelProvider {
    
    private static final String REQUEST_BODY_TEMPLATE = 
        "{\"system\": [{\"text\": \"${parameters.system_prompt}\"}], " +
        "\"messages\": [${parameters._chat_history:-}{\"role\":\"user\",\"content\":[{\"text\":\"${parameters.prompt}\"}]}${parameters._interactions:-}]" +
        "${parameters.tool_configs:-} }";
    
    @Override
    public Connector createConnector(String modelName, Map<String, String> credential, Map<String, String> modelParameters) {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("region", "us-east-1"); // Default region, can be overridden
        parameters.put("service_name", "bedrock");
        parameters.put("model", modelName);
        
        // Override with any provided model parameters
        if (modelParameters != null) {
            parameters.putAll(modelParameters);
        }
        
        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", "application/json");
        
        ConnectorAction predictAction = ConnectorAction.builder()
            .actionType(ConnectorAction.ActionType.PREDICT)
            .method("POST")
            .url("https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/converse")
            .headers(headers)
            .requestBody(REQUEST_BODY_TEMPLATE)
            .build();
        
        return AwsConnector.awsConnectorBuilder()
            .name("Auto-generated Bedrock Converse connector")
            .description("Auto-generated connector for Bedrock Converse API")
            .version("1")
            .protocol(ConnectorProtocols.AWS_SIGV4)
            .parameters(parameters)
            .credential(credential != null ? credential : new HashMap<>())
            .actions(List.of(predictAction))
            .build();
    }
    
    @Override
    public MLRegisterModelInput createModelInput(String modelName, Connector connector, Map<String, String> modelParameters) {
        return MLRegisterModelInput.builder()
            .functionName(FunctionName.REMOTE)
            .modelName("Auto-generated model for " + modelName)
            .description("Auto-generated model for agent")
            .connector(connector)
            .deployModel(false) // Don't auto-deploy
            .build();
    }
    
    @Override
    public String getProtocol() {
        return ConnectorProtocols.AWS_SIGV4;
    }
    
    @Override
    public String getServiceName() {
        return "bedrock";
    }
    
    @Override
    public String getLLMInterface() {
        return "bedrock/converse/claude";
    }
}