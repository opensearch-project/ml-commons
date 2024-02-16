/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.PROMPT_PREFIX;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.PROMPT_SUFFIX;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.ACTION;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.ACTION_INPUT;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.CHAT_HISTORY;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.CONTEXT;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.EXAMPLES;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.FINAL_ANSWER;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.OS_INDICES;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.THOUGHT;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.tools.Tool;

public class AgentUtilsTest {

    @Mock
    private Tool tool1, tool2;

    private Map<String, Map<String, String>> llmResponseExpectedParseResults;

    private String responseForAction = "---------------------\n{\n  "
        + "\"thought\": \"Let me search our index to find population projections\", \n  "
        + "\"action\": \"VectorDBTool\",\n  "
        + "\"action_input\": \"Seattle population projection 2023\"\n}";

    private String responseForActionWrongAction = "---------------------\n{\n  "
        + "\"thought\": \"Let me search our index to find population projections\", \n  "
        + "\"action\": \"Let me run VectorDBTool to get more data\",\n  "
        + "\"action_input\": \"Seattle population projection 2023\"\n}";

    private String responseForActionNullAction = "---------------------\n{\n  "
        + "\"thought\": \"Let me search our index to find population projections\" \n  }";

    private String responseNotFollowJsonFormat = "Final answer is I don't know";
    private String responseForActionInvalidJson = "---------------------\n{\n  "
        + "\"thought\": \"Let me search our index to find population projections\", \n  "
        + "\"action\": \"VectorDBTool\",\n  "
        + "\"action_input\": \"Seattle population projection 2023\"";
    private String responseForFinalAnswer = "---------------------```json\n{\n  "
        + "\"thought\": \"Unfortunately the tools did not provide the weather forecast directly. Let me check online sources:\",\n  "
        + "\"final_answer\": \"After checking online weather forecasts, it looks like tomorrow will be sunny with a high of 25 degrees Celsius.\"\n}\n```";
    private String responseForFinalAnswerInvalidJson =
        "\"thought\": \"Unfortunately the tools did not provide the weather forecast directly. Let me check online sources:\",\n  "
            + "\"final_answer\": \"After checking online weather forecasts, it looks like tomorrow will be sunny with a high of 25 degrees Celsius.\"\n}\n```";

    private String wrongResponseForAction = "---------------------```json\n{\n  "
        + "\"thought\": \"Let's try VectorDBTool\",\n  "
        + "\"action\": \"After checking online weather forecasts, it looks like tomorrow will be sunny with a high of 25 degrees Celsius.\"\n}\n```";

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        llmResponseExpectedParseResults = new HashMap<>();
        Map responseForActionExpectedResult = Map
            .of(
                THOUGHT,
                "Let me search our index to find population projections",
                ACTION,
                "VectorDBTool",
                ACTION_INPUT,
                "Seattle population projection 2023"
            );
        llmResponseExpectedParseResults.put(responseForAction, responseForActionExpectedResult);
        llmResponseExpectedParseResults.put(responseForActionWrongAction, responseForActionExpectedResult);
        llmResponseExpectedParseResults.put(responseForActionInvalidJson, responseForActionExpectedResult);
        Map responseForActionNullActionExpectedResult = Map
            .of(
                THOUGHT,
                "Let me search our index to find population projections",
                FINAL_ANSWER,
                "{\n  \"thought\": \"Let me search our index to find population projections\" \n  }"
            );
        llmResponseExpectedParseResults.put(responseForActionNullAction, responseForActionNullActionExpectedResult);

        Map responseNotFollowJsonFormatExpectedResult = Map.of(FINAL_ANSWER, responseNotFollowJsonFormat);
        llmResponseExpectedParseResults.put(responseNotFollowJsonFormat, responseNotFollowJsonFormatExpectedResult);

        Map responseForFinalAnswerExpectedResult = Map
            .of(
                THOUGHT,
                "Unfortunately the tools did not provide the weather forecast directly. Let me check online sources:",
                FINAL_ANSWER,
                "After checking online weather forecasts, it looks like tomorrow will be sunny with a high of 25 degrees Celsius."
            );
        llmResponseExpectedParseResults.put(responseForFinalAnswer, responseForFinalAnswerExpectedResult);
        Map responseForFinalAnswerExpectedResultExpectedResult = Map
            .of(
                THOUGHT,
                "Unfortunately the tools did not provide the weather forecast directly. Let me check online sources:",
                FINAL_ANSWER,
                "After checking online weather forecasts, it looks like tomorrow will be sunny with a high of 25 degrees Celsius.\"\n}\n```"
            );
        llmResponseExpectedParseResults.put(responseForFinalAnswerInvalidJson, responseForFinalAnswerExpectedResultExpectedResult);

    }

    @Test
    public void testAddIndicesToPrompt_WithIndices() {
        String initialPrompt = "initial prompt ${parameters.opensearch_indices}";
        Map<String, String> parameters = new HashMap<>();
        parameters.put(OS_INDICES, "[\"index1\", \"index2\"]");

        String expected =
            "initial prompt You have access to the following OpenSearch Index defined in <opensearch_indexes>: \n<opensearch_indexes>\n"
                + "<index>\nindex1\n</index>\n<index>\nindex2\n</index>\n</opensearch_indexes>\n";

        String result = AgentUtils.addIndicesToPrompt(parameters, initialPrompt);
        assertEquals(expected, result);
    }

    @Test
    public void testAddIndicesToPrompt_WithoutIndices() {
        String prompt = "initial prompt";
        Map<String, String> parameters = new HashMap<>();

        String expected = "initial prompt";

        String result = AgentUtils.addIndicesToPrompt(parameters, prompt);
        assertEquals(expected, result);
    }

    @Test
    public void testAddIndicesToPrompt_WithCustomPrefixSuffix() {
        String initialPrompt = "initial prompt ${parameters.opensearch_indices}";
        Map<String, String> parameters = new HashMap<>();
        parameters.put(OS_INDICES, "[\"index1\", \"index2\"]");
        parameters.put("opensearch_indices.prefix", "Custom Prefix\n");
        parameters.put("opensearch_indices.suffix", "\nCustom Suffix");
        parameters.put("opensearch_indices.index.prefix", "Index: ");
        parameters.put("opensearch_indices.index.suffix", "; ");

        String expected = "initial prompt Custom Prefix\nIndex: index1; Index: index2; \nCustom Suffix";

        String result = AgentUtils.addIndicesToPrompt(parameters, initialPrompt);
        assertEquals(expected, result);
    }

    @Test
    public void testAddExamplesToPrompt_WithExamples() {
        // Setup
        String initialPrompt = "initial prompt ${parameters.examples}";
        Map<String, String> parameters = new HashMap<>();
        parameters.put(EXAMPLES, "[\"Example 1\", \"Example 2\"]");

        // Expected output
        String expectedPrompt = "initial prompt EXAMPLES\n--------\n"
            + "You should follow and learn from examples defined in <examples>: \n"
            + "<examples>\n"
            + "<example>\nExample 1\n</example>\n"
            + "<example>\nExample 2\n</example>\n"
            + "</examples>\n";

        // Call the method under test
        String actualPrompt = AgentUtils.addExamplesToPrompt(parameters, initialPrompt);

        // Assert
        assertEquals(expectedPrompt, actualPrompt);
    }

    @Test
    public void testAddExamplesToPrompt_WithoutExamples() {
        // Setup
        String initialPrompt = "initial prompt ${parameters.examples}";
        Map<String, String> parameters = new HashMap<>();

        // Expected output (should remain unchanged)
        String expectedPrompt = "initial prompt ";

        // Call the method under test
        String actualPrompt = AgentUtils.addExamplesToPrompt(parameters, initialPrompt);

        // Assert
        assertEquals(expectedPrompt, actualPrompt);
    }

    @Test
    public void testAddPrefixSuffixToPrompt_WithPrefixSuffix() {
        // Setup
        String initialPrompt = "initial prompt ${parameters.prompt.prefix} main content ${parameters.prompt.suffix}";
        Map<String, String> parameters = new HashMap<>();
        parameters.put(PROMPT_PREFIX, "Prefix: ");
        parameters.put(PROMPT_SUFFIX, " :Suffix");

        // Expected output
        String expectedPrompt = "initial prompt Prefix:  main content  :Suffix";

        // Call the method under test
        String actualPrompt = AgentUtils.addPrefixSuffixToPrompt(parameters, initialPrompt);

        // Assert
        assertEquals(expectedPrompt, actualPrompt);
    }

    @Test
    public void testAddPrefixSuffixToPrompt_WithoutPrefixSuffix() {
        // Setup
        String initialPrompt = "initial prompt ${parameters.prompt.prefix} main content ${parameters.prompt.suffix}";
        Map<String, String> parameters = new HashMap<>();

        // Expected output (should remain unchanged)
        String expectedPrompt = "initial prompt  main content ";

        // Call the method under test
        String actualPrompt = AgentUtils.addPrefixSuffixToPrompt(parameters, initialPrompt);

        // Assert
        assertEquals(expectedPrompt, actualPrompt);
    }

    @Test
    public void testAddToolsToPrompt_WithDescriptions() {
        // Setup
        Map<String, Tool> tools = new HashMap<>();
        tools.put("Tool1", tool1);
        tools.put("Tool2", tool2);
        when(tool1.getDescription()).thenReturn("Description of Tool1");
        when(tool2.getDescription()).thenReturn("Description of Tool2");

        List<String> inputTools = Arrays.asList("Tool1", "Tool2");
        String initialPrompt = "initial prompt ${parameters.tool_descriptions} and ${parameters.tool_names}";

        // Expected output
        String expectedPrompt = "initial prompt You have access to the following tools defined in <tools>: \n"
            + "<tools>\n<tool>\nTool1: Description of Tool1\n</tool>\n"
            + "<tool>\nTool2: Description of Tool2\n</tool>\n</tools>\n and Tool1, Tool2,";

        // Call the method under test
        String actualPrompt = AgentUtils.addToolsToPrompt(tools, new HashMap<>(), inputTools, initialPrompt);

        // Assert
        assertEquals(expectedPrompt, actualPrompt);
    }

    @Test
    public void testAddToolsToPrompt_ToolNotRegistered() {
        // Setup
        Map<String, Tool> tools = new HashMap<>();
        tools.put("Tool1", tool1);
        List<String> inputTools = Arrays.asList("Tool1", "UnregisteredTool");
        String initialPrompt = "initial prompt ${parameters.tool_descriptions}";

        // Assert
        assertThrows(IllegalArgumentException.class, () -> AgentUtils.addToolsToPrompt(tools, new HashMap<>(), inputTools, initialPrompt));
    }

    @Test
    public void testAddChatHistoryToPrompt_WithChatHistory() {
        // Setup
        Map<String, String> parameters = new HashMap<>();
        parameters.put(CHAT_HISTORY, "Previous chat history here.");
        String initialPrompt = "initial prompt ${parameters.chat_history}";

        // Expected output
        String expectedPrompt = "initial prompt Previous chat history here.";

        // Call the method under test
        String actualPrompt = AgentUtils.addChatHistoryToPrompt(parameters, initialPrompt);

        // Assert
        assertEquals(expectedPrompt, actualPrompt);
    }

    @Test
    public void testAddChatHistoryToPrompt_NoChatHistory() {
        // Setup
        Map<String, String> parameters = new HashMap<>();
        String initialPrompt = "initial prompt ${parameters.chat_history}";

        // Expected output (no change from initial prompt)
        String expectedPrompt = "initial prompt ";

        // Call the method under test
        String actualPrompt = AgentUtils.addChatHistoryToPrompt(parameters, initialPrompt);

        // Assert
        assertEquals(expectedPrompt, actualPrompt);
    }

    @Test
    public void testAddContextToPrompt_WithContext() {
        // Setup
        Map<String, String> parameters = new HashMap<>();
        parameters.put(CONTEXT, "Contextual information here.");
        String initialPrompt = "initial prompt ${parameters.context}";

        // Expected output
        String expectedPrompt = "initial prompt Contextual information here.";

        // Call the method under test
        String actualPrompt = AgentUtils.addContextToPrompt(parameters, initialPrompt);

        // Assert
        assertEquals(expectedPrompt, actualPrompt);
    }

    @Test
    public void testAddContextToPrompt_NoContext() {
        // Setup
        Map<String, String> parameters = new HashMap<>();
        String initialPrompt = "initial prompt ${parameters.context}";

        // Expected output (no change from initial prompt)
        String expectedPrompt = "initial prompt ";

        // Call the method under test
        String actualPrompt = AgentUtils.addContextToPrompt(parameters, initialPrompt);

        // Assert
        assertEquals(expectedPrompt, actualPrompt);
    }

    @Test
    public void testExtractModelResponseJsonWithInvalidModelOutput() {
        String text = "invalid output";
        assertThrows(IllegalArgumentException.class, () -> AgentUtils.extractModelResponseJson(text));
    }

    @Test
    public void testExtractModelResponseJsonWithValidModelOutput() {
        String text =
            "This is the model response\n```json\n{\"thought\":\"use CatIndexTool to get index first\",\"action\":\"CatIndexTool\"} \n``` other content";
        String responseJson = AgentUtils.extractModelResponseJson(text);
        assertEquals("{\"thought\":\"use CatIndexTool to get index first\",\"action\":\"CatIndexTool\"}", responseJson);
    }

    @Test
    public void testExtractModelResponseJson_ThoughtFinalAnswer() {
        String text =
            "---------------------\n{\n  \"thought\": \"Unfortunately the tools did not provide the weather forecast directly. Let me check online sources:\",\n  \"final_answer\": \"After checking online weather forecasts, it looks like tomorrow will be sunny with a high of 25 degrees Celsius.\"\n}";
        String result = AgentUtils.extractModelResponseJson(text);
        String expectedResult = "{\n"
            + "  \"thought\": \"Unfortunately the tools did not provide the weather forecast directly. Let me check online sources:\",\n"
            + "  \"final_answer\": \"After checking online weather forecasts, it looks like tomorrow will be sunny with a high of 25 degrees Celsius.\"\n"
            + "}";
        Assert.assertEquals(expectedResult, result);
    }

    @Test
    public void testExtractModelResponseJson_ThoughtFinalAnswerJsonBlock() {
        String text = responseForFinalAnswer;
        String result = AgentUtils.extractModelResponseJson(text);
        String expectedResult = "{\n"
            + "  \"thought\": \"Unfortunately the tools did not provide the weather forecast directly. Let me check online sources:\",\n"
            + "  \"final_answer\": \"After checking online weather forecasts, it looks like tomorrow will be sunny with a high of 25 degrees Celsius.\"\n"
            + "}";
        Assert.assertEquals(expectedResult, result);
    }

    @Test
    public void testExtractModelResponseJson_ThoughtActionInput() {
        String text = responseForAction;
        String result = AgentUtils.extractModelResponseJson(text);
        String expectedResult = "{\n"
            + "  \"thought\": \"Let me search our index to find population projections\", \n"
            + "  \"action\": \"VectorDBTool\",\n"
            + "  \"action_input\": \"Seattle population projection 2023\"\n"
            + "}";
        Assert.assertEquals(expectedResult, result);
    }

    @Test
    public void testExtractMethods() {
        List<String> textList = List.of(responseForAction, responseForActionInvalidJson);
        for (String text : textList) {
            String thought = AgentUtils.extractThought(text);
            String action = AgentUtils.extractAction(text);
            String actionInput = AgentUtils.extractActionInput(text);
            String finalAnswer = AgentUtils.extractFinalAnswer(text);
            Assert.assertEquals("Let me search our index to find population projections", thought);
            Assert.assertEquals("VectorDBTool\",\n  ", action);
            Assert.assertEquals("Seattle population projection 2023", actionInput);
            Assert.assertNull(finalAnswer);
        }
    }

    @Test
    public void testExtractMethods_FinalAnswer() {
        List<String> textList = List.of(responseForFinalAnswer, responseForFinalAnswerInvalidJson);
        for (String text : textList) {
            String thought = AgentUtils.extractThought(text);
            String action = AgentUtils.extractAction(text);
            String actionInput = AgentUtils.extractActionInput(text);
            String finalAnswer = AgentUtils.extractFinalAnswer(text);
            Assert
                .assertEquals(
                    "Unfortunately the tools did not provide the weather forecast directly. Let me check online sources:",
                    thought
                );
            Assert.assertNull(action);
            Assert.assertNull(actionInput);
            Assert
                .assertEquals(
                    "After checking online weather forecasts, it looks like tomorrow will be sunny with a high of 25 degrees Celsius.\"\n}\n```",
                    finalAnswer
                );
        }
    }

    @Test
    public void testParseLLMOutput() {
        Set<String> tools = Set.of("VectorDBTool", "CatIndexTool");
        for (Map.Entry<String, Map<String, String>> entry : llmResponseExpectedParseResults.entrySet()) {
            ModelTensorOutput modelTensoOutput = ModelTensorOutput
                .builder()
                .mlModelOutputs(
                    List
                        .of(
                            ModelTensors
                                .builder()
                                .mlModelTensors(
                                    List.of(ModelTensor.builder().name("response").dataAsMap(Map.of("response", entry.getKey())).build())
                                )
                                .build()
                        )
                )
                .build();
            Map<String, String> output = AgentUtils.parseLLMOutput(modelTensoOutput, null, tools);
            for (String key : entry.getValue().keySet()) {
                Assert.assertEquals(entry.getValue().get(key), output.get(key));
            }
        }
    }

    @Test
    public void testExtractThought_InvalidResult() {
        String text = responseForActionInvalidJson;
        String result = AgentUtils.extractThought(text);
        Assert.assertEquals("Let me search our index to find population projections", result);
    }

}
