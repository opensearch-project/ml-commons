/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine;

import static org.junit.Assert.assertNotNull;
import static org.opensearch.ml.engine.helper.MLTestHelper.concstructDataFrameInputDataSet;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.parameter.clustering.KMeansParams;
import org.opensearch.ml.common.input.parameter.regression.LinearRegressionParams;
import org.opensearch.ml.engine.algorithms.clustering.KMeans;
import org.opensearch.ml.engine.algorithms.regression.LinearRegression;
import org.opensearch.ml.engine.utils.ModelSerDeSer;
import org.tribuo.clustering.kmeans.KMeansModel;
import org.tribuo.regression.sgd.linear.LinearSGDModel;

public class ModelSerDeSerTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testModelSerDeSerKMeans() {
        KMeansParams params = KMeansParams.builder().build();
        KMeans kMeans = new KMeans(params);
        MLModel model = kMeans
            .train(MLInput.builder().algorithm(FunctionName.KMEANS).inputDataset(concstructDataFrameInputDataSet(100)).build());

        KMeansModel deserializedModel = (KMeansModel) ModelSerDeSer.deserialize(model);
        assertNotNull(deserializedModel);
    }

    @Test
    public void testModelSerDeSerLinearRegression() {
        LinearRegressionParams params = LinearRegressionParams.builder().target("f2").build();
        LinearRegression linearRegression = new LinearRegression(params);
        MLModel model = linearRegression
            .train(MLInput.builder().algorithm(FunctionName.LINEAR_REGRESSION).inputDataset(concstructDataFrameInputDataSet(100)).build());

        LinearSGDModel deserializedModel = (LinearSGDModel) ModelSerDeSer.deserialize(model);
        assertNotNull(deserializedModel);
    }

}
