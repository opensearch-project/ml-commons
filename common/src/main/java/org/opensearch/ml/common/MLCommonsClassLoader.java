/*
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  The OpenSearch Contributors require contributions made to
 *  this file be licensed under the Apache-2.0 license or a
 *  compatible open source license.
 *
 *  Modifications Copyright OpenSearch Contributors. See
 *  GitHub history for details.
 */

package org.opensearch.ml.common;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.common.xcontent.DeprecationHandler;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.parameter.Input;
import org.opensearch.ml.common.parameter.FunctionName;
import org.opensearch.ml.common.parameter.MLAlgoParams;
import org.opensearch.ml.common.parameter.MLOutputType;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;


public class MLCommonsClassLoader {

    private static final Logger logger = LogManager.getLogger(MLCommonsClassLoader.class);
    private static Map<Enum<?>, Class<?>> parameterClassMap = new HashMap<>();

    private static Map<Enum<?>, Class<?>> mlAlgoClassMap = new HashMap<>();

    static {
        loadClassMapping(MLCommonsClassLoader.class, "/ml-commons-config.yml");
    }

    public static void loadClassMapping(Class<?> resource, String configFile) {
        InputStream inputStream = resource.getResourceAsStream(configFile);
        try (
                XContentParser parser = XContentFactory.xContent(XContentType.YAML)
                        .createParser(NamedXContentRegistry.EMPTY, DeprecationHandler.THROW_UNSUPPORTED_OPERATION, inputStream)
        ) {
            if (parser.currentToken() == null) {
                parser.nextToken();
            }
            while(parser.nextToken() != XContentParser.Token.END_OBJECT) {
                XContentParser.Token currentToken = parser.currentToken();
                if (currentToken == XContentParser.Token.FIELD_NAME) {
                    String key = parser.currentName();
                    if ("ml_algo_param_class".equals(key)) {
                        parseMLAlgoParams(parser, parameterClassMap, k -> FunctionName.fromString(k));
                    } else if ("ml_input_data_set_class".equals(key)) {
                        parseMLAlgoParams(parser, parameterClassMap, k -> MLInputDataType.fromString(k));
                    } else if ("ml_output_class".equals(key)) {
                        parseMLAlgoParams(parser, parameterClassMap, k -> MLOutputType.fromString(k));
                    } else if ("ml_algo_class".equals(key)) {
                        parseMLAlgoParams(parser, mlAlgoClassMap, k -> FunctionName.fromString(k));
                    } else if ("executable_function_class".equals(key)) {
                        parseMLAlgoParams(parser, mlAlgoClassMap, k -> FunctionName.fromString(k));
                    }
                } else {
                    parser.nextToken();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends Enum<T>, S, I extends Object> S initInstance(T type, I in, Class<?> constructorParamClass) {
        Class<?> clazz = constructorParamClass == MLAlgoParams.class || constructorParamClass == Input.class ? mlAlgoClassMap.get(type) : parameterClassMap.get(type);
        if (clazz == null) {
            throw new IllegalArgumentException("Can't find class for type " + type);
        }
        try {
            Constructor<?> constructor = clazz.getConstructor(constructorParamClass);
            return (S) constructor.newInstance(in);
        } catch (Exception e) {
            return null;
        }
    }

    private static <T extends Enum<?>> void parseMLAlgoParams(XContentParser parser, Map<T, Class<?>> map, Function<String, T> enumParser) throws IOException, ClassNotFoundException {
        String key = null;
        String value = null;
        while(parser.nextToken() != XContentParser.Token.END_OBJECT) {
            XContentParser.Token currentToken = parser.currentToken();
            if (currentToken == XContentParser.Token.FIELD_NAME) {
                key = parser.currentName();
            } else if (currentToken == XContentParser.Token.VALUE_STRING) {
                value = parser.text();
                if (key != null) {
                    try {
                        map.put(enumParser.apply(key), Class.forName(value));
                    } catch (Exception e) {
                        logger.error("Failed to load class mapping for " + key, e);
                    }
                }
                key = null;
            }
        }
    }

}
