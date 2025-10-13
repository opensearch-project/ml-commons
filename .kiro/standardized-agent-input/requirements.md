# Requirements Document

## Introduction

This feature standardizes the agent execution input format to support multiple input types (plain text, multi-modal content, and message-based conversations) while maintaining backward compatibility with the existing `question` parameter approach. The current system uses ModelProviders to automatically create connectors and models, with agents registered using `${parameters.question}` parameter mapping. The new standardized approach will allow flexible input formats through a unified `input` field that can handle text, multi-modal content blocks, and complete message conversations, while preserving existing agent functionality.

## Requirements

### Requirement 1

**User Story:** As a developer using the agent framework, I want to provide input in a standardized `input` field that supports multiple formats, so that I can use the same API regardless of whether I'm sending text, images, or complex conversations.

#### Acceptance Criteria

1. WHEN a user sends a POST request to `/_plugins/_ml/agents/<agent_id>/_execute` with `{"input": "simple text"}` THEN the system SHALL process it as a single user message with text content
2. WHEN a user sends an input with multi-modal content blocks THEN the system SHALL support text, image, video, and document content types
3. WHEN a user sends an input with complete message arrays THEN the system SHALL process conversation-style inputs with role-based messages
4. WHEN input format is invalid THEN the system SHALL return HTTP 400 with clear error message explaining supported input formats
5. WHEN a user provides invalid input format THEN the system SHALL return a clear error message explaining supported formats

### Requirement 2

**User Story:** As a developer maintaining existing agent integrations, I want the new input format to be backward compatible with the current `question` parameter, so that my existing code continues to work without modification.

#### Acceptance Criteria

1. WHEN a user sends a request with the legacy `{"parameters": {"question": "text"}}` format THEN the system SHALL continue to process it correctly
2. WHEN both `input` and `question` parameters are provided THEN the system SHALL prioritize the `input` parameter and log a deprecation warning
3. WHEN an agent uses the current ModelProvider approach THEN the system SHALL map standardized input to the provider's expected parameter format
4. WHEN using legacy format THEN all existing functionality SHALL remain unchanged

### Requirement 3

**User Story:** As a model provider implementer, I want to define how standardized input maps to my connector's request body parameters, so that different model providers can handle the same input format appropriately.

#### Acceptance Criteria

1. WHEN a ModelProvider processes standardized input THEN it SHALL implement an input mapping method that converts standard format to provider-specific parameters
2. WHEN BedrockConverseModelProvider receives standardized input THEN it SHALL map it to the appropriate `messages` array format for the Bedrock Converse API
3. WHEN a new ModelProvider is created THEN it SHALL define its own input mapping logic without affecting other providers
4. WHEN input mapping fails THEN the system SHALL provide clear error messages indicating the mapping issue

### Requirement 4

**User Story:** As a system administrator, I want the agent execution to validate input formats before processing, so that invalid requests are rejected early with helpful error messages.

#### Acceptance Criteria

1. WHEN input validation is performed THEN the system SHALL check for supported content block types (text, image, video, document)
2. WHEN image content is provided THEN the system SHALL validate that source type is either "url" or "base64" and format is specified
3. WHEN message format is used THEN the system SHALL validate that each message has required "role" and "content" fields
4. WHEN validation fails THEN the system SHALL return HTTP 400 with detailed error messages
5. WHEN content blocks contain unsupported types THEN the system SHALL return specific error messages listing supported types

### Requirement 5

**User Story:** As a developer building multi-modal applications, I want to send different types of input in various formats, so that I can create rich conversational experiences with flexibility in how content is provided.

#### Acceptance Criteria

1. WHEN multi-modal input is provided THEN the system SHALL support content blocks with type "text", "image", "video", and "document"
2. WHEN image content is included THEN the system SHALL support both URL references and base64-encoded data with format specification
3. WHEN video content is included THEN the system SHALL support URL references and base64-encoded data with format specification
4. WHEN document content is included THEN the system SHALL support document references in the content block
5. WHEN content is provided in different formats (URL, base64, file references) THEN the system SHALL handle each format appropriately

### Requirement 6

**User Story:** As a developer building conversational applications, I want to send complete conversation histories with multiple messages and roles, so that I can maintain context across complex interactions.

#### Acceptance Criteria

1. WHEN message-based input is provided THEN the system SHALL support an array of message objects
2. WHEN each message is processed THEN it SHALL contain "role" and "content" fields where content is an array of content blocks
3. WHEN role is specified THEN the system SHALL accept any role value as long as the message format is valid
4. WHEN conversation history is provided THEN the system SHALL maintain the order and context of messages
5. WHEN messages contain multi-modal content THEN the system SHALL process each content block appropriately

### Requirement 7

**User Story:** As a system integrator, I want the MLAgentExecutor to seamlessly handle both legacy and new input formats, so that the transition is transparent to end users.

#### Acceptance Criteria

1. WHEN MLAgentExecutor receives input THEN it SHALL detect whether legacy `question` or new `input` format is used
2. WHEN new input format is detected THEN the system SHALL convert it to the appropriate format for the model provider
3. WHEN legacy format is detected THEN the system SHALL continue using existing processing logic
4. WHEN input conversion occurs THEN the system SHALL preserve all semantic meaning and context
5. WHEN conversion fails THEN the system SHALL provide clear error messages with troubleshooting guidance

### Requirement 8

**User Story:** As a developer, I want comprehensive error handling and logging for input processing, so that I can quickly diagnose and fix integration issues.

#### Acceptance Criteria

1. WHEN input processing fails THEN the system SHALL log detailed error information including input format and failure reason
2. WHEN legacy question parameter is used THEN the system SHALL process it normally without warnings since agents may be configured to expect this format
3. WHEN model provider mapping fails THEN the system SHALL log provider-specific error details
4. WHEN validation errors occur THEN the system SHALL provide structured error responses with field-level details
5. WHEN debugging is enabled THEN the system SHALL log input transformation steps for troubleshooting