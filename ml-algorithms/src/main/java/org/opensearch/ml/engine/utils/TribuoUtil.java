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
import org.apache.commons.lang3.StringUtils;
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
import org.tribuo.regression.Regressor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.IntStream;
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

    public static <T extends Output<T>> MutableDataset<T> generateDataset(DataFrame dataFrame, OutputFactory<T> outputFactory, String desc, TribuoOutputType outputType, String target) {
        List<Example<T>> dataset = new ArrayList<>();
        Tuple<String[], double[][]> featureNamesValues = transformDataFrame(dataFrame);

        int targetIndex = -1;
        if (StringUtils.isNoneEmpty(target)) {
            for (int i = 0; i < featureNamesValues.v1().length; ++i) {
                if (featureNamesValues.v1()[i].equals(target)) {
                    targetIndex = i;
                    break;
                }
            }
        }

        ArrayExample<T> example;
        final int finalTargetIndex = targetIndex;
        String[] featureNames = new String[0];
        if (outputType.equals(TribuoOutputType.REGRESSOR)) {
            if (finalTargetIndex == -1) {
                throw new RuntimeException("Unknown target when generating dataset from data frame for regression.");
            }
            featureNames = IntStream.range(0, featureNamesValues.v1().length).
                    filter(e -> e != finalTargetIndex).
                    mapToObj(e -> featureNamesValues.v1()[e]).
                    toArray(String[]::new);
        }
        for (int i=0; i<dataFrame.size(); ++i) {
            switch (outputType) {
                case CLUSTERID:
                    example = new ArrayExample<T>((T) new ClusterID(ClusterID.UNASSIGNED), featureNamesValues.v1(), featureNamesValues.v2()[i]);
                    break;
                case REGRESSOR:
                    final int finalI = i;
                    double targetValue = featureNamesValues.v2()[finalI][finalTargetIndex];
                    double[] featureValues = IntStream.range(0, featureNamesValues.v2()[i].length).
                            filter(e -> e != finalTargetIndex).
                            mapToDouble(e -> featureNamesValues.v2()[finalI][e]).
                            toArray();
                    example = new ArrayExample<T>((T) new Regressor(target, targetValue), featureNames, featureValues);
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
