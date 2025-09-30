/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.sample;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.input.execute.samplecalculator.LocalSampleCalculatorInput;
import org.opensearch.ml.common.output.Output;
import org.opensearch.ml.common.output.execute.samplecalculator.LocalSampleCalculatorOutput;
import org.opensearch.transport.client.Client;

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
        ActionListener<Output> actionListener1 = ActionListener.wrap(o -> {
            LocalSampleCalculatorOutput output = (LocalSampleCalculatorOutput) o;
            Assert.assertEquals(6.0, output.getResult().doubleValue(), 1e-5);
        }, e -> { fail("Test failed: " + e.getMessage()); });
        calculator.execute(input, actionListener1, null);

        ActionListener<Output> actionListener2 = ActionListener.wrap(o -> {
            LocalSampleCalculatorOutput output = (LocalSampleCalculatorOutput) o;
            Assert.assertEquals(3.0, output.getResult().doubleValue(), 1e-5);
        }, e -> { fail("Test failed: " + e.getMessage()); });
        LocalSampleCalculatorInput input2 = new LocalSampleCalculatorInput("max", Arrays.asList(1.0, 2.0, 3.0));
        calculator.execute(input2, actionListener2, null);

        ActionListener<Output> actionListener3 = ActionListener.wrap(o -> {
            LocalSampleCalculatorOutput output = (LocalSampleCalculatorOutput) o;
            Assert.assertEquals(1.0, output.getResult().doubleValue(), 1e-5);
        }, e -> { fail("Test failed: " + e.getMessage()); });
        LocalSampleCalculatorInput input3 = new LocalSampleCalculatorInput("min", Arrays.asList(1.0, 2.0, 3.0));
        calculator.execute(input3, actionListener3, null);
    }

    @Test
    public void executeWithWrongOperation() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("can't support this operation");
        input = new LocalSampleCalculatorInput("wrong_operation", Arrays.asList(1.0, 2.0, 3.0));
        ActionListener<Output> actionListener = ActionListener.wrap(o -> {}, e -> { fail("Test failed: " + e.getMessage()); });
        calculator.execute(input, actionListener, null);
    }

    @Test
    public void executeWithNullInput() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("wrong input");
        calculator.execute(null, mock(ActionListener.class), null);
    }
}
