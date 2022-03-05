/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.utils;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.common.collect.Tuple;
import org.opensearch.ml.common.dataframe.ColumnMeta;
import org.opensearch.ml.common.dataframe.ColumnType;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DataFrameBuilder;
import org.opensearch.ml.engine.contants.TribuoOutputType;
import org.tribuo.Example;
import org.tribuo.Feature;
import org.tribuo.MutableDataset;
import org.tribuo.clustering.ClusterID;
import org.tribuo.clustering.ClusteringFactory;
import org.tribuo.impl.ArrayExample;
import org.tribuo.regression.RegressionFactory;
import org.tribuo.regression.Regressor;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class TribuoUtilTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private DataFrame dataFrame;
    private Double[][] rawData = {
        {0.1, 0.2},
        {1.1, 1.2},
        {2.1, 2.2}
    };

    @Before
    public void setUp() {
        constructDataFrame();
    }

    @Test
    public void transformDataFrame() {
        Tuple<String[], double[][]> featureNamesValues = TribuoUtil.transformDataFrame(dataFrame);
        Assert.assertArrayEquals(new String[]{"f1", "f2"}, featureNamesValues.v1());
        Assert.assertEquals(3, (featureNamesValues.v2()).length);
        for (int i=0; i<rawData.length; ++i) {
            Assert.assertArrayEquals(new double[]{0.1+i, 0.2+i}, ((double[][]) featureNamesValues.v2())[i], 0.01);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void generateDataset() {
        MutableDataset<ClusterID> dataset = TribuoUtil.generateDataset(dataFrame, new ClusteringFactory(), "test", TribuoOutputType.CLUSTERID);
        List<Example<ClusterID>> examples = dataset.getData();
        Assert.assertEquals(rawData.length, examples.size());
        for (int i=0; i<rawData.length; ++i){
            ArrayExample arrayExample = (ArrayExample) examples.get(i);
            Iterator<Feature> iterator = arrayExample.iterator();
            int idx = 1;
            while (iterator.hasNext()) {
                Feature feature = iterator.next();
                Assert.assertEquals("f"+idx, feature.getName());
                Assert.assertEquals(i+idx/10.0, feature.getValue(), 0.01);
                ++idx;
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void generateDatasetWithTarget() {
        MutableDataset<Regressor> dataset = TribuoUtil.generateDatasetWithTarget(dataFrame, new RegressionFactory(), "test", TribuoOutputType.REGRESSOR, "f2");
        List<Example<Regressor>> examples = dataset.getData();
        Assert.assertEquals(rawData.length, examples.size());
        for (int i=0; i<rawData.length; ++i){
            ArrayExample arrayExample = (ArrayExample) examples.get(i);
            Iterator<Feature> iterator = arrayExample.iterator();
            int idx = 1;
            while (iterator.hasNext()) {
                Feature feature = iterator.next();
                Assert.assertEquals("f"+idx, feature.getName());
                Assert.assertEquals(i+idx/10.0, feature.getValue(), 0.01);
                ++idx;
            }
        }
    }

    @Test
    public void generateDatasetWithEmptyTarget() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("Empty target when generating dataset from data frame.");
        TribuoUtil.generateDatasetWithTarget(dataFrame, new RegressionFactory(), "test", TribuoOutputType.REGRESSOR, null);
    }

    @Test
    public void generateDatasetWithUnmatchedTarget() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("No matched target when generating dataset from data frame.");
        TribuoUtil.generateDatasetWithTarget(dataFrame, new RegressionFactory(), "test", TribuoOutputType.REGRESSOR, "f0");
    }

    private void constructDataFrame() {
        ColumnMeta[] columnMetas = new ColumnMeta[]{new ColumnMeta("f1", ColumnType.DOUBLE), new ColumnMeta("f2", ColumnType.DOUBLE)};
        dataFrame = DataFrameBuilder.emptyDataFrame(columnMetas);
        Arrays.stream(rawData).forEach(e -> dataFrame.appendRow(e));
    }
}