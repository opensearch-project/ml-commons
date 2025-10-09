/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.MLPostProcessFunction;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class ModelInterfaceUtils {

    // Schema loading infrastructure
    private static final String SCHEMA_BASE_PATH = "model-interface-schemas";
    private static final ConcurrentHashMap<String, String> SCHEMA_CACHE = new ConcurrentHashMap<>();

    /**
     * Loads a schema from the resources directory with caching.
     *
     * @param schemaPath The path to the schema file relative to resources root
     * @return The schema content as a String
     * @throws IOException if the schema file cannot be loaded
     */
    private static String loadSchemaFromFile(String schemaPath) throws IOException {
        // Check cache first
        String cachedSchema = SCHEMA_CACHE.get(schemaPath);
        if (cachedSchema != null) {
            return cachedSchema;
        }

        // Load from file using shared utility
        String schema = IndexUtils.loadResourceFromFile(schemaPath, "Schema");

        // Cache and return
        SCHEMA_CACHE.put(schemaPath, schema);
        log.debug("Loaded and cached schema from: {}", schemaPath);
        return schema;
    }

    /**
     * Loads an input schema by name.
     *
     * @param schemaName The name of the input schema file (without .json extension)
     * @return The schema content as a String
     * @throws IOException if the schema file cannot be loaded
     */
    private static String getInputSchema(String schemaName) throws IOException {
        String schemaPath = SCHEMA_BASE_PATH + "/input/" + schemaName + ".json";
        return loadSchemaFromFile(schemaPath);
    }

    /**
     * Loads an output schema by name.
     *
     * @param schemaName The name of the output schema file (without .json extension)
     * @return The schema content as a String
     * @throws IOException if the schema file cannot be loaded
     */
    private static String getOutputSchema(String schemaName) throws IOException {
        String schemaPath = SCHEMA_BASE_PATH + "/output/" + schemaName + ".json";
        return loadSchemaFromFile(schemaPath);
    }

    /**
     * Enum representing all supported model interface schema variants.
     * Each variant defines input and output schema file names and provides lazy loading with caching.
     */
    public enum ModelInterfaceSchema {
        BEDROCK_AI21_LABS_JURASSIC2_MID_V1(
            "bedrock_ai21_labs_jurassic2_mid_v1",
            "general_conversational_single_round_input",
            "general_conversational_single_round_output"
        ),
        BEDROCK_AI21_LABS_JURASSIC2_MID_V1_RAW(
            "bedrock_ai21_labs_jurassic2_mid_v1_raw",
            "general_conversational_single_round_input",
            "bedrock_ai21_j2_raw_output"
        ),
        BEDROCK_ANTHROPIC_CLAUDE_V3_SONNET(
            "bedrock_anthropic_claude_v3_sonnet",
            "general_conversational_single_round_input",
            "general_conversational_single_round_output"
        ),
        BEDROCK_ANTHROPIC_CLAUDE_V2(
            "bedrock_anthropic_claude_v2",
            "general_conversational_single_round_input",
            "bedrock_anthropic_claude_v2_output"
        ),
        BEDROCK_COHERE_EMBED_ENGLISH_V3("bedrock_cohere_embed_english_v3", "general_embedding_input", "general_embedding_output"),
        BEDROCK_COHERE_EMBED_ENGLISH_V3_RAW(
            "bedrock_cohere_embed_english_v3_raw",
            "general_embedding_input",
            "cohere_embedding_v3_raw_output"
        ),
        BEDROCK_COHERE_EMBED_MULTILINGUAL_V3("bedrock_cohere_embed_multilingual_v3", "general_embedding_input", "general_embedding_output"),
        BEDROCK_COHERE_EMBED_MULTILINGUAL_V3_RAW(
            "bedrock_cohere_embed_multilingual_v3_raw",
            "general_embedding_input",
            "cohere_embedding_v3_raw_output"
        ),
        BEDROCK_TITAN_EMBED_TEXT_V1("bedrock_titan_embed_text_v1", "titan_text_embedding_input", "general_embedding_output"),
        BEDROCK_TITAN_EMBED_TEXT_V1_RAW(
            "bedrock_titan_embed_text_v1_raw",
            "titan_text_embedding_input",
            "amazon_titan_embedding_v1_raw_output"
        ),
        BEDROCK_TITAN_EMBED_MULTI_MODAL_V1(
            "bedrock_titan_embed_multi_modal_v1",
            "titan_multi_modal_embedding_input",
            "general_embedding_output"
        ),
        BEDROCK_TITAN_EMBED_MULTI_MODAL_V1_RAW(
            "bedrock_titan_embed_multi_modal_v1_raw",
            "titan_multi_modal_embedding_input",
            "amazon_titan_embedding_v1_raw_output"
        ),
        AMAZON_COMPREHEND_DETECTDOMAINANTLANGUAGE(
            "amazon_comprehend_detectdomainantlanguage",
            "amazon_comprehend_input",
            "amazon_comprehend_output"
        ),
        AMAZON_TEXTRACT_DETECTDOCUMENTTEXT("amazon_textract_detectdocumenttext", "amazon_textract_input", "amazon_textract_output"),
        BEDROCK_ANTHROPIC_CLAUDE_USE_SYSTEM_PROMPT(
            "bedrock_anthropic_claude_use_system_prompt",
            "bedrock_anthropic_claude_use_system_prompt_input",
            "bedrock_anthropic_claude_use_system_prompt_output"
        ),
        OPENAI_CHAT_COMPLETIONS("openai_chat_completions", "openai_chat_completions_input", "openai_chat_completions_output");

        private final String name;
        private final String inputSchemaFile;
        private final String outputSchemaFile;

        ModelInterfaceSchema(String name, String inputSchemaFile, String outputSchemaFile) {
            this.name = name;
            this.inputSchemaFile = inputSchemaFile;
            this.outputSchemaFile = outputSchemaFile;
        }

        /**
         * Gets the model interface as a Map with "input" and "output" keys.
         * Schema files are cached at the I/O level by loadSchemaFromFile().
         *
         * @return Map containing input and output schemas
         * @throws RuntimeException if schemas cannot be loaded
         */
        public Map<String, String> getInterface() {
            try {
                String input = getInputSchema(inputSchemaFile);
                String output = getOutputSchema(outputSchemaFile);
                return Map.of("input", input, "output", output);
            } catch (IOException e) {
                log.error("Failed to load model interface schema: {}", name, e);
                throw new RuntimeException("Failed to load schema: " + name, e);
            }
        }

        /**
         * Finds a ModelInterfaceSchema by its name string (case-insensitive).
         *
         * @param name The schema name to look up
         * @return The matching ModelInterfaceSchema
         * @throws IllegalArgumentException if no matching schema is found
         */
        public static ModelInterfaceSchema fromString(String name) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Schema name cannot be null or blank");
            }

            for (ModelInterfaceSchema schema : values()) {
                if (schema.name.equalsIgnoreCase(name)) {
                    return schema;
                }
            }

            throw new IllegalArgumentException("Unknown model interface schema: " + name);
        }

        public String getName() {
            return name;
        }
    }

    private static ModelInterfaceSchema createPresetModelInterfaceByConnector(Connector connector) {
        if (connector.getParameters() != null) {
            ConnectorAction connectorAction = connector.getActions().get(0);

            // Check for OpenAI Chat Completions models (outside service_name switch)
            String model = connector.getParameters().get("model");
            String url = connectorAction.getUrl();
            if (model != null && url != null) {
                boolean isOpenAIModel = model.equals("gpt-3.5-turbo") || model.equals("gpt-4o-mini") || model.equals("gpt-5");
                boolean isChatCompletionsEndpoint = url.endsWith("v1/chat/completions");

                if (isOpenAIModel && isChatCompletionsEndpoint) {
                    log.debug("Detected OpenAI Chat Completions model: {}", model);
                    return ModelInterfaceSchema.OPENAI_CHAT_COMPLETIONS;
                }
            }

            switch ((connector.getParameters().get("service_name") != null) ? connector.getParameters().get("service_name") : "null") {
                case "bedrock":
                    log.debug("Detected Amazon Bedrock model");
                    switch ((model != null) ? model : "null") {
                        case "ai21.j2-mid-v1":
                            if (connectorAction.getPostProcessFunction() != null && !connectorAction.getPostProcessFunction().isBlank()) {
                                log.debug("Creating preset model interface for Amazon Bedrock model with post-process function: {}", model);
                                return ModelInterfaceSchema.BEDROCK_AI21_LABS_JURASSIC2_MID_V1;
                            } else {
                                log
                                    .debug(
                                        "Creating preset model interface for Amazon Bedrock model without post-process function: {}",
                                        model
                                    );
                                return ModelInterfaceSchema.BEDROCK_AI21_LABS_JURASSIC2_MID_V1_RAW;
                            }
                        case "anthropic.claude-3-sonnet-20240229-v1:0":
                            log.debug("Creating preset model interface for Amazon Bedrock model: {}", model);
                            return ModelInterfaceSchema.BEDROCK_ANTHROPIC_CLAUDE_V3_SONNET;
                        case "anthropic.claude-v2":
                            log.debug("Creating preset model interface for Amazon Bedrock model: {}", model);
                            return ModelInterfaceSchema.BEDROCK_ANTHROPIC_CLAUDE_V2;
                        case "cohere.embed-english-v3":
                            if (connectorAction.getPostProcessFunction() != null
                                && connectorAction.getPostProcessFunction().equalsIgnoreCase(MLPostProcessFunction.COHERE_EMBEDDING)) {
                                log.debug("Creating preset model interface for Amazon Bedrock model with post-process function: {}", model);
                                return ModelInterfaceSchema.BEDROCK_COHERE_EMBED_ENGLISH_V3;
                            } else {
                                log
                                    .debug(
                                        "Creating preset model interface for Amazon Bedrock model without post-process function: {}",
                                        model
                                    );
                                return ModelInterfaceSchema.BEDROCK_COHERE_EMBED_ENGLISH_V3_RAW;
                            }
                        case "cohere.embed-multilingual-v3":
                            if (connectorAction.getPostProcessFunction() != null
                                && connectorAction.getPostProcessFunction().equalsIgnoreCase(MLPostProcessFunction.COHERE_EMBEDDING)) {
                                log.debug("Creating preset model interface for Amazon Bedrock model with post-process function: {}", model);
                                return ModelInterfaceSchema.BEDROCK_COHERE_EMBED_MULTILINGUAL_V3;
                            } else {
                                log
                                    .debug(
                                        "Creating preset model interface for Amazon Bedrock model without post-process function: {}",
                                        model
                                    );
                                return ModelInterfaceSchema.BEDROCK_COHERE_EMBED_MULTILINGUAL_V3_RAW;
                            }
                        case "amazon.titan-embed-text-v1":
                            if (connectorAction.getPostProcessFunction() != null
                                && connectorAction.getPostProcessFunction().equalsIgnoreCase(MLPostProcessFunction.BEDROCK_EMBEDDING)) {
                                log.debug("Creating preset model interface for Amazon Bedrock model with post-process function: {}", model);
                                return ModelInterfaceSchema.BEDROCK_TITAN_EMBED_TEXT_V1;
                            } else {
                                log
                                    .debug(
                                        "Creating preset model interface for Amazon Bedrock model without post-process function: {}",
                                        model
                                    );
                                return ModelInterfaceSchema.BEDROCK_TITAN_EMBED_TEXT_V1_RAW;
                            }
                        case "amazon.titan-embed-image-v1":
                            if (connectorAction.getPostProcessFunction() != null
                                && connectorAction.getPostProcessFunction().equalsIgnoreCase(MLPostProcessFunction.BEDROCK_EMBEDDING)) {
                                log.debug("Creating preset model interface for Amazon Bedrock model with post-process function: {}", model);
                                return ModelInterfaceSchema.BEDROCK_TITAN_EMBED_MULTI_MODAL_V1;
                            } else {
                                log
                                    .debug(
                                        "Creating preset model interface for Amazon Bedrock model without post-process function: {}",
                                        model
                                    );
                                return ModelInterfaceSchema.BEDROCK_TITAN_EMBED_MULTI_MODAL_V1_RAW;
                            }
                        case "us.anthropic.claude-3-7-sonnet-20250219-v1:0":
                        case "us.anthropic.claude-sonnet-4-20250514-v1:0":
                            // Check if use_system_prompt parameter is true
                            String useSystemPrompt = connector.getParameters().get("use_system_prompt");
                            if ("true".equalsIgnoreCase(useSystemPrompt)) {
                                log.debug("Creating preset model interface for Amazon Bedrock Claude model with system prompt: {}", model);
                                return ModelInterfaceSchema.BEDROCK_ANTHROPIC_CLAUDE_USE_SYSTEM_PROMPT;
                            }
                            log.debug("Model {} does not use system prompt parameter, skipping preset interface", model);
                            return null;
                        default:
                            return null;
                    }
                case "comprehend":
                    log.debug("Detected Amazon Comprehend model");
                    switch ((connector.getParameters().get("api_name") != null) ? connector.getParameters().get("api_name") : "null") {
                        // Single case for switch-case statement due to there is one more API in blueprint for Amazon Comprehend Model
                        // Not set here because there is more than one input/output schema for the DetectEntities API
                        // TODO: Add default model interface for Amazon Comprehend DetectEntities APIs
                        case "DetectDominantLanguage":
                            log
                                .debug(
                                    "Creating preset model interface for Amazon Comprehend API: {}",
                                    connector.getParameters().get("api_name")
                                );
                            return ModelInterfaceSchema.AMAZON_COMPREHEND_DETECTDOMAINANTLANGUAGE;
                        default:
                            return null;
                    }
                case "textract":
                    log.debug("Detected Amazon Textract model");
                    log.debug("Creating preset model interface for Amazon Textract DetectDocumentText API");
                    return ModelInterfaceSchema.AMAZON_TEXTRACT_DETECTDOCUMENTTEXT;
                default:
                    return null;
            }
        }
        return null;
    }

    /**
     * Update the model interface fields of the register model input based on the stand-alone connector
     * @param registerModelInput the register model input
     * @param connector the connector
     */
    public static void updateRegisterModelInputModelInterfaceFieldsByConnector(
        MLRegisterModelInput registerModelInput,
        Connector connector
    ) {
        ModelInterfaceSchema schema = createPresetModelInterfaceByConnector(connector);
        if (schema != null) {
            registerModelInput.setModelInterface(schema.getInterface());
        }
    }

    /**
     * Update the model interface fields of the register model input based on the internal connector
     * @param registerModelInput the register model input
     */
    public static void updateRegisterModelInputModelInterfaceFieldsByConnector(MLRegisterModelInput registerModelInput) {
        ModelInterfaceSchema schema = createPresetModelInterfaceByConnector(registerModelInput.getConnector());
        if (schema != null) {
            registerModelInput.setModelInterface(schema.getInterface());
        }
    }
}
