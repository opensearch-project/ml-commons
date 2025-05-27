/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.Test;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.input.execute.samplecalculator.LocalSampleCalculatorInput;
import org.opensearch.ml.common.input.parameter.MLAlgoParams;
import org.opensearch.ml.common.output.Output;
import org.opensearch.ml.common.output.execute.samplecalculator.LocalSampleCalculatorOutput;
import org.opensearch.ml.engine.algorithms.metrics_correlation.MetricsCorrelation;
import org.opensearch.ml.engine.algorithms.sample.LocalSampleCalculator;
import org.opensearch.transport.client.Client;

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
        final LocalSampleCalculator instance = MLEngineClassLoader
            .initInstance(FunctionName.LOCAL_SAMPLE_CALCULATOR, input, Input.class, properties);

        ActionListener<Output> actionListener = ActionListener.wrap(o -> {
            LocalSampleCalculatorOutput output = (LocalSampleCalculatorOutput) o;
            assertEquals(d1 + d2, output.getResult(), 1e-6);
            assertEquals(client, instance.getClient());
            assertEquals(settings, instance.getSettings());
        }, e -> { fail("Test failed: " + e.getMessage()); });

        instance.execute(input, actionListener);

        // don't set properties
        final LocalSampleCalculator instance2 = MLEngineClassLoader.initInstance(FunctionName.LOCAL_SAMPLE_CALCULATOR, input, Input.class);
        ActionListener<Output> actionListener2 = ActionListener.wrap(o -> {
            LocalSampleCalculatorOutput output = (LocalSampleCalculatorOutput) o;
            assertEquals(d1 + d2, output.getResult(), 1e-6);
            assertNull(instance2.getClient());
            assertNull(instance2.getSettings());
        }, e -> { fail("Test failed: " + e.getMessage()); });
        instance2.execute(input, actionListener2);
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

    @Test(expected = IllegalArgumentException.class)
    public void testInitInstance_ClassNotFound() {
        // Test for case where class is not found in the maps
        MLEngineClassLoader.initInstance("SOMETHING ELSE", null, Object.class);
    }

    @Test
    public void initInstanceMetricsCorrelationReturnsNull() {
        MetricsCorrelation instance = MLEngineClassLoader.initInstance(FunctionName.METRICS_CORRELATION, null, MLAlgoParams.class);
        assertNull(instance);
    }

}
