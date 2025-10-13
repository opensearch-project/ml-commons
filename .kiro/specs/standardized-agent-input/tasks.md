# Implementation Plan

- [x] 1. Create standard input data structures and enums
  - Create ContentType enum with TEXT, IMAGE, VIDEO, DOCUMENT values
  - Create SourceType enum with URL, BASE64 values  
  - Create ContentBlock class with type field and content-specific fields
  - Create ImageContent, VideoContent, DocumentContent classes with type, format, and data fields
  - Create Message class with role and content fields
  - Create AgentInput class with input field and getInputType() method
  - Create InputType enum with TEXT, CONTENT_BLOCKS, MESSAGES, UNKNOWN values
  - _Requirements: 1.1, 1.2, 5.1, 6.1, 6.2_

- [x] 2. Implement input validation component
  - Create InputValidator class with validateAgentInput() method
  - Implement validateContentBlocks() method to validate content block arrays
  - Implement validateMessages() method to validate message arrays  
  - Implement validateContentBlock() method to validate individual content blocks
  - Implement validateImageContent(), validateVideoContent(), validateDocumentContent() methods
  - Create ValidationException class for validation errors
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

- [x] 3. Create agent input processor component
  - Create AgentInputProcessor class with validateInput() method (renamed from processInput)
  - Implement validateTextInput() method to handle simple text input validation
  - Implement validateContentBlocks() method to handle multi-modal content validation
  - Implement validateMessages() method to handle conversation-style input validation
  - Validation ensures input is ready for ModelProvider processing
  - _Requirements: 1.1, 5.1, 5.2, 5.3, 5.4, 5.5, 6.1, 6.2, 6.4_

- [x] 4. Extend ModelProvider interface with input mapping methods
  - Add abstract mapTextInput() method to ModelProvider base class
  - Add abstract mapContentBlocks() method to ModelProvider base class
  - Add abstract mapMessages() method to ModelProvider base class
  - Add protected createDefaultParameters() helper method for backward compatibility
  - _Requirements: 3.1, 3.2, 3.3_

- [x] 5. Implement BedrockConverseModelProvider input mapping
  - Implement mapTextInput() method to return prompt parameter
  - Implement mapContentBlocks() method to convert content blocks to Bedrock format
  - Implement mapMessages() method to convert messages to Bedrock format
  - Create BedrockMessageConverter utility class for format conversion
  - Implement convertContentBlocksToBedrockMessages() method
  - Implement convertMessagesToBedrockFormat() method
  - _Requirements: 3.1, 3.2, 5.1, 5.2, 5.3, 5.4, 6.1, 6.2, 6.4_

- [x] 6. Update AgentMLInput to support standard input format
  - Add AgentInput field to AgentMLInput class
  - Update XContentParser constructor to parse "input" field
  - Implement parseAgentInput() method to handle different input formats
  - Add hasStandardInput() and hasLegacyInput() helper methods
  - Update writeTo() and StreamInput constructor for serialization
  - _Requirements: 1.1, 1.2, 2.1, 7.1, 7.2_

- [x] 7. Enhance MLAgentExecutor with dual input processing
  - Add AgentInputProcessor and InputValidator fields to MLAgentExecutor
  - Update execute() method to detect input format type
  - Implement processStandardInput() method for new input format
  - Implement processLegacyInput() method for backward compatibility
  - Implement getModelProviderForAgent() method to determine provider from agent config
  - Update continueExecution() method to work with both input types
  - _Requirements: 2.1, 2.2, 7.1, 7.2, 7.3, 7.4, 7.5_

- [ ] 8. Add comprehensive error handling and logging
  - Implement structured error responses with ValidationException details
  - Add logging for input processing steps and provider mapping
  - Create error response format with type, message, and details fields
  - Add error handling for model provider detection failures
  - Add error handling for input mapping failures
  - _Requirements: 8.1, 8.3, 8.4, 8.5_

- [ ] 9. Enhance memory system for rich multi-modal storage (Future Enhancement)
  - Extend ConversationIndexMessage to support structured input storage
  - Add fields for storing complete AgentInput with images, documents, videos
  - Implement serialization/deserialization for multi-modal content
  - Update memory retrieval to reconstruct original input format with media
  - Enable rich conversation replay with full context preservation
  - Add backward compatibility for existing text-based storage
  - Support content block metadata (image descriptions, document titles, etc.)
  - _Requirements: 2.1, 2.2, 7.1, 7.2_

- [ ]* 10. Create unit tests for input validation
  - Write tests for ContentBlock validation with all content types
  - Write tests for Message validation with various roles
  - Write tests for AgentInput validation with different input formats
  - Write tests for validation error cases and edge conditions
  - Write tests for ValidationException handling

- [ ]* 11. Create unit tests for input validation
  - Write tests for AgentInputProcessor.validateInput() with text input
  - Write tests for AgentInputProcessor.validateInput() with content blocks
  - Write tests for AgentInputProcessor.validateInput() with messages
  - Write tests for validation error cases and edge conditions
  - Write tests for ValidationException handling

- [ ]* 12. Create unit tests for ModelProvider input mapping
  - Write tests for BedrockConverseModelProvider mapTextInput() method
  - Write tests for BedrockConverseModelProvider mapContentBlocks() method  
  - Write tests for BedrockConverseModelProvider mapMessages() method
  - Write tests for BedrockMessageConverter utility methods
  - Write tests for error handling in input mapping

- [ ]* 13. Create integration tests for end-to-end agent execution
  - Write tests for agent execution with standard text input
  - Write tests for agent execution with multi-modal content blocks
  - Write tests for agent execution with message-based conversations
  - Write tests for backward compatibility with legacy question parameter
  - Write tests for mixed input scenarios and error cases