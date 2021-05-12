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

package org.opensearch.ml.engine.regression;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.parameter.MLParameter;
import org.opensearch.ml.common.parameter.MLParameterBuilder;
import org.opensearch.ml.engine.Model;

import java.util.ArrayList;
import java.util.List;

import static org.opensearch.ml.engine.helper.LinearRegressionHelper.constructLinearRegressionTrainDataFrame;
import static org.opensearch.ml.engine.regression.LinearRegression.BETA1;
import static org.opensearch.ml.engine.regression.LinearRegression.BETA2;
import static org.opensearch.ml.engine.regression.LinearRegression.EPSILON;
import static org.opensearch.ml.engine.regression.LinearRegression.LEARNING_RATE;
import static org.opensearch.ml.engine.regression.LinearRegression.OBJECTIVE;
import static org.opensearch.ml.engine.regression.LinearRegression.OPTIMISER;
import static org.opensearch.ml.engine.regression.LinearRegression.TARGET;


public class LinearRegressionTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private List<MLParameter> parameters = new ArrayList<>();
    private DataFrame trainDataFrame;

    @Before
    public void setUp() {
        parameters.add(MLParameterBuilder.parameter(OBJECTIVE, 0));
        parameters.add(MLParameterBuilder.parameter(OPTIMISER, 5));
        parameters.add(MLParameterBuilder.parameter(LEARNING_RATE, 0.01));
        parameters.add(MLParameterBuilder.parameter(EPSILON, 1e-6));
        parameters.add(MLParameterBuilder.parameter(BETA1, 0.9));
        parameters.add(MLParameterBuilder.parameter(BETA2, 0.99));
        trainDataFrame = constructLinearRegressionTrainDataFrame();
    }

    @Test
    public void train() {
        parameters.add(MLParameterBuilder.parameter(TARGET, "price"));
        LinearRegression regression = new LinearRegression(parameters);
        Model model = regression.train(trainDataFrame);
        Assert.assertEquals("LinearRegression", model.getName());
        Assert.assertEquals(1, model.getVersion());
        Assert.assertNotNull(model.getContent());
    }

    @Test
    public void trainExceptionWithoutTarget() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("Empty target when generating dataset from data frame.");
        LinearRegression regression = new LinearRegression(parameters);
        Model model = regression.train(trainDataFrame);
    }

    @Test
    public void trainExceptionUnmatchedTarget() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("No matched target when generating dataset from data frame.");
        parameters.add(MLParameterBuilder.parameter("target", "not found"));
        LinearRegression regression = new LinearRegression(parameters);
        Model model = regression.train(trainDataFrame);
    }

}