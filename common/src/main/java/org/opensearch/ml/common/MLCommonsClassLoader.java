/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.ml.common.annotation.ExecuteInput;
import org.opensearch.ml.common.annotation.ExecuteOutput;
import org.opensearch.ml.common.annotation.InputDataSet;
import org.opensearch.ml.common.annotation.MLAlgoOutput;
import org.opensearch.ml.common.annotation.MLAlgoParameter;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.exception.MLException;
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
    private static Map<Enum<?>, Class<?>> executeInputClassMap = new HashMap<>();
    private static Map<Enum<?>, Class<?>> executeOutputClassMap = new HashMap<>();

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
        loadExecuteInputOutputClassMapping();
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

    /**
     * Load execute input output class.
     */
    private static void loadExecuteInputOutputClassMapping() {
        Reflections reflections = new Reflections("org.opensearch.ml.common.parameter");

        Set<Class<?>> classes = reflections.getTypesAnnotatedWith(ExecuteInput.class);
        // Load execute input class
        for (Class<?> clazz : classes) {
            ExecuteInput executeInput = clazz.getAnnotation(ExecuteInput.class);
            FunctionName[] algorithms = executeInput.algorithms();
            if (algorithms != null && algorithms.length > 0) {
                for(FunctionName name : algorithms){
                    executeInputClassMap.put(name, clazz);
                }
            }
        }

        // Load execute output class
        classes = reflections.getTypesAnnotatedWith(ExecuteOutput.class);
        for (Class<?> clazz : classes) {
            ExecuteOutput executeOutput = clazz.getAnnotation(ExecuteOutput.class);
            FunctionName[] algorithms = executeOutput.algorithms();
            if (algorithms != null && algorithms.length > 0) {
                for(FunctionName name : algorithms){
                    executeOutputClassMap.put(name, clazz);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends Enum<T>, S, I extends Object> S initMLInstance(T type, I in, Class<?> constructorParamClass) {
        return init(parameterClassMap, type, in, constructorParamClass);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Enum<T>, S, I extends Object> S initExecuteInputInstance(T type, I in, Class<?> constructorParamClass) {
        return init(executeInputClassMap, type, in, constructorParamClass);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Enum<T>, S, I extends Object> S initExecuteOutputInstance(T type, I in, Class<?> constructorParamClass) {
        return init(executeOutputClassMap, type, in, constructorParamClass);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Enum<T>, S, I extends Object> S init(Map<Enum<?>, Class<?>> map, T type, I in, Class<?> constructorParamClass) {
        Class<?> clazz = map.get(type);
        if (clazz == null) {
            throw new IllegalArgumentException("Can't find class for type " + type);
        }
        try {
            Constructor<?> constructor = clazz.getConstructor(constructorParamClass);
            return (S) constructor.newInstance(in);
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof MLException) {
                throw (MLException)cause;
            } else {
                logger.error("Failed to init instance for type " + type, e);
                return null;
            }
        }
    }

}
