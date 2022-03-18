/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine;

import org.junit.Test;
import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.ml.common.parameter.FunctionName;
import org.opensearch.ml.common.parameter.Input;
import org.opensearch.ml.common.parameter.LocalSampleCalculatorInput;
import org.opensearch.ml.common.parameter.LocalSampleCalculatorOutput;
import org.opensearch.ml.engine.algorithms.sample.LocalSampleCalculator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

public class MLEngineClassLoaderTests {

    @Test
    public void initInstance_LocalSampleCalculator() {
        List<Double> inputData = new ArrayList<>();
        double d1 = 10.0;
        double d2 = 20.0;
        inputData.add(d1);
        inputData.add(d2);
        LocalSampleCalculatorInput input = LocalSampleCalculatorInput.builder().operation("sum").inputData(inputData).build();

        Map<String, Object> properties = new HashMap<>();
        properties.put("wrongField", "test");
        Client client = mock(Client.class);
        properties.put("client", client);
        Settings settings = Settings.EMPTY;
        properties.put("settings", settings);

        // set properties
        MLEngineClassLoader.deregister(FunctionName.LOCAL_SAMPLE_CALCULATOR);
        LocalSampleCalculator instance = MLEngineClassLoader.initInstance(FunctionName.LOCAL_SAMPLE_CALCULATOR, input, Input.class, properties);
        LocalSampleCalculatorOutput output = (LocalSampleCalculatorOutput) instance.execute(input);
        assertEquals(d1 + d2, output.getResult(), 1e-6);
        assertEquals(client, instance.getClient());
        assertEquals(settings, instance.getSettings());

        // don't set properties
        instance = MLEngineClassLoader.initInstance(FunctionName.LOCAL_SAMPLE_CALCULATOR, input, Input.class);
        output = (LocalSampleCalculatorOutput) instance.execute(input);
        assertEquals(d1 + d2, output.getResult(), 1e-6);
        assertNull(instance.getClient());
        assertNull(instance.getSettings());
    }

    @Test
    public void initInstance_LocalSampleCalculator_RegisterFirst() {
        Client client = mock(Client.class);
        Settings settings = Settings.EMPTY;
        LocalSampleCalculator calculator = new LocalSampleCalculator(client, settings);
        MLEngineClassLoader.register(FunctionName.LOCAL_SAMPLE_CALCULATOR, calculator);

        LocalSampleCalculator instance = MLEngineClassLoader.initInstance(FunctionName.LOCAL_SAMPLE_CALCULATOR, null, Input.class);
        assertEquals(calculator, instance);
    }
}
