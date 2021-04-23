package org.opensearch.ml.engine.utils;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
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

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class TribuoUtilTest {
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
        Tuple featureNamesValues = TribuoUtil.transformDataFrame(dataFrame);
        Assert.assertArrayEquals(new String[]{"f1", "f2"}, (String[])featureNamesValues.v1());
        Assert.assertEquals(3, ((double[][])featureNamesValues.v2()).length);
        for (int i=0; i<rawData.length; ++i) {
            Assert.assertArrayEquals(new double[]{0.1+i, 0.2+i}, ((double[][]) featureNamesValues.v2())[i], 0.01);
        }
    }

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

    private void constructDataFrame() {
        ColumnMeta[] columnMetas = new ColumnMeta[]{new ColumnMeta("f1", ColumnType.DOUBLE), new ColumnMeta("f2", ColumnType.DOUBLE)};
        dataFrame = DataFrameBuilder.emptyDataFrame(columnMetas);
        Arrays.stream(rawData).forEach(e -> dataFrame.appendRow(e));
    }
}