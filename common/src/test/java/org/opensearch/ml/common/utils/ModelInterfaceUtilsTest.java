/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Spy;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.opensearch.ml.common.utils.ModelInterfaceUtils.AMAZON_COMPREHEND_DETECTDOMAINANTLANGUAGE_API_INTERFACE;
import static org.opensearch.ml.common.utils.ModelInterfaceUtils.AMAZON_TEXTRACT_DETECTDOCUMENTTEXT_API_INTERFACE;
import static org.opensearch.ml.common.utils.ModelInterfaceUtils.BEDROCK_AI21_LABS_JURASSIC2_MID_V1_MODEL_INTERFACE;
import static org.opensearch.ml.common.utils.ModelInterfaceUtils.BEDROCK_ANTHROPIC_CLAUDE_V2_MODEL_INTERFACE;
import static org.opensearch.ml.common.utils.ModelInterfaceUtils.BEDROCK_ANTHROPIC_CLAUDE_V3_SONNET_MODEL_INTERFACE;
import static org.opensearch.ml.common.utils.ModelInterfaceUtils.BEDROCK_COHERE_EMBED_ENGLISH_V3_MODEL_INTERFACE;
import static org.opensearch.ml.common.utils.ModelInterfaceUtils.BEDROCK_COHERE_EMBED_MULTILINGUAL_V3_MODEL_INTERFACE;
import static org.opensearch.ml.common.utils.ModelInterfaceUtils.BEDROCK_TITAN_EMBED_MULTI_MODAL_V1_MODEL_INTERFACE;
import static org.opensearch.ml.common.utils.ModelInterfaceUtils.BEDROCK_TITAN_EMBED_TEXT_V1_MODEL_INTERFACE;
import static org.opensearch.ml.common.utils.ModelInterfaceUtils.updateRegisterModelInputModelInterfaceFieldsByConnector;

public class ModelInterfaceUtilsTest {
    @Spy
    MLRegisterModelInput registerModelInputWithInnerConnector;

    @Spy
    MLRegisterModelInput registerModelInputWithStandaloneConnector;

    @Spy
    public HttpConnector connector;

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
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorBEDROCK_AI21_LABS_JURASSIC2_MID_V1_MODEL_INTERFACE() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("service_name", "bedrock");
        parameters.put("model", "ai21.j2-mid-v1");
        connector = HttpConnector.builder().protocol("http").parameters(parameters).build();

        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertEquals(registerModelInputWithStandaloneConnector.getModelInterface(), BEDROCK_AI21_LABS_JURASSIC2_MID_V1_MODEL_INTERFACE);
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorBEDROCK_ANTHROPIC_CLAUDE_V3_SONNET_MODEL_INTERFACE() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("service_name", "bedrock");
        parameters.put("model", "anthropic.claude-3-sonnet-20240229-v1:0");
        connector = HttpConnector.builder().protocol("http").parameters(parameters).build();

        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertEquals(registerModelInputWithStandaloneConnector.getModelInterface(), BEDROCK_ANTHROPIC_CLAUDE_V3_SONNET_MODEL_INTERFACE);
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorBEDROCK_ANTHROPIC_CLAUDE_V2_MODEL_INTERFACE() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("service_name", "bedrock");
        parameters.put("model", "anthropic.claude-v2");
        connector = HttpConnector.builder().protocol("http").parameters(parameters).build();

        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertEquals(registerModelInputWithStandaloneConnector.getModelInterface(), BEDROCK_ANTHROPIC_CLAUDE_V2_MODEL_INTERFACE);
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorBEDROCK_COHERE_EMBED_ENGLISH_V3_MODEL_INTERFACE() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("service_name", "bedrock");
        parameters.put("model", "cohere.embed.english-v3");
        connector = HttpConnector.builder().protocol("http").parameters(parameters).build();

        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertEquals(registerModelInputWithStandaloneConnector.getModelInterface(), BEDROCK_COHERE_EMBED_ENGLISH_V3_MODEL_INTERFACE);
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorBEDROCK_COHERE_EMBED_MULTILINGUAL_V3_MODEL_INTERFACE() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("service_name", "bedrock");
        parameters.put("model", "cohere.embed.multilingual-v3");
        connector = HttpConnector.builder().protocol("http").parameters(parameters).build();

        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertEquals(registerModelInputWithStandaloneConnector.getModelInterface(), BEDROCK_COHERE_EMBED_MULTILINGUAL_V3_MODEL_INTERFACE);
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorBEDROCK_TITAN_EMBED_TEXT_V1_MODEL_INTERFACE() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("service_name", "bedrock");
        parameters.put("model", "amazon.titan-embed-text-v1");
        connector = HttpConnector.builder().protocol("http").parameters(parameters).build();

        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertEquals(registerModelInputWithStandaloneConnector.getModelInterface(), BEDROCK_TITAN_EMBED_TEXT_V1_MODEL_INTERFACE);
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorBEDROCK_TITAN_EMBED_MULTI_MODAL_V1_MODEL_INTERFACE() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("service_name", "bedrock");
        parameters.put("model", "amazon.titan-embed-image-v1");
        connector = HttpConnector.builder().protocol("http").parameters(parameters).build();

        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertEquals(registerModelInputWithStandaloneConnector.getModelInterface(), BEDROCK_TITAN_EMBED_MULTI_MODAL_V1_MODEL_INTERFACE);
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorAMAZON_COMPREHEND_DETECTDOMAINANTLANGUAGE_API_INTERFACE() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("service_name", "comprehend");
        parameters.put("api_name", "DetectDominantLanguage");
        connector = HttpConnector.builder().protocol("http").parameters(parameters).build();

        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertEquals(registerModelInputWithStandaloneConnector.getModelInterface(), AMAZON_COMPREHEND_DETECTDOMAINANTLANGUAGE_API_INTERFACE);
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorAMAZON_TEXTRACT_DETECTDOCUMENTTEXT_API_INTERFACE() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("service_name", "textract");
        parameters.put("api_name", "DetectDocumentText");
        connector = HttpConnector.builder().protocol("http").parameters(parameters).build();

        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertEquals(registerModelInputWithStandaloneConnector.getModelInterface(), AMAZON_TEXTRACT_DETECTDOCUMENTTEXT_API_INTERFACE);
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorServiceNameNotFound() {
        Map<String, String> parameters = new HashMap<>();
        connector = HttpConnector.builder().protocol("http").parameters(parameters).build();

        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertNull(registerModelInputWithStandaloneConnector.getModelInterface());
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorBedrockModelNameNotFound() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("service_name", "bedrock");
        connector = HttpConnector.builder().protocol("http").parameters(parameters).build();

        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithStandaloneConnector, connector);
        assertNull(registerModelInputWithStandaloneConnector.getModelInterface());
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorAmazonComprehendAPINameNotFound() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("service_name", "comprehend");
        connector = HttpConnector.builder().protocol("http").parameters(parameters).build();

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
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorInnerConnectorBEDROCK_AI21_LABS_JURASSIC2_MID_V1_MODEL_INTERFACE() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("service_name", "bedrock");
        parameters.put("model", "ai21.j2-mid-v1");
        connector = HttpConnector.builder().protocol("http").parameters(parameters).build();
        registerModelInputWithInnerConnector.setConnector(connector);
        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithInnerConnector);
        assertEquals(registerModelInputWithInnerConnector.getModelInterface(), BEDROCK_AI21_LABS_JURASSIC2_MID_V1_MODEL_INTERFACE);
    }

    @Test
    public void testUpdateRegisterModelInputModelInterfaceFieldsByConnectorInnerConnectorNullParameters() {
        connector = HttpConnector.builder().protocol("http").build();
        registerModelInputWithInnerConnector.setConnector(connector);
        updateRegisterModelInputModelInterfaceFieldsByConnector(registerModelInputWithInnerConnector);
        assertNull(registerModelInputWithInnerConnector.getModelInterface());
    }
}
