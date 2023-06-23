/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import java.util.HashMap;
import java.util.Map;

public class MLPostProcessFunction {

    private static Map<String, String> POST_PROCESS_FUNCTIONS;
    public static final String POST_PROCESS_FUNCTION = "connector.post_process_function";
    public static final String COHERE_EMBEDDING = "connector.post_process.cohere.embedding";
    public static final String OPENAI_EMBEDDING = "connector.post_process.openai.embedding";

    static {
        POST_PROCESS_FUNCTIONS = new HashMap<>();
        POST_PROCESS_FUNCTIONS.put(COHERE_EMBEDDING, "\n      def name = \"sentence_embedding\";\n" +
                "      def dataType = \"FLOAT32\";\n" +
                "      if (params.embeddings == null || params.embeddings.length == 0) {\n" +
                "          return null;\n" +
                "      }\n" +
                "      def embeddings = params.embeddings;\n" +
                "      StringBuilder builder = new StringBuilder(\"[\");\n" +
                "      for (int i=0; i<embeddings.length; i++) {\n" +
                "        def shape = [embeddings[i].length];\n" +
                "        def json = \"{\" +\n" +
                "                 \"\\\"name\\\":\\\"\" + name + \"\\\",\" +\n" +
                "                 \"\\\"data_type\\\":\\\"\" + dataType + \"\\\",\" +\n" +
                "                 \"\\\"shape\\\":\" + shape + \",\" +\n" +
                "                 \"\\\"data\\\":\" + embeddings[i] +\n" +
                "                 \"}\";\n" +
                "        builder.append(json);\n" +
                "        if (i < embeddings.length - 1) {\n" +
                "          builder.append(\",\");\n" +
                "        }\n" +
                "      }\n" +
                "      builder.append(\"]\");\n" +
                "      \n" +
                "      return builder.toString();\n    ");

        POST_PROCESS_FUNCTIONS.put(OPENAI_EMBEDDING, "\n      def name = \"sentence_embedding\";\n" +
                "      def dataType = \"FLOAT32\";\n" +
                "      if (params.data == null || params.data.length == 0) {\n" +
                "          return null;\n" +
                "      }\n" +
                "      def shape = [params.data[0].embedding.length];\n" +
                "      def json = \"{\" +\n" +
                "                 \"\\\"name\\\":\\\"\" + name + \"\\\",\" +\n" +
                "                 \"\\\"data_type\\\":\\\"\" + dataType + \"\\\",\" +\n" +
                "                 \"\\\"shape\\\":\" + shape + \",\" +\n" +
                "                 \"\\\"data\\\":\" + params.data[0].embedding +\n" +
                "                 \"}\";\n" +
                "      return json;\n    ");
    }

    public static boolean contains(String functionName) {
        return POST_PROCESS_FUNCTIONS.containsKey(functionName);
    }

    public static String get(String postProcessFunction) {
        return POST_PROCESS_FUNCTIONS.get(postProcessFunction);
    }
}
