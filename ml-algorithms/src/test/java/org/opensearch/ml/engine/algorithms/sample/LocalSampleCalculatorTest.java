/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.sample;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.ml.common.parameter.LocalSampleCalculatorInput;
import org.opensearch.ml.common.parameter.LocalSampleCalculatorOutput;

import java.util.Arrays;

public class LocalSampleCalculatorTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();
    @Mock
    Client client;
    @Mock
    Settings settings;
    private LocalSampleCalculator calculator;
    private LocalSampleCalculatorInput input;

    @Before
    public void setUp() {
        calculator = new LocalSampleCalculator(client, settings);
        input = new LocalSampleCalculatorInput("sum", Arrays.asList(1.0, 2.0, 3.0));
    }

    @Test
    public void execute() {
        LocalSampleCalculatorOutput output = (LocalSampleCalculatorOutput) calculator.execute(input);
        Assert.assertEquals(6.0, output.getResult().doubleValue(), 1e-5);

        input = new LocalSampleCalculatorInput("max", Arrays.asList(1.0, 2.0, 3.0));
        output = (LocalSampleCalculatorOutput) calculator.execute(input);
        Assert.assertEquals(3.0, output.getResult().doubleValue(), 1e-5);

        input = new LocalSampleCalculatorInput("min", Arrays.asList(1.0, 2.0, 3.0));
        output = (LocalSampleCalculatorOutput) calculator.execute(input);
        Assert.assertEquals(1.0, output.getResult().doubleValue(), 1e-5);
    }

    @Test
    public void executeWithWrongOperation() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("can't support this operation");
        input = new LocalSampleCalculatorInput("wrong_operation", Arrays.asList(1.0, 2.0, 3.0));
        calculator.execute(input);
    }

    @Test
    public void executeWithNullInput() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("wrong input");
        calculator.execute(null);
    }
}
