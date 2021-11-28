/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.engine;

import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.parameter.Input;
import org.opensearch.ml.common.parameter.FunctionName;
import org.opensearch.ml.common.parameter.MLAlgoParams;
import org.opensearch.ml.common.parameter.MLOutput;
import org.opensearch.ml.common.parameter.Output;
import org.opensearch.ml.common.MLCommonsClassLoader;
import org.opensearch.ml.engine.annotation.MLAlgorithm;
import org.opensearch.ml.engine.exceptions.MetaDataException;
import org.reflections.Reflections;

import java.lang.reflect.InvocationTargetException;
import java.util.Set;

/**
 * This is the interface to all ml algorithms.
 */
public class MLEngine {
    private static final String ALGO_PACKAGE_NAME = "org.opensearch.ml.engine.algorithms";
    static {
        MLCommonsClassLoader.loadClassMapping(MLEngine.class, "/ml-algorithm-config.yml");
    }

    public static MLOutput predict(FunctionName algoName, MLAlgoParams parameters, DataFrame dataFrame, Model model) {
        if (algoName == null) {
            throw new IllegalArgumentException("Algo name should not be null");
        }
        MLAlgo mlAlgo = MLCommonsClassLoader.initInstance(algoName, parameters, MLAlgoParams.class);
        if (mlAlgo == null) {
            throw new IllegalArgumentException("Unsupported algorithm: " + algoName);
        }
        return mlAlgo.predict(dataFrame, model);
    }

    public static Model train(FunctionName algoName, MLAlgoParams parameters, DataFrame dataFrame) {
        if (algoName == null) {
            throw new IllegalArgumentException("Algo name should not be null");
        }
        MLAlgo mlAlgo = MLCommonsClassLoader.initInstance(algoName, parameters, MLAlgoParams.class);
        if (mlAlgo == null) {
            throw new IllegalArgumentException("Unsupported algorithm: " + algoName);
        }
        return mlAlgo.train(dataFrame);
    }

    public static Output execute(Input input) {
        if (input == null) {
            throw new IllegalArgumentException("Algo name should not be null");
        }
        Executable function = MLCommonsClassLoader.initInstance(input.getFunctionName(), input, Input.class);
        if (function == null) {
            throw new IllegalArgumentException("Unsupported algorithm: " + input.getFunctionName());
        }
        return function.execute(input);
    }

    public static MLEngineMetaData getMetaData() {
        MLEngineMetaData engineMetaData = new MLEngineMetaData();
        Reflections reflections = new Reflections(ALGO_PACKAGE_NAME);
        Set<Class<?>> classes = reflections.getTypesAnnotatedWith(MLAlgorithm.class);
        try {
            for (Class c : classes) {
                MLAlgo algo = (MLAlgo) c.getDeclaredConstructor().newInstance();
                engineMetaData.addAlgoMetaData(algo.getMetaData());
            }
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new MetaDataException("Failed to get ML engine meta data.", e.getCause());
        }
        return engineMetaData;
    }
}
