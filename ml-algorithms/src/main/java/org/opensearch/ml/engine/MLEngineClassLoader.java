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

package org.opensearch.ml.engine;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.ml.common.parameter.FunctionName;
import org.opensearch.ml.engine.annotation.Function;
import org.reflections.Reflections;

import java.lang.reflect.Constructor;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


public class MLEngineClassLoader {

    private static final Logger logger = LogManager.getLogger(MLEngineClassLoader.class);
    private static Map<Enum<?>, Class<?>> mlAlgoClassMap = new HashMap<>();

    static {
        try {
            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                loadClassMapping();
                return null;
            });
        } catch (PrivilegedActionException e) {
            throw new RuntimeException("Can't load class mapping in ML engine", e);
        }
    }

    public static void loadClassMapping() {
        Reflections reflections = new Reflections("org.opensearch.ml.engine.algorithms");

        Set<Class<?>> classes = reflections.getTypesAnnotatedWith(Function.class);
        // Load ML algorithm parameter class
        for (Class<?> clazz : classes) {
            Function function = clazz.getAnnotation(Function.class);
            FunctionName functionName = function.value();
            if (functionName != null) {
                mlAlgoClassMap.put(functionName, clazz);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends Enum<T>, S, I extends Object> S initInstance(T type, I in, Class<?> constructorParamClass) {
        Class<?> clazz = mlAlgoClassMap.get(type);
        if (clazz == null) {
            throw new IllegalArgumentException("Can't find class for type " + type);
        }
        try {
            Constructor<?> constructor = clazz.getConstructor(constructorParamClass);
            return (S) constructor.newInstance(in);
        } catch (Exception e) {
            logger.error("Failed to init instance for type " + type, e);
            return null;
        }
    }

}
