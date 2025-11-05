/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agent;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.action.contextmanagement.ContextManagementTemplateService;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.contextmanager.ContextManagementTemplate;
import org.opensearch.ml.common.contextmanager.ContextManagerConfig;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;

import junit.framework.TestCase;

public class MLAgentRegistrationValidatorTests extends TestCase {

    private ContextManagementTemplateService mockTemplateService;
    private MLAgentRegistrationValidator validator;

    @Before
    public void setUp() {
        mockTemplateService = mock(ContextManagementTemplateService.class);
        validator = new MLAgentRegistrationValidator(mockTemplateService);
    }

    @Test
    public void testValidateAgentForRegistration_NoContextManagement() throws InterruptedException {
        MLAgent agent = MLAgent.builder().name("test_agent").type("flow").build();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Boolean> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        validator.validateAgentForRegistration(agent, new ActionListener<Boolean>() {
            @Override
            public void onResponse(Boolean response) {
                result.set(response);
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                error.set(e);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(result.get());
        assertNull(error.get());

        // Verify template service was not called since no template reference
        verify(mockTemplateService, never()).getTemplate(any(), any());
    }

    @Test
    public void testValidateAgentForRegistration_TemplateExists() throws InterruptedException {
        MLAgent agent = MLAgent.builder().name("test_agent").type("flow").contextManagementName("existing_template").build();

        // Mock template service to return a template (exists)
        doAnswer(invocation -> {
            ActionListener<ContextManagementTemplate> listener = invocation.getArgument(1);
            ContextManagementTemplate template = ContextManagementTemplate.builder().name("existing_template").build();
            listener.onResponse(template);
            return null;
        }).when(mockTemplateService).getTemplate(eq("existing_template"), any());

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Boolean> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        validator.validateAgentForRegistration(agent, new ActionListener<Boolean>() {
            @Override
            public void onResponse(Boolean response) {
                result.set(response);
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                error.set(e);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(result.get());
        assertNull(error.get());

        verify(mockTemplateService).getTemplate(eq("existing_template"), any());
    }

    @Test
    public void testValidateAgentForRegistration_TemplateNotFound() throws InterruptedException {
        MLAgent agent = MLAgent.builder().name("test_agent").type("flow").contextManagementName("nonexistent_template").build();

        // Mock template service to return template not found
        doAnswer(invocation -> {
            ActionListener<ContextManagementTemplate> listener = invocation.getArgument(1);
            listener.onFailure(new MLResourceNotFoundException("Context management template not found: nonexistent_template"));
            return null;
        }).when(mockTemplateService).getTemplate(eq("nonexistent_template"), any());

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Boolean> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        validator.validateAgentForRegistration(agent, new ActionListener<Boolean>() {
            @Override
            public void onResponse(Boolean response) {
                result.set(response);
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                error.set(e);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(result.get());
        assertNotNull(error.get());
        assertTrue(error.get() instanceof IllegalArgumentException);
        assertTrue(error.get().getMessage().contains("Context management template not found: nonexistent_template"));

        verify(mockTemplateService).getTemplate(eq("nonexistent_template"), any());
    }

    @Test
    public void testValidateAgentForRegistration_TemplateServiceError() throws InterruptedException {
        MLAgent agent = MLAgent.builder().name("test_agent").type("flow").contextManagementName("error_template").build();

        // Mock template service to return an error
        doAnswer(invocation -> {
            ActionListener<ContextManagementTemplate> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Service error"));
            return null;
        }).when(mockTemplateService).getTemplate(eq("error_template"), any());

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Boolean> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        validator.validateAgentForRegistration(agent, new ActionListener<Boolean>() {
            @Override
            public void onResponse(Boolean response) {
                result.set(response);
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                error.set(e);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(result.get());
        assertNotNull(error.get());
        assertTrue(error.get() instanceof IllegalArgumentException);
        assertTrue(error.get().getMessage().contains("Failed to validate context management template"));

        verify(mockTemplateService).getTemplate(eq("error_template"), any());
    }

    @Test
    public void testValidateAgentForRegistration_InlineContextManagement() throws InterruptedException {
        Map<String, List<ContextManagerConfig>> hooks = new HashMap<>();
        hooks.put("POST_TOOL", List.of(new ContextManagerConfig("ToolsOutputTruncateManager", null, null)));

        ContextManagementTemplate contextManagement = ContextManagementTemplate.builder().name("inline_template").hooks(hooks).build();

        MLAgent agent = MLAgent.builder().name("test_agent").type("flow").contextManagement(contextManagement).build();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Boolean> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        validator.validateAgentForRegistration(agent, new ActionListener<Boolean>() {
            @Override
            public void onResponse(Boolean response) {
                result.set(response);
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                error.set(e);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(result.get());
        assertNull(error.get());

        // Verify template service was not called since using inline configuration
        verify(mockTemplateService, never()).getTemplate(any(), any());
    }

    @Test
    public void testValidateAgentForRegistration_InvalidTemplateName() throws InterruptedException {
        MLAgent agent = MLAgent.builder().name("test_agent").type("flow").contextManagementName("invalid@name").build();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Boolean> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        validator.validateAgentForRegistration(agent, new ActionListener<Boolean>() {
            @Override
            public void onResponse(Boolean response) {
                result.set(response);
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                error.set(e);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(result.get());
        assertNotNull(error.get());
        assertTrue(error.get() instanceof IllegalArgumentException);
        assertTrue(
            error
                .get()
                .getMessage()
                .contains("Context management template name can only contain letters, numbers, underscores, hyphens, and dots")
        );

        // Verify template service was not called due to early validation failure
        verify(mockTemplateService, never()).getTemplate(any(), any());
    }

    @Test
    public void testValidateAgentForRegistration_InvalidInlineConfiguration() throws InterruptedException {
        Map<String, List<ContextManagerConfig>> invalidHooks = new HashMap<>();
        invalidHooks.put("INVALID_HOOK", List.of(new ContextManagerConfig("ToolsOutputTruncateManager", null, null)));

        ContextManagementTemplate contextManagement = ContextManagementTemplate.builder().name("test_template").hooks(invalidHooks).build();

        MLAgent agent = MLAgent.builder().name("test_agent").type("flow").contextManagement(contextManagement).build();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Boolean> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();

        validator.validateAgentForRegistration(agent, new ActionListener<Boolean>() {
            @Override
            public void onResponse(Boolean response) {
                result.set(response);
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                error.set(e);
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertNull(result.get());
        assertNotNull(error.get());
        assertTrue(error.get() instanceof IllegalArgumentException);
        assertTrue(error.get().getMessage().contains("Invalid hook name: INVALID_HOOK"));

        // Verify template service was not called due to early validation failure
        verify(mockTemplateService, never()).getTemplate(any(), any());
    }

    @Test
    public void testValidateContextManagementConfiguration_ValidTemplateName() {
        MLAgent agent = MLAgent.builder().name("test_agent").type("flow").contextManagementName("valid_template_name").build();

        String result = validator.validateContextManagementConfiguration(agent);
        assertNull(result);
    }

    @Test
    public void testValidateContextManagementConfiguration_ValidInlineConfig() {
        Map<String, List<ContextManagerConfig>> hooks = new HashMap<>();
        hooks.put("POST_TOOL", List.of(new ContextManagerConfig("ToolsOutputTruncateManager", null, null)));

        ContextManagementTemplate contextManagement = ContextManagementTemplate.builder().name("inline_template").hooks(hooks).build();

        MLAgent agent = MLAgent.builder().name("test_agent").type("flow").contextManagement(contextManagement).build();

        String result = validator.validateContextManagementConfiguration(agent);
        assertNull(result);
    }

    @Test
    public void testValidateContextManagementConfiguration_EmptyTemplateName() {
        MLAgent agent = MLAgent.builder().name("test_agent").type("flow").contextManagementName("").build();

        String result = validator.validateContextManagementConfiguration(agent);
        assertNotNull(result);
        assertTrue(result.contains("Context management template name cannot be null or empty"));
    }

    @Test
    public void testValidateContextManagementConfiguration_TooLongTemplateName() {
        String longName = "a".repeat(257);
        MLAgent agent = MLAgent.builder().name("test_agent").type("flow").contextManagementName(longName).build();

        String result = validator.validateContextManagementConfiguration(agent);
        assertNotNull(result);
        assertTrue(result.contains("Context management template name cannot exceed 256 characters"));
    }

    @Test
    public void testValidateContextManagementConfiguration_InvalidTemplateNameCharacters() {
        MLAgent agent = MLAgent.builder().name("test_agent").type("flow").contextManagementName("invalid@name#").build();

        String result = validator.validateContextManagementConfiguration(agent);
        assertNotNull(result);
        assertTrue(result.contains("Context management template name can only contain letters, numbers, underscores, hyphens, and dots"));
    }

    @Test
    public void testValidateContextManagementConfiguration_InvalidHookName() {
        Map<String, List<ContextManagerConfig>> invalidHooks = new HashMap<>();
        invalidHooks.put("INVALID_HOOK", List.of(new ContextManagerConfig("ToolsOutputTruncateManager", null, null)));

        ContextManagementTemplate contextManagement = ContextManagementTemplate.builder().name("test_template").hooks(invalidHooks).build();

        MLAgent agent = MLAgent.builder().name("test_agent").type("flow").contextManagement(contextManagement).build();

        String result = validator.validateContextManagementConfiguration(agent);
        assertNotNull(result);
        assertTrue(result.contains("Invalid hook name: INVALID_HOOK"));
    }

    @Test
    public void testValidateContextManagementConfiguration_EmptyHookConfigs() {
        Map<String, List<ContextManagerConfig>> emptyHooks = new HashMap<>();
        emptyHooks.put("POST_TOOL", List.of());

        ContextManagementTemplate contextManagement = ContextManagementTemplate.builder().name("test_template").hooks(emptyHooks).build();

        MLAgent agent = MLAgent.builder().name("test_agent").type("flow").contextManagement(contextManagement).build();

        String result = validator.validateContextManagementConfiguration(agent);
        assertNotNull(result);
        assertTrue(result.contains("Hook POST_TOOL must have at least one context manager configuration"));
    }

    @Test
    public void testValidateContextManagementConfiguration_Conflict() {
        // This test should verify that the MLAgent constructor throws an exception
        // when both context management name and inline config are provided
        Map<String, List<ContextManagerConfig>> hooks = new HashMap<>();
        hooks.put("POST_TOOL", List.of(new ContextManagerConfig("ToolsOutputTruncateManager", null, null)));

        ContextManagementTemplate contextManagement = ContextManagementTemplate.builder().name("inline_template").hooks(hooks).build();

        try {
            MLAgent agent = MLAgent
                .builder()
                .name("test_agent")
                .type("flow")
                .contextManagementName("template_name")
                .contextManagement(contextManagement)
                .build();
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertEquals("Cannot specify both context_management_name and context_management", e.getMessage());
        }
    }

    @Test
    public void testValidateContextManagementConfiguration_InvalidInlineConfig() {
        // This test should verify that the MLAgent constructor throws an exception
        // when invalid context management configuration is provided
        ContextManagementTemplate invalidContextManagement = ContextManagementTemplate
            .builder()
            .name("invalid_template")
            .hooks(new HashMap<>())
            .build();

        try {
            MLAgent agent = MLAgent.builder().name("test_agent").type("flow").contextManagement(invalidContextManagement).build();
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertEquals("Invalid context management configuration", e.getMessage());
        }
    }
}
