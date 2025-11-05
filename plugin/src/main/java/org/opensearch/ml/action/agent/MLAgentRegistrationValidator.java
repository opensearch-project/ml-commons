/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agent;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.action.contextmanagement.ContextManagementTemplateService;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;

import lombok.extern.log4j.Log4j2;

/**
 * Validator for ML Agent registration that performs advanced validation
 * requiring service dependencies.
 * This validator handles validation that cannot be performed in the request
 * object itself,
 * such as template existence checking.
 */
@Log4j2
public class MLAgentRegistrationValidator {

    private final ContextManagementTemplateService contextManagementTemplateService;

    public MLAgentRegistrationValidator(ContextManagementTemplateService contextManagementTemplateService) {
        this.contextManagementTemplateService = contextManagementTemplateService;
    }

    /**
     * Validates an ML agent for registration, performing all necessary validation checks.
     * This is the main validation entry point that orchestrates all validation steps.
     * 
     * @param agent the ML agent to validate
     * @param listener callback for validation result - onResponse(true) if valid, onFailure with exception if not
     */
    public void validateAgentForRegistration(MLAgent agent, ActionListener<Boolean> listener) {
        try {
            log.debug("Starting agent registration validation for agent: {}", agent.getName());

            // First, perform basic context management configuration validation
            String configError = validateContextManagementConfiguration(agent);
            if (configError != null) {
                log.error("Agent registration validation failed - configuration error: {}", configError);
                listener.onFailure(new IllegalArgumentException(configError));
                return;
            }

            // If agent has a context management template reference, validate template access
            if (agent.getContextManagementName() != null) {
                validateContextManagementTemplateAccess(agent.getContextManagementName(), ActionListener.wrap(templateAccessValid -> {
                    log.debug("Agent registration validation completed successfully for agent: {}", agent.getName());
                    listener.onResponse(true);
                }, templateAccessError -> {
                    log.error("Agent registration validation failed - template access error: {}", templateAccessError.getMessage());
                    listener.onFailure(templateAccessError);
                }));
            } else {
                // No template reference, validation is complete
                log.debug("Agent registration validation completed successfully for agent: {}", agent.getName());
                listener.onResponse(true);
            }
        } catch (Exception e) {
            log.error("Unexpected error during agent registration validation", e);
            listener.onFailure(new IllegalArgumentException("Agent validation failed: " + e.getMessage()));
        }
    }

    /**
     * Validates context management template access (following connector access validation pattern).
     * This method checks if the template exists and if the user has access to it.
     * 
     * @param templateName the context management template name to validate
     * @param listener callback for validation result - onResponse(true) if accessible, onFailure with exception if not
     */
    public void validateContextManagementTemplateAccess(String templateName, ActionListener<Boolean> listener) {
        try {
            log.debug("Validating context management template access: {}", templateName);

            contextManagementTemplateService.getTemplate(templateName, ActionListener.wrap(template -> {
                log.debug("Context management template access validation passed: {}", templateName);
                listener.onResponse(true);
            }, exception -> {
                log.error("Context management template access validation failed: {}", templateName, exception);
                if (exception instanceof MLResourceNotFoundException) {
                    listener.onFailure(new IllegalArgumentException("Context management template not found: " + templateName));
                } else {
                    listener
                        .onFailure(
                            new IllegalArgumentException("Failed to validate context management template: " + exception.getMessage())
                        );
                }
            }));
        } catch (Exception e) {
            log.error("Unexpected error during context management template access validation", e);
            listener.onFailure(new IllegalArgumentException("Context management template validation failed: " + e.getMessage()));
        }
    }

    /**
     * Validates context management configuration structure and requirements.
     * This method performs comprehensive validation of context management settings.
     * 
     * @param agent the ML agent to validate
     * @return validation error message if invalid, null if valid
     */
    public String validateContextManagementConfiguration(MLAgent agent) {
        // Check for conflicting configuration (both name and inline config specified)
        if (agent.getContextManagementName() != null && agent.getContextManagement() != null) {
            return "Cannot specify both context_management_name and context_management";
        }

        // Validate context management template name if specified
        if (agent.getContextManagementName() != null) {
            String templateNameError = validateContextManagementTemplateName(agent.getContextManagementName());
            if (templateNameError != null) {
                return templateNameError;
            }
        }

        // Validate inline context management configuration if specified
        if (agent.getContextManagement() != null) {
            String inlineConfigError = validateInlineContextManagementConfiguration(agent.getContextManagement());
            if (inlineConfigError != null) {
                return inlineConfigError;
            }
        }

        return null; // Valid
    }

    /**
     * Validates the context management template name format and basic requirements.
     * 
     * @param templateName the template name to validate
     * @return validation error message if invalid, null if valid
     */
    private String validateContextManagementTemplateName(String templateName) {
        if (templateName == null || templateName.trim().isEmpty()) {
            return "Context management template name cannot be null or empty";
        }

        if (templateName.length() > 256) {
            return "Context management template name cannot exceed 256 characters";
        }

        if (!templateName.matches("^[a-zA-Z0-9_\\-\\.]+$")) {
            return "Context management template name can only contain letters, numbers, underscores, hyphens, and dots";
        }

        return null; // Valid
    }

    /**
     * Validates the inline context management configuration structure and content.
     * 
     * @param contextManagement the context management configuration to validate
     * @return validation error message if invalid, null if valid
     */
    private String validateInlineContextManagementConfiguration(
        org.opensearch.ml.common.contextmanager.ContextManagementTemplate contextManagement
    ) {
        // Use the built-in validation from ContextManagementTemplate
        if (!contextManagement.isValid()) {
            return "Invalid context management configuration: configuration must have a name and at least one hook with valid context manager configurations";
        }

        // Additional validation for specific requirements
        if (contextManagement.getName() == null || contextManagement.getName().trim().isEmpty()) {
            return "Context management configuration name cannot be null or empty";
        }

        if (contextManagement.getHooks() == null || contextManagement.getHooks().isEmpty()) {
            return "Context management configuration must define at least one hook";
        }

        // Validate hook names and configurations
        return validateContextManagementHooks(contextManagement.getHooks());
    }

    /**
     * Validates context management hooks configuration.
     * 
     * @param hooks the hooks configuration to validate
     * @return validation error message if invalid, null if valid
     */
    private String validateContextManagementHooks(
        java.util.Map<String, java.util.List<org.opensearch.ml.common.contextmanager.ContextManagerConfig>> hooks
    ) {
        // Define valid hook names
        java.util.Set<String> validHookNames = java.util.Set
            .of("PRE_TOOL", "POST_TOOL", "PRE_LLM", "POST_LLM", "PRE_EXECUTION", "POST_EXECUTION");

        for (java.util.Map.Entry<String, java.util.List<org.opensearch.ml.common.contextmanager.ContextManagerConfig>> entry : hooks
            .entrySet()) {
            String hookName = entry.getKey();
            java.util.List<org.opensearch.ml.common.contextmanager.ContextManagerConfig> configs = entry.getValue();

            // Validate hook name
            if (!validHookNames.contains(hookName)) {
                return "Invalid hook name: " + hookName + ". Valid hook names are: " + validHookNames;
            }

            // Validate hook configurations
            if (configs == null || configs.isEmpty()) {
                return "Hook " + hookName + " must have at least one context manager configuration";
            }

            for (int i = 0; i < configs.size(); i++) {
                org.opensearch.ml.common.contextmanager.ContextManagerConfig config = configs.get(i);
                if (!config.isValid()) {
                    return "Invalid context manager configuration at index "
                        + i
                        + " in hook "
                        + hookName
                        + ": type cannot be null or empty";
                }

                // Validate context manager type
                if (config.getType() != null) {
                    String typeError = validateContextManagerType(config.getType(), hookName, i);
                    if (typeError != null) {
                        return typeError;
                    }
                }
            }
        }

        return null; // Valid
    }

    /**
     * Validates context manager type for known types.
     * 
     * @param type     the context manager type to validate
     * @param hookName the hook name for error reporting
     * @param index    the configuration index for error reporting
     * @return validation error message if invalid, null if valid
     */
    private String validateContextManagerType(String type, String hookName, int index) {
        // Define known context manager types
        java.util.Set<String> knownTypes = java.util.Set
            .of("ToolsOutputTruncateManager", "SummarizationManager", "MemoryManager", "ConversationManager");

        // For now, we'll allow unknown types to provide flexibility for future context
        // manager types
        // This provides extensibility while still validating known ones
        if (!knownTypes.contains(type)) {
            log
                .debug(
                    "Unknown context manager type '{}' in hook '{}' at index {}. This may be a custom or future type.",
                    type,
                    hookName,
                    index
                );
        }

        return null; // Valid - we allow unknown types for extensibility
    }
}
