/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.connector.AwsConnector;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.input.execute.agent.ContentBlock;
import org.opensearch.ml.common.input.execute.agent.ContentType;
import org.opensearch.ml.common.input.execute.agent.ImageContent;
import org.opensearch.ml.common.input.execute.agent.Message;
import org.opensearch.ml.common.input.execute.agent.SourceType;

public class BedrockConverseNovaModelProviderTest {

    private BedrockConverseNovaModelProvider provider;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() {
        provider = new BedrockConverseNovaModelProvider();
    }

    @Test
    public void testGetLLMInterface() {
        assertEquals("bedrock/converse/nova", provider.getLLMInterface());
    }

    @Test
    public void testGetLLMInterface_DifferentFromClaude() {
        BedrockConverseModelProvider claudeProvider = new BedrockConverseModelProvider();
        assertNotEquals(claudeProvider.getLLMInterface(), provider.getLLMInterface());
    }

    @Test
    public void testIsInstanceOfBedrockConverseModelProvider() {
        assertTrue(provider instanceof BedrockConverseModelProvider);
    }

    @Test
    public void testCreateConnector_WithNovaModel() {
        String modelId = "amazon.nova-pro-v1:0";
        Map<String, String> credential = new HashMap<>();
        credential.put("access_key", "test_access_key");
        credential.put("secret_key", "test_secret_key");

        Map<String, String> modelParameters = new HashMap<>();
        modelParameters.put("region", "us-west-2");

        Connector connector = provider.createConnector(modelId, credential, modelParameters);

        assertNotNull(connector);
        assertTrue(connector instanceof AwsConnector);
        AwsConnector awsConnector = (AwsConnector) connector;
        assertEquals("us-west-2", awsConnector.getParameters().get("region"));
        assertEquals("bedrock", awsConnector.getParameters().get("service_name"));
        assertEquals(modelId, awsConnector.getParameters().get("model"));
    }

    @Test
    public void testCreateConnector_WithDefaultRegion() {
        String modelId = "amazon.nova-lite-v1:0";
        Map<String, String> credential = new HashMap<>();
        credential.put("access_key", "test_key");
        credential.put("secret_key", "test_secret");

        Connector connector = provider.createConnector(modelId, credential, null);

        assertNotNull(connector);
        assertTrue(connector instanceof AwsConnector);
        AwsConnector awsConnector = (AwsConnector) connector;
        assertEquals("us-east-1", awsConnector.getParameters().get("region"));
    }

    @Test
    public void testCreateConnector_WithNullCredential() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Missing credential");
        provider.createConnector("amazon.nova-pro-v1:0", null, null);
    }

    @Test
    public void testMapTextInput() {
        String text = "Hello from Nova";
        Map<String, String> result = provider.mapTextInput(text, MLAgentType.CONVERSATIONAL);

        assertNotNull(result);
        assertTrue(result.containsKey("body"));
        String body = result.get("body");
        assertTrue(body.contains("\"role\":\"user\""));
        assertTrue(body.contains("\"text\":\"Hello from Nova\""));
    }

    @Test
    public void testMapContentBlocks_TextAndImage() {
        List<ContentBlock> blocks = new ArrayList<>();

        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Describe this image:");
        blocks.add(textBlock);

        ContentBlock imageBlock = new ContentBlock();
        imageBlock.setType(ContentType.IMAGE);
        ImageContent image = new ImageContent(SourceType.BASE64, "png", "imagedata");
        imageBlock.setImage(image);
        blocks.add(imageBlock);

        Map<String, String> result = provider.mapContentBlocks(blocks, MLAgentType.CONVERSATIONAL);

        assertNotNull(result);
        assertTrue(result.containsKey("body"));
        String body = result.get("body");
        assertTrue(body.contains("\"text\":\"Describe this image:\""));
        assertTrue(body.contains("\"image\""));
    }

    @Test
    public void testMapMessages() {
        List<Message> messages = new ArrayList<>();

        List<ContentBlock> content = new ArrayList<>();
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType(ContentType.TEXT);
        textBlock.setText("Hello");
        content.add(textBlock);

        messages.add(new Message("user", content));

        Map<String, String> result = provider.mapMessages(messages, MLAgentType.CONVERSATIONAL);

        assertNotNull(result);
        assertTrue(result.containsKey("body"));
        String body = result.get("body");
        assertTrue(body.contains("\"role\":\"user\""));
        assertTrue(body.contains("\"text\":\"Hello\""));
    }

    @Test
    public void testParseToUnifiedMessage_TextContent() {
        String json = "{\"role\":\"assistant\",\"content\":[{\"text\":\"Hello from Nova\"}]}";
        Message result = provider.parseToUnifiedMessage(json);

        assertNotNull(result);
        assertEquals("assistant", result.getRole());
        assertNotNull(result.getContent());
        assertEquals(1, result.getContent().size());
        assertEquals(ContentType.TEXT, result.getContent().get(0).getType());
        assertEquals("Hello from Nova", result.getContent().get(0).getText());
    }

    @Test
    public void testParseToUnifiedMessage_ToolUse() {
        String json =
            "{\"role\":\"assistant\",\"content\":[{\"toolUse\":{\"toolUseId\":\"tc_1\",\"name\":\"search\",\"input\":{\"query\":\"test\"}}}]}";
        Message result = provider.parseToUnifiedMessage(json);

        assertNotNull(result);
        assertEquals("assistant", result.getRole());
        assertNotNull(result.getToolCalls());
        assertEquals(1, result.getToolCalls().size());
        assertEquals("tc_1", result.getToolCalls().get(0).getId());
        assertEquals("search", result.getToolCalls().get(0).getFunction().getName());
    }
}
