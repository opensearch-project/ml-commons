/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.ml.common.annotation.InputDataSet;
import org.opensearch.ml.common.annotation.MLAlgoOutput;
import org.opensearch.ml.common.annotation.MLAlgoParameter;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.parameter.FunctionName;
import org.opensearch.ml.common.parameter.MLOutputType;
import org.reflections.Reflections;

import java.lang.reflect.Constructor;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


public class MLCommonsClassLoader {

    private static final Logger logger = LogManager.getLogger(MLCommonsClassLoader.class);
    private static Map<Enum<?>, Class<?>> parameterClassMap = new HashMap<>();

    static {
        try {
            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                loadClassMapping();
                return null;
            });
        } catch (PrivilegedActionException e) {
            throw new RuntimeException("Can't load class mapping in ML commons", e);
        }
    }

    public static void loadClassMapping() {
        loadMLAlgoParameterClassMapping();
        loadMLInputDataSetClassMapping();
    }

    /**
     * Load ML algorithm parameter and ML output class.
     */
    private static void loadMLAlgoParameterClassMapping() {
        Reflections reflections = new Reflections("org.opensearch.ml.common.parameter");

        Set<Class<?>> classes = reflections.getTypesAnnotatedWith(MLAlgoParameter.class);
        // Load ML algorithm parameter class
        for (Class<?> clazz : classes) {
            MLAlgoParameter mlAlgoParameter = clazz.getAnnotation(MLAlgoParameter.class);
            FunctionName[] algorithms = mlAlgoParameter.algorithms();
            if (algorithms != null && algorithms.length > 0) {
                for(FunctionName name : algorithms){
                    parameterClassMap.put(name, clazz);
                }
            }
        }

        // Load ML output class
        classes = reflections.getTypesAnnotatedWith(MLAlgoOutput.class);
        for (Class<?> clazz : classes) {
            MLAlgoOutput mlAlgoOutput = clazz.getAnnotation(MLAlgoOutput.class);
            MLOutputType mlOutputType = mlAlgoOutput.value();
            if (mlOutputType != null) {
                parameterClassMap.put(mlOutputType, clazz);
            }
        }
    }

    /**
     * Load ML input data set class
     */
    private static void loadMLInputDataSetClassMapping() {
        Reflections reflections = new Reflections("org.opensearch.ml.common.dataset");
        Set<Class<?>> classes = reflections.getTypesAnnotatedWith(InputDataSet.class);
        for (Class<?> clazz : classes) {
            InputDataSet inputDataSet = clazz.getAnnotation(InputDataSet.class);
            MLInputDataType value = inputDataSet.value();
            if (value != null) {
                parameterClassMap.put(value, clazz);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends Enum<T>, S, I extends Object> S initInstance(T type, I in, Class<?> constructorParamClass) {
        Class<?> clazz = parameterClassMap.get(type);
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
