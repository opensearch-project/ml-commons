/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.opensearch.ml.common.connector.ConnectorAction.ActionType.PREDICT;
import static org.opensearch.ml.common.utils.ModelInterfaceUtils.ModelInterfaceSchema;
import static org.opensearch.ml.common.utils.ModelInterfaceUtils.updateRegisterModelInputModelInterfaceFieldsByConnector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Spy;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.connector.MLPostProcessFunction;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;

public class ModelInterfaceUtilsTest {
    @Spy
    MLRegisterModelInput registerModelInputWithInnerConnector;

    @Spy
    MLRegisterModelInput registerModelInputWithStandaloneConnector;

    @Spy
    public HttpConnector connector;

    public ConnectorAction connectorActionWithPostProcessFunction;

    public ConnectorAction connectorActionWithoutPostProcessFunction;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        registerModelInputWithInnerConnector = MLRegisterModelInput
            .builder()
            .modelName("test-model-with-inner-connector")
            .functionName(FunctionName.REMOTE)
            .build();

        registerModelInputWithStandaloneConnector = MLRegisterModelInput
            .builder()
            .modelName("test-model-with-stand-alone-connector")
            .functionName(FunctionName.REMOTE)
            .build();

        connectorActionWithPostProcessFunction = ConnectorAction
            .builder()
            .actionType(PREDICT)
            .method("POST")
            .url("http:///mock")
            .requestBody("{\"input\": \"${parameters.input}\"}")
            .postProcessFunction("test-post-process-function")
            .build();

        connectorActionWithoutPostProcessFunction = ConnectorAction
            .builder()
            .actionType(PREDICT)
            .method("POST")
            .url("http:///mock")
            .requestBody("{\"input\": \"${parameters.input}\"}")
            .build();
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorBEDROCK_AI21_LABS_JURASSIC2_MID_V1_MODEL_INTERFACE() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("service_name", "bedrock");
        parameters.put("model", "ai21.j2-mid-v1");
        connector = HttpConnector
            .builder()
            .protocol("http")
            .parameters(parameters)
            .actions(List.of(connectorActionWithPostProcessFunction))
            .build();
        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertEquals(
            registerModelInputWithStandaloneConnector.getModelInterface(),
            ModelInterfaceSchema.BEDROCK_AI21_LABS_JURASSIC2_MID_V1.getInterface()
        );
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorBEDROCK_AI21_LABS_JURASSIC2_MID_V1_RAW_MODEL_INTERFACE() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("service_name", "bedrock");
        parameters.put("model", "ai21.j2-mid-v1");
        connector = HttpConnector
            .builder()
            .protocol("http")
            .parameters(parameters)
            .actions(List.of(connectorActionWithoutPostProcessFunction))
            .build();
        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertEquals(
            registerModelInputWithStandaloneConnector.getModelInterface(),
            ModelInterfaceSchema.BEDROCK_AI21_LABS_JURASSIC2_MID_V1_RAW.getInterface()
        );
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorBEDROCK_ANTHROPIC_CLAUDE_V3_SONNET_MODEL_INTERFACE() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("service_name", "bedrock");
        parameters.put("model", "anthropic.claude-3-sonnet-20240229-v1:0");
        connector = HttpConnector
            .builder()
            .protocol("http")
            .parameters(parameters)
            .actions(List.of(connectorActionWithPostProcessFunction))
            .build();

        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertEquals(
            registerModelInputWithStandaloneConnector.getModelInterface(),
            ModelInterfaceSchema.BEDROCK_ANTHROPIC_CLAUDE_V3_SONNET.getInterface()
        );
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorBEDROCK_ANTHROPIC_CLAUDE_V2_MODEL_INTERFACE() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("service_name", "bedrock");
        parameters.put("model", "anthropic.claude-v2");
        connector = HttpConnector
            .builder()
            .protocol("http")
            .parameters(parameters)
            .actions(List.of(connectorActionWithPostProcessFunction))
            .build();

        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertEquals(
            registerModelInputWithStandaloneConnector.getModelInterface(),
            ModelInterfaceSchema.BEDROCK_ANTHROPIC_CLAUDE_V2.getInterface()
        );
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorBEDROCK_COHERE_EMBED_ENGLISH_V3_MODEL_INTERFACE() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("service_name", "bedrock");
        parameters.put("model", "cohere.embed-english-v3");

        connectorActionWithPostProcessFunction = ConnectorAction
            .builder()
            .actionType(PREDICT)
            .method("POST")
            .url("http:///mock")
            .requestBody("{\"input\": \"${parameters.input}\"}")
            .postProcessFunction(MLPostProcessFunction.COHERE_EMBEDDING)
            .build();

        connector = HttpConnector
            .builder()
            .protocol("http")
            .parameters(parameters)
            .actions(List.of(connectorActionWithPostProcessFunction))
            .build();

        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertEquals(
            registerModelInputWithStandaloneConnector.getModelInterface(),
            ModelInterfaceSchema.BEDROCK_COHERE_EMBED_ENGLISH_V3.getInterface()
        );
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorBEDROCK_COHERE_EMBED_ENGLISH_V3_RAW_MODEL_INTERFACE() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("service_name", "bedrock");
        parameters.put("model", "cohere.embed-english-v3");
        connector = HttpConnector
            .builder()
            .protocol("http")
            .parameters(parameters)
            .actions(List.of(connectorActionWithoutPostProcessFunction))
            .build();
        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertEquals(
            registerModelInputWithStandaloneConnector.getModelInterface(),
            ModelInterfaceSchema.BEDROCK_COHERE_EMBED_ENGLISH_V3_RAW.getInterface()
        );
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorBEDROCK_COHERE_EMBED_MULTILINGUAL_V3_MODEL_INTERFACE() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("service_name", "bedrock");
        parameters.put("model", "cohere.embed-multilingual-v3");

        connectorActionWithPostProcessFunction = ConnectorAction
            .builder()
            .actionType(PREDICT)
            .method("POST")
            .url("http:///mock")
            .requestBody("{\"input\": \"${parameters.input}\"}")
            .postProcessFunction(MLPostProcessFunction.COHERE_EMBEDDING)
            .build();

        connector = HttpConnector
            .builder()
            .protocol("http")
            .parameters(parameters)
            .actions(List.of(connectorActionWithPostProcessFunction))
            .build();

        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertEquals(
            registerModelInputWithStandaloneConnector.getModelInterface(),
            ModelInterfaceSchema.BEDROCK_COHERE_EMBED_MULTILINGUAL_V3.getInterface()
        );
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorBEDROCK_COHERE_EMBED_MULTILINGUAL_V3_RAW_MODEL_INTERFACE() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("service_name", "bedrock");
        parameters.put("model", "cohere.embed-multilingual-v3");
        connector = HttpConnector
            .builder()
            .protocol("http")
            .parameters(parameters)
            .actions(List.of(connectorActionWithoutPostProcessFunction))
            .build();
        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertEquals(
            registerModelInputWithStandaloneConnector.getModelInterface(),
            ModelInterfaceSchema.BEDROCK_COHERE_EMBED_MULTILINGUAL_V3_RAW.getInterface()
        );
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorBEDROCK_TITAN_EMBED_TEXT_V1_MODEL_INTERFACE() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("service_name", "bedrock");
        parameters.put("model", "amazon.titan-embed-text-v1");

        connectorActionWithPostProcessFunction = ConnectorAction
            .builder()
            .actionType(PREDICT)
            .method("POST")
            .url("http:///mock")
            .requestBody("{\"input\": \"${parameters.input}\"}")
            .postProcessFunction(MLPostProcessFunction.BEDROCK_EMBEDDING)
            .build();

        connector = HttpConnector
            .builder()
            .protocol("http")
            .parameters(parameters)
            .actions(List.of(connectorActionWithPostProcessFunction))
            .build();

        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertEquals(
            registerModelInputWithStandaloneConnector.getModelInterface(),
            ModelInterfaceSchema.BEDROCK_TITAN_EMBED_TEXT_V1.getInterface()
        );
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorBEDROCK_TITAN_EMBED_TEXT_V1_RAW_MODEL_INTERFACE() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("service_name", "bedrock");
        parameters.put("model", "amazon.titan-embed-text-v1");
        connector = HttpConnector
            .builder()
            .protocol("http")
            .parameters(parameters)
            .actions(List.of(connectorActionWithoutPostProcessFunction))
            .build();
        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertEquals(
            registerModelInputWithStandaloneConnector.getModelInterface(),
            ModelInterfaceSchema.BEDROCK_TITAN_EMBED_TEXT_V1_RAW.getInterface()
        );
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorBEDROCK_TITAN_EMBED_MULTI_MODAL_V1_MODEL_INTERFACE() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("service_name", "bedrock");
        parameters.put("model", "amazon.titan-embed-image-v1");

        connectorActionWithPostProcessFunction = ConnectorAction
            .builder()
            .actionType(PREDICT)
            .method("POST")
            .url("http:///mock")
            .requestBody("{\"input\": \"${parameters.input}\"}")
            .postProcessFunction(MLPostProcessFunction.BEDROCK_EMBEDDING)
            .build();

        connector = HttpConnector
            .builder()
            .protocol("http")
            .parameters(parameters)
            .actions(List.of(connectorActionWithPostProcessFunction))
            .build();

        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertEquals(
            registerModelInputWithStandaloneConnector.getModelInterface(),
            ModelInterfaceSchema.BEDROCK_TITAN_EMBED_MULTI_MODAL_V1.getInterface()
        );
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorBEDROCK_TITAN_EMBED_MULTI_MODAL_V1_RAW_MODEL_INTERFACE() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("service_name", "bedrock");
        parameters.put("model", "amazon.titan-embed-image-v1");
        connector = HttpConnector
            .builder()
            .protocol("http")
            .parameters(parameters)
            .actions(List.of(connectorActionWithoutPostProcessFunction))
            .build();
        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertEquals(
            registerModelInputWithStandaloneConnector.getModelInterface(),
            ModelInterfaceSchema.BEDROCK_TITAN_EMBED_MULTI_MODAL_V1_RAW.getInterface()
        );
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorBEDROCK_ANTHROPIC_CLAUDE_3_7_SONNET_WITH_SYSTEM_PROMPT() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("service_name", "bedrock");
        parameters.put("model", "us.anthropic.claude-3-7-sonnet-20250219-v1:0");
        parameters.put("use_system_prompt", "true");
        connector = HttpConnector
            .builder()
            .protocol("http")
            .parameters(parameters)
            .actions(List.of(connectorActionWithPostProcessFunction))
            .build();

        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertEquals(
            registerModelInputWithStandaloneConnector.getModelInterface(),
            ModelInterfaceSchema.BEDROCK_ANTHROPIC_CLAUDE_USE_SYSTEM_PROMPT.getInterface()
        );
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorBEDROCK_ANTHROPIC_CLAUDE_3_7_SONNET_WITHOUT_SYSTEM_PROMPT() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("service_name", "bedrock");
        parameters.put("model", "us.anthropic.claude-3-7-sonnet-20250219-v1:0");
        parameters.put("use_system_prompt", "false");
        connector = HttpConnector
            .builder()
            .protocol("http")
            .parameters(parameters)
            .actions(List.of(connectorActionWithPostProcessFunction))
            .build();

        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertNull(registerModelInputWithStandaloneConnector.getModelInterface());
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorBEDROCK_ANTHROPIC_CLAUDE_3_7_SONNET_MISSING_SYSTEM_PROMPT() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("service_name", "bedrock");
        parameters.put("model", "us.anthropic.claude-3-7-sonnet-20250219-v1:0");
        // use_system_prompt parameter not set
        connector = HttpConnector
            .builder()
            .protocol("http")
            .parameters(parameters)
            .actions(List.of(connectorActionWithPostProcessFunction))
            .build();

        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertNull(registerModelInputWithStandaloneConnector.getModelInterface());
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorBEDROCK_ANTHROPIC_CLAUDE_SONNET_4_WITH_SYSTEM_PROMPT() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("service_name", "bedrock");
        parameters.put("model", "us.anthropic.claude-sonnet-4-20250514-v1:0");
        parameters.put("use_system_prompt", "true");
        connector = HttpConnector
            .builder()
            .protocol("http")
            .parameters(parameters)
            .actions(List.of(connectorActionWithPostProcessFunction))
            .build();

        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertEquals(
            registerModelInputWithStandaloneConnector.getModelInterface(),
            ModelInterfaceSchema.BEDROCK_ANTHROPIC_CLAUDE_USE_SYSTEM_PROMPT.getInterface()
        );
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorBEDROCK_ANTHROPIC_CLAUDE_SONNET_4_WITHOUT_SYSTEM_PROMPT() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("service_name", "bedrock");
        parameters.put("model", "us.anthropic.claude-sonnet-4-20250514-v1:0");
        parameters.put("use_system_prompt", "false");
        connector = HttpConnector
            .builder()
            .protocol("http")
            .parameters(parameters)
            .actions(List.of(connectorActionWithPostProcessFunction))
            .build();

        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertNull(registerModelInputWithStandaloneConnector.getModelInterface());
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorBEDROCK_ANTHROPIC_CLAUDE_SONNET_4_MISSING_SYSTEM_PROMPT() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("service_name", "bedrock");
        parameters.put("model", "us.anthropic.claude-sonnet-4-20250514-v1:0");
        // use_system_prompt parameter not set
        connector = HttpConnector
            .builder()
            .protocol("http")
            .parameters(parameters)
            .actions(List.of(connectorActionWithPostProcessFunction))
            .build();

        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertNull(registerModelInputWithStandaloneConnector.getModelInterface());
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorOPENAI_GPT_3_5_TURBO() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("model", "gpt-3.5-turbo");

        ConnectorAction openaiAction = ConnectorAction
            .builder()
            .actionType(PREDICT)
            .method("POST")
            .url("https://api.openai.com/v1/chat/completions")
            .requestBody("{\"model\": \"${parameters.model}\", \"messages\": \"${parameters.messages}\"}")
            .build();

        connector = HttpConnector.builder().protocol("http").parameters(parameters).actions(List.of(openaiAction)).build();

        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertEquals(
            registerModelInputWithStandaloneConnector.getModelInterface(),
            ModelInterfaceSchema.OPENAI_CHAT_COMPLETIONS.getInterface()
        );
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorOPENAI_GPT_4O_MINI() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("model", "gpt-4o-mini");

        ConnectorAction openaiAction = ConnectorAction
            .builder()
            .actionType(PREDICT)
            .method("POST")
            .url("https://api.openai.com/v1/chat/completions")
            .requestBody("{\"model\": \"${parameters.model}\", \"messages\": \"${parameters.messages}\"}")
            .build();

        connector = HttpConnector.builder().protocol("http").parameters(parameters).actions(List.of(openaiAction)).build();

        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertEquals(
            registerModelInputWithStandaloneConnector.getModelInterface(),
            ModelInterfaceSchema.OPENAI_CHAT_COMPLETIONS.getInterface()
        );
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorOPENAI_GPT_5() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("model", "gpt-5");

        ConnectorAction openaiAction = ConnectorAction
            .builder()
            .actionType(PREDICT)
            .method("POST")
            .url("https://api.openai.com/v1/chat/completions")
            .requestBody("{\"model\": \"${parameters.model}\", \"messages\": \"${parameters.messages}\"}")
            .build();

        connector = HttpConnector.builder().protocol("http").parameters(parameters).actions(List.of(openaiAction)).build();

        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertEquals(
            registerModelInputWithStandaloneConnector.getModelInterface(),
            ModelInterfaceSchema.OPENAI_CHAT_COMPLETIONS.getInterface()
        );
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorOPENAI_WRONG_ENDPOINT() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("model", "gpt-3.5-turbo");

        ConnectorAction openaiAction = ConnectorAction
            .builder()
            .actionType(PREDICT)
            .method("POST")
            .url("https://api.openai.com/v1/completions")
            .requestBody("{\"model\": \"${parameters.model}\", \"prompt\": \"${parameters.prompt}\"}")
            .build();

        connector = HttpConnector.builder().protocol("http").parameters(parameters).actions(List.of(openaiAction)).build();

        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertNull(registerModelInputWithStandaloneConnector.getModelInterface());
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorOPENAI_WRONG_MODEL() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("model", "gpt-4");

        ConnectorAction openaiAction = ConnectorAction
            .builder()
            .actionType(PREDICT)
            .method("POST")
            .url("https://api.openai.com/v1/chat/completions")
            .requestBody("{\"model\": \"${parameters.model}\", \"messages\": \"${parameters.messages}\"}")
            .build();

        connector = HttpConnector.builder().protocol("http").parameters(parameters).actions(List.of(openaiAction)).build();

        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertNull(registerModelInputWithStandaloneConnector.getModelInterface());
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorAMAZON_COMPREHEND_DETECTDOMAINANTLANGUAGE_API_INTERFACE() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("service_name", "comprehend");
        parameters.put("api_name", "DetectDominantLanguage");
        connector = HttpConnector
            .builder()
            .protocol("http")
            .parameters(parameters)
            .actions(List.of(connectorActionWithPostProcessFunction))
            .build();

        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertEquals(
            registerModelInputWithStandaloneConnector.getModelInterface(),
            ModelInterfaceSchema.AMAZON_COMPREHEND_DETECTDOMAINANTLANGUAGE.getInterface()
        );
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorAMAZON_TEXTRACT_DETECTDOCUMENTTEXT_API_INTERFACE() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("service_name", "textract");
        parameters.put("api_name", "DetectDocumentText");
        connector = HttpConnector
            .builder()
            .protocol("http")
            .parameters(parameters)
            .actions(List.of(connectorActionWithPostProcessFunction))
            .build();

        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertEquals(
            registerModelInputWithStandaloneConnector.getModelInterface(),
            ModelInterfaceSchema.AMAZON_TEXTRACT_DETECTDOCUMENTTEXT.getInterface()
        );
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorServiceNameNotFound() {
        Map<String, String> parameters = new HashMap<>();
        connector = HttpConnector
            .builder()
            .protocol("http")
            .parameters(parameters)
            .actions(List.of(connectorActionWithPostProcessFunction))
            .build();

        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertNull(registerModelInputWithStandaloneConnector.getModelInterface());
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorBedrockModelNameNotFound() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("service_name", "bedrock");
        connector = HttpConnector
            .builder()
            .protocol("http")
            .parameters(parameters)
            .actions(List.of(connectorActionWithPostProcessFunction))
            .build();

        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertNull(registerModelInputWithStandaloneConnector.getModelInterface());
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorAmazonComprehendAPINameNotFound() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("service_name", "comprehend");
        connector = HttpConnector
            .builder()
            .protocol("http")
            .parameters(parameters)
            .actions(List.of(connectorActionWithPostProcessFunction))
            .build();

        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertNull(registerModelInputWithStandaloneConnector.getModelInterface());
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorNullParameters() {
        connector = HttpConnector.builder().protocol("http").build();

        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertNull(registerModelInputWithStandaloneConnector.getModelInterface());
    }

    @Test
    public
        void
        testUpdateRegisterModelInputModelInterfaceFieldsByConnectorInnerConnectorBEDROCK_AI21_LABS_JURASSIC2_MID_V1_MODEL_INTERFACE() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("service_name", "bedrock");
        parameters.put("model", "ai21.j2-mid-v1");
        connector = HttpConnector
            .builder()
            .protocol("http")
            .parameters(parameters)
            .actions(List.of(connectorActionWithPostProcessFunction))
            .build();
        registerModelInputWithInnerConnector.setConnector(connector);
        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithInnerConnector);
        assertEquals(
            registerModelInputWithInnerConnector.getModelInterface(),
            ModelInterfaceSchema.BEDROCK_AI21_LABS_JURASSIC2_MID_V1.getInterface()
        );
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorInnerConnectorNullParameters() {
        connector = HttpConnector.builder().protocol("http").build();
        registerModelInputWithInnerConnector.setConnector(connector);
        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithInnerConnector);
        assertNull(registerModelInputWithInnerConnector.getModelInterface());
    }
}
