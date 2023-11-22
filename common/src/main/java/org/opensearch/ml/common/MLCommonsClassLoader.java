/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import java.lang.reflect.Constructor;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.opensearch.ml.common.annotation.Connector;
import org.opensearch.ml.common.annotation.ExecuteInput;
import org.opensearch.ml.common.annotation.ExecuteOutput;
import org.opensearch.ml.common.annotation.InputDataSet;
import org.opensearch.ml.common.annotation.MLAlgoOutput;
import org.opensearch.ml.common.annotation.MLAlgoParameter;
import org.opensearch.ml.common.annotation.MLInput;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.output.MLOutputType;
import org.reflections.Reflections;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class MLCommonsClassLoader {

    private static Map<Enum<?>, Class<?>> parameterClassMap = new HashMap<>();
    private static Map<Enum<?>, Class<?>> executeInputClassMap = new HashMap<>();
    private static Map<Enum<?>, Class<?>> executeOutputClassMap = new HashMap<>();
    private static Map<Enum<?>, Class<?>> mlInputClassMap = new HashMap<>();
    private static Map<String, Class<?>> connectorClassMap = new HashMap<>();

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
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(MLCommonsClassLoader.class.getClassLoader());
            loadMLAlgoParameterClassMapping();
            loadMLOutputClassMapping();
            loadMLInputDataSetClassMapping();
            loadExecuteInputClassMapping();
            loadExecuteOutputClassMapping();
            loadMLInputClassMapping();
            loadConnectorClassMapping();
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private static void loadConnectorClassMapping() {
        Reflections reflections = new Reflections("org.opensearch.ml.common.connector");
        Set<Class<?>> classes = reflections.getTypesAnnotatedWith(Connector.class);
        for (Class<?> clazz : classes) {
            Connector connector = clazz.getAnnotation(Connector.class);
            if (connector != null) {
                String name = connector.value();
                if (name != null && name.length() > 0) {
                    connectorClassMap.put(name, clazz);
                }
            }
        }
    }

    /**
     * Load ML algorithm parameter and ML output class.
     */
    private static void loadMLAlgoParameterClassMapping() {
        Reflections reflections = new Reflections("org.opensearch.ml.common.input.parameter");

        Set<Class<?>> classes = reflections.getTypesAnnotatedWith(MLAlgoParameter.class);
        // Load ML algorithm parameter class
        for (Class<?> clazz : classes) {
            MLAlgoParameter mlAlgoParameter = clazz.getAnnotation(MLAlgoParameter.class);
            if (mlAlgoParameter != null) {
                FunctionName[] algorithms = mlAlgoParameter.algorithms();
                if (algorithms != null && algorithms.length > 0) {
                    for (FunctionName name : algorithms) {
                        parameterClassMap.put(name, clazz);
                    }
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
     * Load ML algorithm parameter and ML output class.
     */
    private static void loadMLOutputClassMapping() {
        Reflections reflections = new Reflections("org.opensearch.ml.common.output");

        Set<Class<?>> classes = reflections.getTypesAnnotatedWith(MLAlgoOutput.class);
        for (Class<?> clazz : classes) {
            MLAlgoOutput mlAlgoOutput = clazz.getAnnotation(MLAlgoOutput.class);
            if (mlAlgoOutput != null) {
                MLOutputType mlOutputType = mlAlgoOutput.value();
                if (mlOutputType != null) {
                    parameterClassMap.put(mlOutputType, clazz);
                }
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
            if (inputDataSet != null) {
                MLInputDataType value = inputDataSet.value();
                if (value != null) {
                    parameterClassMap.put(value, clazz);
                }
            }
        }
    }

    /**
     * Load execute input output class.
     */
    private static void loadExecuteInputClassMapping() {
        Reflections reflections = new Reflections("org.opensearch.ml.common.input.execute");
        Set<Class<?>> classes = reflections.getTypesAnnotatedWith(ExecuteInput.class);
        for (Class<?> clazz : classes) {
            ExecuteInput executeInput = clazz.getAnnotation(ExecuteInput.class);
            if (executeInput != null) {
                FunctionName[] algorithms = executeInput.algorithms();
                if (algorithms != null && algorithms.length > 0) {
                    for (FunctionName name : algorithms) {
                        executeInputClassMap.put(name, clazz);
                    }
                }
            }
        }
    }

    /**
     * Load execute input output class.
     */
    private static void loadExecuteOutputClassMapping() {
        Reflections reflections = new Reflections("org.opensearch.ml.common.output.execute");
        Set<Class<?>> classes = reflections.getTypesAnnotatedWith(ExecuteOutput.class);
        for (Class<?> clazz : classes) {
            ExecuteOutput executeOutput = clazz.getAnnotation(ExecuteOutput.class);
            if (executeOutput != null) {
                FunctionName[] algorithms = executeOutput.algorithms();
                if (algorithms != null && algorithms.length > 0) {
                    for (FunctionName name : algorithms) {
                        executeOutputClassMap.put(name, clazz);
                    }
                }
            }
        }
    }

    private static void loadMLInputClassMapping() {
        Reflections reflections = new Reflections("org.opensearch.ml.common.input");
        Set<Class<?>> classes = reflections.getTypesAnnotatedWith(MLInput.class);
        for (Class<?> clazz : classes) {
            MLInput mlInput = clazz.getAnnotation(MLInput.class);
            if (mlInput != null) {
                FunctionName[] algorithms = mlInput.functionNames();
                if (algorithms != null && algorithms.length > 0) {
                    for (FunctionName name : algorithms) {
                        mlInputClassMap.put(name, clazz);
                    }
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
    private static <T, S, I extends Object> S init(Map<T, Class<?>> map, T type, I in, Class<?> constructorParamClass) {
        Class<?> clazz = map.get(type);
        if (clazz == null) {
            throw new IllegalArgumentException("Can't find class for type " + type);
        }
        try {
            Constructor<?> constructor = clazz.getConstructor(constructorParamClass);
            return (S) constructor.newInstance(in);
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof MLException || cause instanceof IllegalArgumentException) {
                throw (RuntimeException) cause;
            } else {
                log.error("Failed to init instance for type " + type, e);
                return null;
            }
        }
    }

    public static boolean canInitMLInput(FunctionName functionName) {
        return mlInputClassMap.containsKey(functionName);
    }

    public static <S> S initConnector(String name, Object[] initArgs, Class<?>... constructorParameterTypes) {
        return init(connectorClassMap, name, initArgs, constructorParameterTypes);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Enum<T>, S> S initMLInput(T type, Object[] initArgs, Class<?>... constructorParameterTypes) {
        return init(mlInputClassMap, type, initArgs, constructorParameterTypes);
    }

    private static <T, S> S init(Map<T, Class<?>> map, T type, Object[] initArgs, Class<?>... constructorParameterTypes) {
        Class<?> clazz = map.get(type);
        if (clazz == null) {
            throw new IllegalArgumentException("Can't find class for type " + type);
        }
        try {
            Constructor<?> constructor = clazz.getConstructor(constructorParameterTypes);
            return (S) constructor.newInstance(initArgs);
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof MLException) {
                throw (MLException) cause;
            } else if (cause instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) cause;
            } else {
                log.error("Failed to init instance for type " + type, e);
                return null;
            }
        }
    }
}
