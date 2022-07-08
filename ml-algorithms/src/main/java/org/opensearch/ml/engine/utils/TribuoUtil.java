/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.utils;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.opensearch.common.collect.Tuple;
import org.opensearch.ml.common.dataframe.ColumnMeta;
import org.opensearch.ml.common.dataframe.ColumnValue;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.Row;
import org.opensearch.ml.engine.contants.TribuoOutputType;
import org.tribuo.Example;
import org.tribuo.MutableDataset;
import org.tribuo.Output;
import org.tribuo.OutputFactory;
import org.tribuo.anomaly.Event;
import org.tribuo.classification.Label;
import org.tribuo.clustering.ClusterID;
import org.tribuo.datasource.ListDataSource;
import org.tribuo.impl.ArrayExample;
import org.tribuo.provenance.SimpleDataSourceProvenance;
import org.tribuo.regression.Regressor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

@UtilityClass
public class TribuoUtil {
    public static Tuple<String[], double[][]> transformDataFrame(DataFrame dataFrame) {
        String[] featureNames = Arrays.stream(dataFrame.columnMetas()).map(ColumnMeta::getName).toArray(String[]::new);
        double[][] featureValues = new double[dataFrame.size()][];
        Iterator<Row> itr = dataFrame.iterator();
        int i = 0;
        while (itr.hasNext()) {
            Row row = itr.next();
            featureValues[i] = StreamSupport.stream(row.spliterator(), false).mapToDouble(ColumnValue::doubleValue).toArray();
            ++i;
        }

        return new Tuple<>(featureNames, featureValues);
    }

    public static Tuple<String[], float[][]> transformDataFrameFloat(DataFrame dataFrame) {
        String[] featureNames = Arrays.stream(dataFrame.columnMetas()).map(ColumnMeta::getName).toArray(String[]::new);
        float[][] featureValues = new float[dataFrame.size()][];
        Iterator<Row> itr = dataFrame.iterator();
        int i = 0;
        while (itr.hasNext()) {
            Row row = itr.next();
            float[] v = new float[row.size()];
            for (int ii = 0; ii < row.size(); ii++) {
                v[ii] = (float) row.getValue(ii).doubleValue();
            }

            featureValues[i] = v;
            ++i;
        }

        return new Tuple<>(featureNames, featureValues);
    }

    public static Tuple<String[], double[][]> transformClassificationDataFrame(DataFrame dataFrame, String target) {
        List<String> featureNames = Arrays.stream(dataFrame.columnMetas()).map(ColumnMeta::getName).collect(Collectors.toList());
        int targetIndex = dataFrame.getColumnIndex(target);

        int i = 0;
        Iterator<Row> itr = dataFrame.iterator();
        double[][] featureValues = new double[dataFrame.size()][featureNames.size() - 1];
        while (itr.hasNext()) {
            Row row = itr.next();
            int col = 0;
            for (int j = 0; j < featureNames.size(); j++) {
                if (j == targetIndex) {
                    continue;
                }
                featureValues[i][col++] = row.getValue(j).doubleValue();
            }
            ++i;
        }
        featureNames.remove(target);
        return new Tuple<>(featureNames.toArray(new String[featureNames.size()]), featureValues);
    }

    public static String[] transformTargetValuesDataFrames(DataFrame dataFrame, String target) {
        int targetIndex = dataFrame.getColumnIndex(target);

        int i = 0;
        Iterator<Row> itr = dataFrame.iterator();
        String[] targetValues = new String[dataFrame.size()];
        while (itr.hasNext()) {
            Row row = itr.next();
            targetValues[i] = row.getValue(targetIndex).stringValue();
            ++i;
        }

        return targetValues;
    }

    /**
     * Generate tribuo dataset from data frame.
     * @param dataFrame features data
     * @param outputFactory the tribuo output factory
     * @param desc description for tribuo provenance
     * @param outputType the tribuo output type
     * @return tribuo dataset
     */
    public static <T extends Output<T>> MutableDataset<T> generateDataset(DataFrame dataFrame, OutputFactory<T> outputFactory, String desc, TribuoOutputType outputType) {
        List<Example<T>> dataset = new ArrayList<>();
        Tuple<String[], double[][]> featureNamesValues = transformDataFrame(dataFrame);
        ArrayExample<T> example;
        for (int i=0; i<dataFrame.size(); ++i) {
            switch (outputType) {
                case CLUSTERID:
                    example = new ArrayExample<>((T) new ClusterID(ClusterID.UNASSIGNED), featureNamesValues.v1(), featureNamesValues.v2()[i]);
                    break;
                case REGRESSOR:
                    //Create single dimension tribuo regressor with name DIM-0 and value double NaN.
                    example = new ArrayExample<>((T) new Regressor("DIM-0", Double.NaN), featureNamesValues.v1(), featureNamesValues.v2()[i]);
                    break;
                case ANOMALY_DETECTION_LIBSVM:
                    // Why we set default event type as EXPECTED(non-anomalous)
                    // 1. For training data, Tribuo LibSVMAnomalyTrainer only supports EXPECTED events at training time.
                    // 2. For prediction data, we treat the data as non-anomalous by default as Tribuo lib don't accept UNKNOWN type.
                    Event.EventType defaultEventType = Event.EventType.EXPECTED;
                    // TODO: support anomaly labels to evaluate prediction result
                    example = new ArrayExample<>((T) new Event(defaultEventType), featureNamesValues.v1(), featureNamesValues.v2()[i]);
                    break;
                case LABEL:
                    example = new ArrayExample<>((T) new Label(""), featureNamesValues.v1(), featureNamesValues.v2()[i]);
                    break;
                default:
                    throw new IllegalArgumentException("unknown type:" + outputType);
            }
            dataset.add(example);
        }
        SimpleDataSourceProvenance provenance = new SimpleDataSourceProvenance(desc, outputFactory);
        return new MutableDataset<>(new ListDataSource<>(dataset, outputFactory, provenance));
    }

    /**
     * Generate tribuo dataset from data frame with target.
     * @param dataFrame features data
     * @param outputFactory the tribuo output factory
     * @param desc description for tribuo provenance
     * @param outputType the tribuo output type
     * @param target target name
     * @return tribuo dataset
     */
    public static <T extends Output<T>> MutableDataset<T> generateDatasetWithTarget(DataFrame dataFrame, OutputFactory<T> outputFactory, String desc, TribuoOutputType outputType, String target) {
        if (StringUtils.isEmpty(target)) {
            throw new IllegalArgumentException("Empty target when generating dataset from data frame.");
        }

        List<Example<T>> dataset = new ArrayList<>();
        ArrayExample<T> example;

        for (int i=0; i<dataFrame.size(); ++i) {
            final int finalI = i;
            double[] featureValues;
            Tuple<String[], double[][]> featureNamesValues;
            String[] featureNames;
            int finalTargetIndex;
            switch (outputType) {
                case REGRESSOR:
                    featureNamesValues = transformDataFrame(dataFrame);
                    finalTargetIndex = findFinalTargetIndex(featureNamesValues, target);
                    featureNames = createFeatureNames(featureNamesValues, finalTargetIndex);
                    double targetValue = featureNamesValues.v2()[finalI][finalTargetIndex];
                    featureValues = IntStream.range(0, featureNamesValues.v2()[i].length).
                            filter(e -> e != finalTargetIndex).
                            mapToDouble(e -> featureNamesValues.v2()[finalI][e]).
                            toArray();
                    example = new ArrayExample<>((T) new Regressor(target, targetValue), featureNames, featureValues);
                    break;
                case LABEL:
                    featureNamesValues = transformClassificationDataFrame(dataFrame, target);
                    featureNames = createFeatureNames(featureNamesValues);
                    String[] targetValues = transformTargetValuesDataFrames(dataFrame, target);
                    featureValues = IntStream.range(0, featureNamesValues.v2()[i].length).
                            mapToDouble(e -> featureNamesValues.v2()[finalI][e]).
                            toArray();
                    example = new ArrayExample<>((T) new Label(targetValues[i]), featureNames, featureValues);
                    break;
                default:
                    throw new IllegalArgumentException("unknown type:" + outputType);
            }
            dataset.add(example);
        }
        SimpleDataSourceProvenance provenance = new SimpleDataSourceProvenance(desc, outputFactory);
        return new MutableDataset<>(new ListDataSource<>(dataset, outputFactory, provenance));
    }

    private int findFinalTargetIndex(Tuple<String[], double[][]> featureNamesValues, String target) {
        int targetIndex = -1;
        for (int i = 0; i < featureNamesValues.v1().length; ++i) {
            if (featureNamesValues.v1()[i].equals(target)) {
                targetIndex = i;
                break;
            }
        }
        if (targetIndex == -1) {
            throw new IllegalArgumentException("No matched target when generating dataset from data frame.");
        }

        return targetIndex;
    }

    private String[] createFeatureNames(Tuple<String[], double[][]> featureNamesValues, int finalTargetIndex) {
        String[] featureNames = IntStream.range(0, featureNamesValues.v1().length).
                filter(e -> e != finalTargetIndex).
                mapToObj(e -> featureNamesValues.v1()[e]).
                toArray(String[]::new);

        return featureNames;
    }

    private String[] createFeatureNames(Tuple<String[], double[][]> featureNamesValues) {
        String[] featureNames = IntStream.range(0, featureNamesValues.v1().length).
                mapToObj(e -> featureNamesValues.v1()[e]).
                toArray(String[]::new);

        return featureNames;
    }
}
