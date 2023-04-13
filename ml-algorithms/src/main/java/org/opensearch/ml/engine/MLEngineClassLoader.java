/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.engine.annotation.ConnectorExecutor;
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
    /**
     * This map contains class mapping of enum types like {@link FunctionName}
     */
    private static Map<Enum<?>, Class<?>> mlAlgoClassMap = new HashMap<>();
    private static Map<String, Class<?>> connectorExecutorMap = new HashMap<>();

    /**
     * This map contains pre-created thread-safe ML objects.
     */
    private static Map<Enum<?>, Object> mlObjects = new HashMap<>();

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

    /**
     * Register thread-safe ML objects. "initInstance" method will get thread-safe object from
     * "mlObjects" map first. If not found, will try to create new instance. So if you are not
     * sure if the object to be registered is thread-safe or not, you should NOT register it.
     * @param functionName function name
     * @param obj object
     */
    public static void register(Enum<?> functionName, Object obj) {
        mlObjects.put(functionName, obj);
    }

    /**
     * If you are sure some ML objects will not be used anymore, you can deregister it to release
     * memory.
     * @param functionName function name
     * @return removed object
     */
    public static Object deregister(Enum<?> functionName) {
        return mlObjects.remove(functionName);
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

        Set<Class<?>> connectorExecutorClasses = reflections.getTypesAnnotatedWith(ConnectorExecutor.class);
        // Load connector class
        for (Class<?> clazz : connectorExecutorClasses) {
            ConnectorExecutor connectorExecutor = clazz.getAnnotation(ConnectorExecutor.class);
            String connectorName = connectorExecutor.value();
            if (connectorName != null) {
                connectorExecutorMap.put(connectorName, clazz);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static <T, S, I extends Object> S initInstance(T type, I in, Class<?> constructorParamClass) {
        return initInstance(type, in, constructorParamClass, null);
    }

    /**
     * Get instance from registered ML objects. If not registered, will create new instance.
     * When create new instance, will try constructor with "constructorParamClass" first. If
     * not found, will try default constructor without input parameter.
     * @param type type
     * @param in input parameter of constructor
     * @param constructorParamClass constructor parameter class
     * @param properties class properties
     * @param <T> Enum type
     * @param <S> return class
     * @param <I> input parameter of constructor
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <T, S, I extends Object> S initInstance(T type, I in, Class<?> constructorParamClass, Map<String, Object> properties) {
        if (mlObjects.containsKey(type)) {
            return (S) mlObjects.get(type);
        }
        Class<?> clazz = mlAlgoClassMap.get(type);
        if (clazz == null) {
            clazz = connectorExecutorMap.get(type);
        }
        if (clazz == null) {
            throw new IllegalArgumentException("Can't find class for type " + type);
        }
        try {
            Constructor<?> constructor;
            S instance;
            try {
                constructor = clazz.getConstructor(constructorParamClass);
                instance = (S) constructor.newInstance(in);
            } catch (NoSuchMethodException e) {
                constructor = clazz.getConstructor();
                instance = (S) constructor.newInstance();
            }
            BeanUtils.populate(instance, properties);
            return instance;
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
