/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils;

import java.util.Map;

import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class ModelInterfaceUtils {

    private static final String GENERAL_CONVERSATIONAL_MODEL_INTERFACE_INPUT = "{\n"
        + "    \"type\": \"object\",\n"
        + "    \"properties\": {\n"
        + "        \"parameters\": {\n"
        + "            \"type\": \"object\",\n"
        + "            \"properties\": {\n"
        + "                \"inputs\": {\n"
        + "                    \"type\": \"string\"\n"
        + "                }\n"
        + "            },\n"
        + "            \"required\": [\n"
        + "                \"inputs\"\n"
        + "            ]\n"
        + "        }\n"
        + "    },\n"
        + "    \"required\": [\n"
        + "        \"parameters\"\n"
        + "    ]\n"
        + "}";

    private static final String GENERAL_EMBEDDING_MODEL_INTERFACE_INPUT = "{\n"
        + "    \"type\": \"object\",\n"
        + "    \"properties\": {\n"
        + "        \"parameters\": {\n"
        + "            \"type\": \"object\",\n"
        + "            \"properties\": {\n"
        + "                \"texts\": {\n"
        + "                    \"type\": \"array\",\n"
        + "                    \"items\": {\n"
        + "                        \"type\": \"string\"\n"
        + "                    }\n"
        + "                }\n"
        + "            },\n"
        + "            \"required\": [\n"
        + "                \"texts\"\n"
        + "            ]\n"
        + "        }\n"
        + "    }\n"
        + "}";

    private static final String TITAN_TEXT_EMBEDDING_MODEL_INTERFACE_INPUT = "{\n"
        + "    \"type\": \"object\",\n"
        + "    \"properties\": {\n"
        + "        \"parameters\": {\n"
        + "            \"type\": \"object\",\n"
        + "            \"properties\": {\n"
        + "                \"inputText\": {\n"
        + "                    \"type\": \"string\"\n"
        + "                }\n"
        + "            },\n"
        + "            \"required\": [\n"
        + "                \"inputText\"\n"
        + "            ]\n"
        + "        }\n"
        + "    }\n"
        + "}";

    private static final String TITAN_MULTI_MODAL_EMBEDDING_MODEL_INTERFACE_INPUT = "{\n"
        + "    \"type\": \"object\",\n"
        + "    \"properties\": {\n"
        + "        \"parameters\": {\n"
        + "            \"type\": \"object\",\n"
        + "            \"properties\": {\n"
        + "                \"inputText\": {\n"
        + "                    \"type\": \"string\"\n"
        + "                },\n"
        + "                \"inputImage\": {\n"
        + "                    \"type\": \"string\"\n"
        + "                }\n"
        + "            }\n"
        + "        }\n"
        + "    }\n"
        + "}";

    private static final String AMAZON_COMPREHEND_DETECTDOMAINANTLANGUAGE_API_INTERFACE_INPUT = "{\n"
        + "    \"type\": \"object\",\n"
        + "    \"properties\": {\n"
        + "        \"parameters\": {\n"
        + "            \"type\": \"object\",\n"
        + "            \"properties\": {\n"
        + "                \"Text\": {\n"
        + "                    \"type\": \"string\"\n"
        + "                }\n"
        + "            },\n"
        + "            \"required\": [\n"
        + "                \"Text\"\n"
        + "            ]\n"
        + "        }\n"
        + "    },\n"
        + "    \"required\": [\n"
        + "        \"parameters\"\n"
        + "    ]\n"
        + "}";

    private static final String AMAZON_TEXTRACT_DETECTDOCUMENTTEXT_API_INTERFACE_INPUT = "{\n"
        + "    \"type\": \"object\",\n"
        + "    \"properties\": {\n"
        + "        \"parameters\": {\n"
        + "            \"type\": \"object\",\n"
        + "            \"properties\": {\n"
        + "                \"bytes\": {\n"
        + "                    \"type\": \"string\"\n"
        + "                }\n"
        + "            },\n"
        + "            \"required\": [\n"
        + "                \"bytes\"\n"
        + "            ]\n"
        + "        }\n"
        + "    },\n"
        + "    \"required\": [\n"
        + "        \"parameters\"\n"
        + "    ]\n"
        + "}";

    private static final String GENERAL_CONVERSATIONAL_MODEL_INTERFACE_OUTPUT = "{\n"
        + "    \"type\": \"object\",\n"
        + "    \"properties\": {\n"
        + "        \"inference_results\": {\n"
        + "            \"type\": \"array\",\n"
        + "            \"items\": {\n"
        + "                \"type\": \"object\",\n"
        + "                \"properties\": {\n"
        + "                    \"output\": {\n"
        + "                        \"type\": \"array\",\n"
        + "                        \"items\": {\n"
        + "                            \"type\": \"object\",\n"
        + "                            \"properties\": {\n"
        + "                                \"name\": {\n"
        + "                                    \"type\": \"string\"\n"
        + "                                },\n"
        + "                                \"dataAsMap\": {\n"
        + "                                    \"type\": \"object\",\n"
        + "                                    \"properties\": {\n"
        + "                                        \"response\": {\n"
        + "                                            \"type\": \"string\"\n"
        + "                                        }\n"
        + "                                    },\n"
        + "                                    \"required\": [\n"
        + "                                        \"response\"\n"
        + "                                    ]\n"
        + "                                }\n"
        + "                            },\n"
        + "                            \"required\": [\n"
        + "                                \"name\",\n"
        + "                                \"dataAsMap\"\n"
        + "                            ]\n"
        + "                        }\n"
        + "                    },\n"
        + "                    \"status_code\": {\n"
        + "                        \"type\": \"integer\"\n"
        + "                    }\n"
        + "                },\n"
        + "                \"required\": [\n"
        + "                    \"output\",\n"
        + "                    \"status_code\"\n"
        + "                ]\n"
        + "            }\n"
        + "        }\n"
        + "    },\n"
        + "    \"required\": [\n"
        + "        \"inference_results\"\n"
        + "    ]\n"
        + "}";

    private static final String BEDROCK_ANTHROPIC_CLAUDE_V2_MODEL_INTERFACE_OUTPUT = "{\n"
        + "    \"type\": \"object\",\n"
        + "    \"properties\": {\n"
        + "        \"inference_results\": {\n"
        + "            \"type\": \"array\",\n"
        + "            \"items\": {\n"
        + "                \"type\": \"object\",\n"
        + "                \"properties\": {\n"
        + "                    \"output\": {\n"
        + "                        \"type\": \"array\",\n"
        + "                        \"items\": {\n"
        + "                            \"type\": \"object\",\n"
        + "                            \"properties\": {\n"
        + "                                \"name\": {\n"
        + "                                    \"type\": \"string\"\n"
        + "                                },\n"
        + "                                \"dataAsMap\": {\n"
        + "                                    \"type\": \"object\",\n"
        + "                                    \"properties\": {\n"
        + "                                        \"type\": {\n"
        + "                                            \"type\": \"string\"\n"
        + "                                        },\n"
        + "                                        \"completion\": {\n"
        + "                                            \"type\": \"string\"\n"
        + "                                        },\n"
        + "                                        \"stop_reason\": {\n"
        + "                                            \"type\": \"string\"\n"
        + "                                        },\n"
        + "                                        \"stop\": {\n"
        + "                                            \"type\": \"string\"\n"
        + "                                        }\n"
        + "                                    },\n"
        + "                                    \"required\": [\n"
        + "                                        \"type\",\n"
        + "                                        \"completion\",\n"
        + "                                        \"stop_reason\",\n"
        + "                                        \"stop\"\n"
        + "                                    ]\n"
        + "                                }\n"
        + "                            },\n"
        + "                            \"required\": [\n"
        + "                                \"name\",\n"
        + "                                \"dataAsMap\"\n"
        + "                            ]\n"
        + "                        }\n"
        + "                    },\n"
        + "                    \"status_code\": {\n"
        + "                        \"type\": \"integer\"\n"
        + "                    }\n"
        + "                },\n"
        + "                \"required\": [\n"
        + "                    \"output\",\n"
        + "                    \"status_code\"\n"
        + "                ]\n"
        + "            }\n"
        + "        }\n"
        + "    },\n"
        + "    \"required\": [\n"
        + "        \"inference_results\"\n"
        + "    ]\n"
        + "}";

    private static final String GENERAL_EMBEDDING_MODEL_INTERFACE_OUTPUT = "{\n"
        + "    \"type\": \"object\",\n"
        + "    \"properties\": {\n"
        + "        \"inference_results\": {\n"
        + "            \"type\": \"array\",\n"
        + "            \"items\": {\n"
        + "                \"type\": \"object\",\n"
        + "                \"properties\": {\n"
        + "                    \"output\": {\n"
        + "                        \"type\": \"array\",\n"
        + "                        \"items\": {\n"
        + "                            \"type\": \"object\",\n"
        + "                            \"properties\": {\n"
        + "                                \"name\": {\n"
        + "                                    \"type\": \"string\"\n"
        + "                                },\n"
        + "                                \"data_type\": {\n"
        + "                                    \"type\": \"string\"\n"
        + "                                },\n"
        + "                                \"shape\": {\n"
        + "                                    \"type\": \"array\",\n"
        + "                                    \"items\": {\n"
        + "                                        \"type\": \"integer\"\n"
        + "                                    }\n"
        + "                                },\n"
        + "                                \"data\": {\n"
        + "                                    \"type\": \"array\",\n"
        + "                                    \"items\": {\n"
        + "                                        \"type\": \"number\"\n"
        + "                                    }\n"
        + "                                }\n"
        + "                            },\n"
        + "                            \"required\": [\n"
        + "                                \"name\",\n"
        + "                                \"data_type\",\n"
        + "                                \"shape\",\n"
        + "                                \"data\"\n"
        + "                            ]\n"
        + "                        }\n"
        + "                    },\n"
        + "                    \"status_code\": {\n"
        + "                        \"type\": \"integer\"\n"
        + "                    }\n"
        + "                },\n"
        + "                \"required\": [\n"
        + "                    \"output\",\n"
        + "                    \"status_code\"\n"
        + "                ]\n"
        + "            }\n"
        + "        }\n"
        + "    },\n"
        + "    \"required\": [\n"
        + "        \"inference_results\"\n"
        + "    ]\n"
        + "}";

    private static final String AMAZON_COMPREHEND_DETECTDOMAINANTLANGUAGE_API_INTERFACE_OUTPUT = "{\n"
        + "    \"type\": \"object\",\n"
        + "    \"properties\": {\n"
        + "        \"inference_results\": {\n"
        + "            \"type\": \"array\",\n"
        + "            \"items\": {\n"
        + "                \"type\": \"object\",\n"
        + "                \"properties\": {\n"
        + "                    \"output\": {\n"
        + "                        \"type\": \"array\",\n"
        + "                        \"items\": {\n"
        + "                            \"type\": \"object\",\n"
        + "                            \"properties\": {\n"
        + "                                \"name\": {\n"
        + "                                    \"type\": \"string\"\n"
        + "                                },\n"
        + "                                \"dataAsMap\": {\n"
        + "                                    \"type\": \"object\",\n"
        + "                                    \"properties\": {\n"
        + "                                        \"response\": {\n"
        + "                                            \"type\": \"object\",\n"
        + "                                            \"properties\": {\n"
        + "                                                \"Languages\": {\n"
        + "                                                    \"type\": \"array\",\n"
        + "                                                    \"items\": {\n"
        + "                                                        \"type\": \"object\",\n"
        + "                                                        \"properties\": {\n"
        + "                                                            \"LanguageCode\": {\n"
        + "                                                                \"type\": \"string\"\n"
        + "                                                            },\n"
        + "                                                            \"Score\": {\n"
        + "                                                                \"type\": \"number\"\n"
        + "                                                            }\n"
        + "                                                        },\n"
        + "                                                        \"required\": [\n"
        + "                                                            \"LanguageCode\",\n"
        + "                                                            \"Score\"\n"
        + "                                                        ]\n"
        + "                                                    }\n"
        + "                                                }\n"
        + "                                            },\n"
        + "                                            \"required\": [\n"
        + "                                                \"Languages\"\n"
        + "                                            ]\n"
        + "                                        }\n"
        + "                                    },\n"
        + "                                    \"required\": [\n"
        + "                                        \"response\"\n"
        + "                                    ]\n"
        + "                                }\n"
        + "                            },\n"
        + "                            \"required\": [\n"
        + "                                \"name\",\n"
        + "                                \"dataAsMap\"\n"
        + "                            ]\n"
        + "                        }\n"
        + "                    },\n"
        + "                    \"status_code\": {\n"
        + "                        \"type\": \"integer\"\n"
        + "                    }\n"
        + "                },\n"
        + "                \"required\": [\n"
        + "                    \"output\",\n"
        + "                    \"status_code\"\n"
        + "                ]\n"
        + "            }\n"
        + "        }\n"
        + "    },\n"
        + "    \"required\": [\n"
        + "        \"inference_results\"\n"
        + "    ]\n"
        + "}";

    private static final String AMAZON_TEXTRACT_DETECTDOCUMENTTEXT_API_INTERFACE_OUTPUT = "{\n"
        + "    \"type\": \"object\",\n"
        + "    \"properties\": {\n"
        + "        \"inference_results\": {\n"
        + "            \"type\": \"array\",\n"
        + "            \"items\": {\n"
        + "                \"type\": \"object\",\n"
        + "                \"properties\": {\n"
        + "                    \"output\": {\n"
        + "                        \"type\": \"array\",\n"
        + "                        \"items\": {\n"
        + "                            \"type\": \"object\",\n"
        + "                            \"properties\": {\n"
        + "                                \"name\": {\n"
        + "                                    \"type\": \"string\"\n"
        + "                                },\n"
        + "                                \"dataAsMap\": {\n"
        + "                                    \"type\": \"object\",\n"
        + "                                    \"properties\": {\n"
        + "                                        \"Blocks\": {\n"
        + "                                            \"type\": \"array\",\n"
        + "                                            \"items\": {\n"
        + "                                                \"type\": \"object\",\n"
        + "                                                \"properties\": {\n"
        + "                                                    \"BlockType\": {\n"
        + "                                                        \"type\": \"string\"\n"
        + "                                                    },\n"
        + "                                                    \"Geometry\": {\n"
        + "                                                        \"type\": \"object\",\n"
        + "                                                        \"properties\": {\n"
        + "                                                            \"BoundingBox\": {\n"
        + "                                                                \"type\": \"object\",\n"
        + "                                                                \"properties\": {\n"
        + "                                                                    \"Height\": {\n"
        + "                                                                        \"type\": \"number\"\n"
        + "                                                                    },\n"
        + "                                                                    \"Left\": {\n"
        + "                                                                        \"type\": \"number\"\n"
        + "                                                                    },\n"
        + "                                                                    \"Top\": {\n"
        + "                                                                        \"type\": \"number\"\n"
        + "                                                                    },\n"
        + "                                                                    \"Width\": {\n"
        + "                                                                        \"type\": \"number\"\n"
        + "                                                                    }\n"
        + "                                                                }\n"
        + "                                                            },\n"
        + "                                                            \"Polygon\": {\n"
        + "                                                                \"type\": \"array\",\n"
        + "                                                                \"items\": {\n"
        + "                                                                    \"type\": \"object\",\n"
        + "                                                                    \"properties\": {\n"
        + "                                                                        \"X\": {\n"
        + "                                                                            \"type\": \"number\"\n"
        + "                                                                        },\n"
        + "                                                                        \"Y\": {\n"
        + "                                                                            \"type\": \"number\"\n"
        + "                                                                        }\n"
        + "                                                                    }\n"
        + "                                                                }\n"
        + "                                                            }\n"
        + "                                                        }\n"
        + "                                                    },\n"
        + "                                                    \"Id\": {\n"
        + "                                                        \"type\": \"string\"\n"
        + "                                                    },\n"
        + "                                                    \"Relationships\": {\n"
        + "                                                        \"type\": \"array\",\n"
        + "                                                        \"items\": {\n"
        + "                                                            \"type\": \"object\",\n"
        + "                                                            \"properties\": {\n"
        + "                                                                \"Ids\": {\n"
        + "                                                                    \"type\": \"array\",\n"
        + "                                                                    \"items\": {\n"
        + "                                                                        \"type\": \"string\"\n"
        + "                                                                    }\n"
        + "                                                                },\n"
        + "                                                                \"Type\": {\n"
        + "                                                                    \"type\": \"string\"\n"
        + "                                                                }\n"
        + "                                                            }\n"
        + "                                                        }\n"
        + "                                                    }\n"
        + "                                                }\n"
        + "                                            }\n"
        + "                                        },\n"
        + "                                        \"DetectDocumentTextModelVersion\": {\n"
        + "                                            \"type\": \"string\"\n"
        + "                                        },\n"
        + "                                        \"DocumentMetadata\": {\n"
        + "                                            \"type\": \"object\",\n"
        + "                                            \"properties\": {\n"
        + "                                                \"Pages\": {\n"
        + "                                                    \"type\": \"number\"\n"
        + "                                                }\n"
        + "                                            }\n"
        + "                                        }\n"
        + "                                    }\n"
        + "                                }\n"
        + "                            }\n"
        + "                        }\n"
        + "                    },\n"
        + "                    \"status_code\": {\n"
        + "                        \"type\": \"number\"\n"
        + "                    }\n"
        + "                }\n"
        + "            }\n"
        + "        }\n"
        + "    }\n"
        + "}";

    public static final Map<String, String> BEDROCK_AI21_LABS_JURASSIC2_MID_V1_MODEL_INTERFACE = Map
        .of("input", GENERAL_CONVERSATIONAL_MODEL_INTERFACE_INPUT, "output", GENERAL_CONVERSATIONAL_MODEL_INTERFACE_OUTPUT);

    public static final Map<String, String> BEDROCK_ANTHROPIC_CLAUDE_V3_SONNET_MODEL_INTERFACE = Map
        .of("input", GENERAL_CONVERSATIONAL_MODEL_INTERFACE_INPUT, "output", GENERAL_CONVERSATIONAL_MODEL_INTERFACE_OUTPUT);

    public static final Map<String, String> BEDROCK_ANTHROPIC_CLAUDE_V2_MODEL_INTERFACE = Map
        .of("input", GENERAL_CONVERSATIONAL_MODEL_INTERFACE_INPUT, "output", BEDROCK_ANTHROPIC_CLAUDE_V2_MODEL_INTERFACE_OUTPUT);

    public static final Map<String, String> BEDROCK_COHERE_EMBED_ENGLISH_V3_MODEL_INTERFACE = Map
        .of("input", GENERAL_EMBEDDING_MODEL_INTERFACE_INPUT, "output", GENERAL_EMBEDDING_MODEL_INTERFACE_OUTPUT);

    public static final Map<String, String> BEDROCK_COHERE_EMBED_MULTILINGUAL_V3_MODEL_INTERFACE = Map
        .of("input", GENERAL_EMBEDDING_MODEL_INTERFACE_INPUT, "output", GENERAL_EMBEDDING_MODEL_INTERFACE_OUTPUT);

    public static final Map<String, String> BEDROCK_TITAN_EMBED_TEXT_V1_MODEL_INTERFACE = Map
        .of("input", TITAN_TEXT_EMBEDDING_MODEL_INTERFACE_INPUT, "output", GENERAL_EMBEDDING_MODEL_INTERFACE_OUTPUT);

    public static final Map<String, String> BEDROCK_TITAN_EMBED_MULTI_MODAL_V1_MODEL_INTERFACE = Map
        .of("input", TITAN_MULTI_MODAL_EMBEDDING_MODEL_INTERFACE_INPUT, "output", GENERAL_EMBEDDING_MODEL_INTERFACE_OUTPUT);

    public static final Map<String, String> AMAZON_COMPREHEND_DETECTDOMAINANTLANGUAGE_API_INTERFACE = Map
        .of(
            "input",
            AMAZON_COMPREHEND_DETECTDOMAINANTLANGUAGE_API_INTERFACE_INPUT,
            "output",
            AMAZON_COMPREHEND_DETECTDOMAINANTLANGUAGE_API_INTERFACE_OUTPUT
        );

    public static final Map<String, String> AMAZON_TEXTRACT_DETECTDOCUMENTTEXT_API_INTERFACE = Map
        .of(
            "input",
            AMAZON_TEXTRACT_DETECTDOCUMENTTEXT_API_INTERFACE_INPUT,
            "output",
            AMAZON_TEXTRACT_DETECTDOCUMENTTEXT_API_INTERFACE_OUTPUT
        );

    private static Map<String, String> createPresetModelInterfaceByConnector(Connector connector) {
        if (connector.getParameters() != null) {
            switch ((connector.getParameters().get("service_name") != null) ? connector.getParameters().get("service_name") : "null") {
                case "bedrock":
                    log.debug("Detected Amazon Bedrock model");
                    switch ((connector.getParameters().get("model") != null) ? connector.getParameters().get("model") : "null") {
                        case "ai21.j2-mid-v1":
                            log
                                .debug(
                                    "Creating preset model interface for Amazon Bedrock model: {}",
                                    connector.getParameters().get("model")
                                );
                            return BEDROCK_AI21_LABS_JURASSIC2_MID_V1_MODEL_INTERFACE;
                        case "anthropic.claude-3-sonnet-20240229-v1:0":
                            log
                                .debug(
                                    "Creating preset model interface for Amazon Bedrock model: {}",
                                    connector.getParameters().get("model")
                                );
                            return BEDROCK_ANTHROPIC_CLAUDE_V3_SONNET_MODEL_INTERFACE;
                        case "anthropic.claude-v2":
                            log
                                .debug(
                                    "Creating preset model interface for Amazon Bedrock model: {}",
                                    connector.getParameters().get("model")
                                );
                            return BEDROCK_ANTHROPIC_CLAUDE_V2_MODEL_INTERFACE;
                        case "cohere.embed-english-v3":
                            log
                                .debug(
                                    "Creating preset model interface for Amazon Bedrock model: {}",
                                    connector.getParameters().get("model")
                                );
                            return BEDROCK_COHERE_EMBED_ENGLISH_V3_MODEL_INTERFACE;
                        case "cohere.embed-multilingual-v3":
                            log
                                .debug(
                                    "Creating preset model interface for Amazon Bedrock model: {}",
                                    connector.getParameters().get("model")
                                );
                            return BEDROCK_COHERE_EMBED_MULTILINGUAL_V3_MODEL_INTERFACE;
                        case "amazon.titan-embed-text-v1":
                            log
                                .debug(
                                    "Creating preset model interface for Amazon Bedrock model: {}",
                                    connector.getParameters().get("model")
                                );
                            return BEDROCK_TITAN_EMBED_TEXT_V1_MODEL_INTERFACE;
                        case "amazon.titan-embed-image-v1":
                            log
                                .debug(
                                    "Creating preset model interface for Amazon Bedrock model: {}",
                                    connector.getParameters().get("model")
                                );
                            return BEDROCK_TITAN_EMBED_MULTI_MODAL_V1_MODEL_INTERFACE;
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
                            return AMAZON_COMPREHEND_DETECTDOMAINANTLANGUAGE_API_INTERFACE;
                        default:
                            return null;
                    }
                case "textract":
                    log.debug("Detected Amazon Textract model");
                    log.debug("Creating preset model interface for Amazon Textract DetectDocumentText API");
                    return AMAZON_TEXTRACT_DETECTDOCUMENTTEXT_API_INTERFACE;
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
        Map<String, String> presetModelInterface = createPresetModelInterfaceByConnector(connector);
        if (presetModelInterface != null) {
            registerModelInput.setModelInterface(presetModelInterface);
        }
    }

    /**
     * Update the model interface fields of the register model input based on the internal connector
     * @param registerModelInput the register model input
     */
    public static void updateRegisterModelInputModelInterfaceFieldsByConnector(MLRegisterModelInput registerModelInput) {
        Map<String, String> presetModelInterface = createPresetModelInterfaceByConnector(registerModelInput.getConnector());
        if (presetModelInterface != null) {
            registerModelInput.setModelInterface(presetModelInterface);
        }
    }
}
