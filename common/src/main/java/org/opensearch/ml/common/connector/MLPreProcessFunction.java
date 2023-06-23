/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import java.util.HashMap;
import java.util.Map;

public class MLPreProcessFunction {

    private static Map<String, String> PRE_PROCESS_FUNCTIONS;
    public static final String PRE_PROCESS_FUNCTION = "connector.pre_process_function";
    public static final String TEXT_DOCS_TO_COHERE_EMBEDDING_INPUT = "connector.pre_process.cohere.embedding";
    public static final String TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT = "connector.pre_process.openai.embedding";

    static {
        PRE_PROCESS_FUNCTIONS = new HashMap<>();
        //TODO: change to java for openAI, embedding and Titan
        PRE_PROCESS_FUNCTIONS.put(TEXT_DOCS_TO_COHERE_EMBEDDING_INPUT, "\n    StringBuilder builder = new StringBuilder();\n" +
                "    builder.append(\"[\");\n" +
                "    for (int i=0; i< params.text_docs.length; i++) {\n" +
                "        builder.append(\"\\\"\");\n" +
                "        builder.append(params.text_docs[i]);\n" +
                "        builder.append(\"\\\"\");\n" +
                "        if (i < params.text_docs.length - 1) {\n" +
                "          builder.append(\",\")\n" +
                "        }\n" +
                "    }\n" +
                "    builder.append(\"]\");\n" +
                "    def parameters = \"{\" +\"\\\"prompt\\\":\" + builder + \"}\";\n" +
                "    return  \"{\" +\"\\\"parameters\\\":\" + parameters + \"}\";");

        PRE_PROCESS_FUNCTIONS.put(TEXT_DOCS_TO_OPENAI_EMBEDDING_INPUT, "\n    StringBuilder builder = new StringBuilder();\n" +
                        "    builder.append(\"\\\"\");\n" +
                        "    builder.append(params.text_docs[0]);\n" +
                        "    builder.append(\"\\\"\");\n" +
                        "    def parameters = \"{\" +\"\\\"input\\\":\" + builder + \"}\";\n" +
                        "    return  \"{\" +\"\\\"parameters\\\":\" + parameters + \"}\";");
    }

    public static boolean contains(String functionName) {
        return PRE_PROCESS_FUNCTIONS.containsKey(functionName);
    }

    public static String get(String postProcessFunction) {
        return PRE_PROCESS_FUNCTIONS.get(postProcessFunction);
    }
}
