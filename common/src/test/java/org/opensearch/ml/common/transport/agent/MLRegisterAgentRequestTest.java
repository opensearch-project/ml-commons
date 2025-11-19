/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.agent;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.contextmanager.ContextManagementTemplate;
import org.opensearch.ml.common.contextmanager.ContextManagerConfig;

public class MLRegisterAgentRequestTest {

    MLAgent mlAgent;
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() {
        mlAgent = MLAgent
            .builder()
            .name("test_agent")
            .appType("test_app")
            .type("flow")
            .tools(Arrays.asList(MLToolSpec.builder().type("ListIndexTool").build()))
            .build();
    }

    @Test
    public void constructor_Agent() {
        MLRegisterAgentRequest registerAgentRequest = new MLRegisterAgentRequest(mlAgent);
        assertEquals(mlAgent, registerAgentRequest.getMlAgent());

        ActionRequestValidationException validationException = registerAgentRequest.validate();
        assertNull(validationException);
    }

    @Test
    public void constructor_NullAgent() {
        MLRegisterAgentRequest registerAgentRequest = new MLRegisterAgentRequest((MLAgent) null);
        assertNull(registerAgentRequest.getMlAgent());

        ActionRequestValidationException validationException = registerAgentRequest.validate();
        assertNotNull(validationException);
        assertTrue(validationException.toString().contains("ML agent can't be null"));
    }

    @Test
    public void writeTo_Success() throws IOException {
        MLRegisterAgentRequest registerAgentRequest = new MLRegisterAgentRequest(mlAgent);
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        registerAgentRequest.writeTo(bytesStreamOutput);
        MLRegisterAgentRequest parsedRequest = new MLRegisterAgentRequest(bytesStreamOutput.bytes().streamInput());
        assertEquals(mlAgent, parsedRequest.getMlAgent());
    }

    @Test
    public void fromActionRequest_Success() {
        MLRegisterAgentRequest registerAgentRequest = new MLRegisterAgentRequest(mlAgent);
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                registerAgentRequest.writeTo(out);
            }
        };
        MLRegisterAgentRequest parsedRequest = MLRegisterAgentRequest.fromActionRequest(actionRequest);
        assertNotSame(registerAgentRequest, parsedRequest);
        assertEquals(registerAgentRequest.getMlAgent(), parsedRequest.getMlAgent());
    }

    @Test
    public void fromActionRequest_Success_MLRegisterAgentRequest() {
        MLRegisterAgentRequest registerAgentRequest = new MLRegisterAgentRequest(mlAgent);
        MLRegisterAgentRequest parsedRequest = MLRegisterAgentRequest.fromActionRequest(registerAgentRequest);
        assertSame(registerAgentRequest, parsedRequest);
    }

    @Test
    public void fromActionRequest_Exception() {
        exceptionRule.expect(UncheckedIOException.class);
        exceptionRule.expectMessage("Failed to parse ActionRequest into MLRegisterAgentRequest");
        MLRegisterAgentRequest registerAgentRequest = new MLRegisterAgentRequest(mlAgent);
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException();
            }
        };
        MLRegisterAgentRequest.fromActionRequest(actionRequest);
    }

    @Test
    public void validate_ContextManagementConflict() {
        // Create agent with both context management name and inline configuration
        ContextManagementTemplate contextManagement = ContextManagementTemplate
            .builder()
            .name("test_template")
            .hooks(createValidHooks())
            .build();

        // This should throw an exception during MLAgent construction
        try {
            MLAgent agentWithConflict = MLAgent
                .builder()
                .name("test_agent")
                .type("flow")
                .contextManagementName("template_name")
                .contextManagement(contextManagement)
                .build();
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Cannot specify both context_management_name and context_management"));
        }
    }

    @Test
    public void validate_ContextManagementTemplateName_Valid() {
        MLAgent agentWithTemplateName = MLAgent
            .builder()
            .name("test_agent")
            .type("flow")
            .contextManagementName("valid_template_name")
            .build();

        MLRegisterAgentRequest request = new MLRegisterAgentRequest(agentWithTemplateName);
        ActionRequestValidationException exception = request.validate();

        assertNull(exception);
    }

    @Test
    public void validate_ContextManagementTemplateName_Empty() {
        // Test empty template name - this should be caught at request validation level
        MLAgent agentWithEmptyName = MLAgent.builder().name("test_agent").type("flow").contextManagementName("").build();

        MLRegisterAgentRequest request = new MLRegisterAgentRequest(agentWithEmptyName);
        ActionRequestValidationException exception = request.validate();

        assertNotNull(exception);
        assertTrue(exception.toString().contains("Context management template name cannot be null or empty"));
    }

    @Test
    public void validate_ContextManagementTemplateName_TooLong() {
        // Test template name that's too long
        String longName = "a".repeat(257);
        MLAgent agentWithLongName = MLAgent.builder().name("test_agent").type("flow").contextManagementName(longName).build();

        MLRegisterAgentRequest request = new MLRegisterAgentRequest(agentWithLongName);
        ActionRequestValidationException exception = request.validate();

        assertNotNull(exception);
        assertTrue(exception.toString().contains("Context management template name cannot exceed 256 characters"));
    }

    @Test
    public void validate_ContextManagementTemplateName_InvalidCharacters() {
        // Test template name with invalid characters
        MLAgent agentWithInvalidName = MLAgent.builder().name("test_agent").type("flow").contextManagementName("invalid@name#").build();

        MLRegisterAgentRequest request = new MLRegisterAgentRequest(agentWithInvalidName);
        ActionRequestValidationException exception = request.validate();

        assertNotNull(exception);
        assertTrue(
            exception
                .toString()
                .contains("Context management template name can only contain letters, numbers, underscores, hyphens, and dots")
        );
    }

    @Test
    public void validate_InlineContextManagement_Valid() {
        ContextManagementTemplate validContextManagement = ContextManagementTemplate
            .builder()
            .name("test_template")
            .hooks(createValidHooks())
            .build();

        MLAgent agentWithInlineConfig = MLAgent.builder().name("test_agent").type("flow").contextManagement(validContextManagement).build();

        MLRegisterAgentRequest request = new MLRegisterAgentRequest(agentWithInlineConfig);
        ActionRequestValidationException exception = request.validate();

        assertNull(exception);
    }

    @Test
    public void validate_InlineContextManagement_InvalidHookName() {
        // Create a context management template with invalid hook name but valid structure
        // This should pass MLAgent validation but fail request validation
        Map<String, List<ContextManagerConfig>> invalidHooks = new HashMap<>();
        invalidHooks.put("INVALID_HOOK", Arrays.asList(new ContextManagerConfig("ToolsOutputTruncateManager", null, null)));

        ContextManagementTemplate invalidContextManagement = ContextManagementTemplate
            .builder()
            .name("test_template")
            .hooks(invalidHooks)
            .build();

        MLAgent agentWithInvalidConfig = MLAgent
            .builder()
            .name("test_agent")
            .type("flow")
            .contextManagement(invalidContextManagement)
            .build();

        MLRegisterAgentRequest request = new MLRegisterAgentRequest(agentWithInvalidConfig);
        ActionRequestValidationException exception = request.validate();

        assertNotNull(exception);
        assertTrue(exception.toString().contains("Invalid hook name: INVALID_HOOK"));
    }

    @Test
    public void validate_InlineContextManagement_EmptyHooks() {
        ContextManagementTemplate emptyHooksTemplate = ContextManagementTemplate
            .builder()
            .name("test_template")
            .hooks(new HashMap<>())
            .build();

        // This should throw an exception during MLAgent construction due to invalid context management
        try {
            MLAgent agentWithEmptyHooks = MLAgent.builder().name("test_agent").type("flow").contextManagement(emptyHooksTemplate).build();
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid context management configuration"));
        }
    }

    @Test
    public void validate_InlineContextManagement_InvalidContextManagerConfig() {
        Map<String, List<ContextManagerConfig>> hooksWithInvalidConfig = new HashMap<>();
        hooksWithInvalidConfig
            .put(
                "POST_TOOL",
                Arrays
                    .asList(
                        new ContextManagerConfig(null, null, null) // Invalid: null type
                    )
            );

        ContextManagementTemplate invalidTemplate = ContextManagementTemplate
            .builder()
            .name("test_template")
            .hooks(hooksWithInvalidConfig)
            .build();

        // This should throw an exception during MLAgent construction due to invalid context management
        try {
            MLAgent agentWithInvalidConfig = MLAgent.builder().name("test_agent").type("flow").contextManagement(invalidTemplate).build();
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid context management configuration"));
        }
    }

    @Test
    public void validate_NoContextManagement_Valid() {
        MLAgent agentWithoutContextManagement = MLAgent.builder().name("test_agent").type("flow").build();

        MLRegisterAgentRequest request = new MLRegisterAgentRequest(agentWithoutContextManagement);
        ActionRequestValidationException exception = request.validate();

        assertNull(exception);
    }

    @Test
    public void validate_ContextManagementTemplateName_NullValue() {
        // Test null template name - this should pass validation since null is acceptable
        MLAgent agentWithNullName = MLAgent.builder().name("test_agent").type("flow").contextManagementName(null).build();

        MLRegisterAgentRequest request = new MLRegisterAgentRequest(agentWithNullName);
        ActionRequestValidationException exception = request.validate();

        assertNull(exception);
    }

    @Test
    public void validate_ContextManagementTemplateName_Null() {
        // Test null template name validation
        MLAgent agentWithNullName = MLAgent.builder().name("test_agent").type("flow").contextManagementName(null).build();

        MLRegisterAgentRequest request = new MLRegisterAgentRequest(agentWithNullName);
        ActionRequestValidationException exception = request.validate();

        // This should pass since null is handled differently than empty
        assertNull(exception);
    }

    @Test
    public void validate_InlineContextManagement_NullHooks() {
        // Test inline context management with null hooks
        ContextManagementTemplate contextManagementWithNullHooks = ContextManagementTemplate
            .builder()
            .name("test_template")
            .hooks(null)
            .build();

        MLAgent agentWithNullHooks = MLAgent
            .builder()
            .name("test_agent")
            .type("flow")
            .contextManagement(contextManagementWithNullHooks)
            .build();

        MLRegisterAgentRequest request = new MLRegisterAgentRequest(agentWithNullHooks);
        ActionRequestValidationException exception = request.validate();

        // Should pass since null hooks are handled gracefully
        assertNull(exception);
    }

    @Test
    public void validate_HookName_AllValidTypes() {
        // Test all valid hook names to improve branch coverage
        Map<String, List<ContextManagerConfig>> allValidHooks = new HashMap<>();
        allValidHooks.put("POST_TOOL", Arrays.asList(new ContextManagerConfig("ToolsOutputTruncateManager", null, null)));
        allValidHooks.put("PRE_LLM", Arrays.asList(new ContextManagerConfig("SummarizationManager", null, null)));
        allValidHooks.put("PRE_TOOL", Arrays.asList(new ContextManagerConfig("MemoryManager", null, null)));
        allValidHooks.put("POST_LLM", Arrays.asList(new ContextManagerConfig("ConversationManager", null, null)));

        ContextManagementTemplate contextManagement = ContextManagementTemplate
            .builder()
            .name("test_template")
            .hooks(allValidHooks)
            .build();

        MLAgent agentWithAllHooks = MLAgent.builder().name("test_agent").type("flow").contextManagement(contextManagement).build();

        MLRegisterAgentRequest request = new MLRegisterAgentRequest(agentWithAllHooks);
        ActionRequestValidationException exception = request.validate();

        assertNull(exception);
    }

    /**
     * Helper method to create valid hooks configuration for testing
     */
    private Map<String, List<ContextManagerConfig>> createValidHooks() {
        Map<String, List<ContextManagerConfig>> hooks = new HashMap<>();
        hooks.put("POST_TOOL", Arrays.asList(new ContextManagerConfig("ToolsOutputTruncateManager", null, null)));
        hooks.put("PRE_LLM", Arrays.asList(new ContextManagerConfig("SummarizationManager", null, null)));
        return hooks;
    }
}
