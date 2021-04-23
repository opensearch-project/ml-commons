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

package org.opensearch.ml.engine.utils;

import lombok.experimental.UtilityClass;
import org.opensearch.common.collect.Tuple;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.Row;
import org.opensearch.ml.engine.contants.TribuoOutputType;
import org.tribuo.Example;
import org.tribuo.MutableDataset;
import org.tribuo.Output;
import org.tribuo.OutputFactory;
import org.tribuo.clustering.ClusterID;
import org.tribuo.datasource.ListDataSource;
import org.tribuo.impl.ArrayExample;
import org.tribuo.provenance.SimpleDataSourceProvenance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.StreamSupport;

@UtilityClass
public class TribuoUtil {
    public static Tuple transformDataFrame(DataFrame dataFrame) {
        String[] featureNames = Arrays.stream(dataFrame.columnMetas()).map(e -> e.getName()).toArray(String[]::new);
        double[][] featureValues = new double[dataFrame.size()][];
        Iterator<Row> itr = dataFrame.iterator();
        int i = 0;
        while (itr.hasNext()) {
            Row row = itr.next();
            featureValues[i] = StreamSupport.stream(row.spliterator(), false).mapToDouble(e -> e.doubleValue()).toArray();
            ++i;
        }

        return new Tuple(featureNames, featureValues);
    }

    public static <T extends Output<T>> MutableDataset<T> generateDataset(DataFrame dataFrame, OutputFactory<T> outputFactory, String desc, TribuoOutputType outputType) {
        List<Example<T>> dataset = new ArrayList<>();
        Tuple<String[], double[][]> featureNamesValues = transformDataFrame(dataFrame);
        ArrayExample<T> example;
        for (int i=0; i<dataFrame.size(); ++i) {
            switch (outputType) {
                case CLUSTERID:
                    example = new ArrayExample<T>((T) new ClusterID(ClusterID.UNASSIGNED), featureNamesValues.v1(), featureNamesValues.v2()[i]);
                    break;
                default:
                    throw new IllegalArgumentException("unknown type:" + outputType);
            }
            dataset.add(example);
        }
        SimpleDataSourceProvenance provenance = new SimpleDataSourceProvenance(desc, outputFactory);
        return new MutableDataset<>(new ListDataSource<>(dataset, outputFactory, provenance));
    }

}
