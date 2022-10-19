/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.ad;

import com.oracle.labs.mlrg.olcut.util.Pair;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataframe.ColumnMeta;
import org.opensearch.ml.common.dataframe.ColumnType;
import org.opensearch.ml.common.dataframe.ColumnValue;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DefaultDataFrame;
import org.opensearch.ml.common.dataframe.DoubleValue;
import org.opensearch.ml.common.dataframe.Row;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.input.parameter.ad.AnomalyDetectionLibSVMParams;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.output.MLPredictionOutput;
import org.tribuo.Dataset;
import org.tribuo.Example;
import org.tribuo.Feature;
import org.tribuo.anomaly.Event;
import org.tribuo.anomaly.example.AnomalyDataGenerator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class AnomalyDetectionLibSVMTest {

    private AnomalyDetectionLibSVMParams parameters;
    private AnomalyDetectionLibSVM anomalyDetection;
    private DataFrame trainDataFrame;
    private DataFrameInputDataset trainDataFrameInputDataset;
    private DataFrame predictionDataFrame;
    private DataFrameInputDataset predictionDataFrameInputDataset;
    private List<Event.EventType> predictionLabels;
    private double gamma = 1.0;
    private double nu = 0.1;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() {
        parameters = AnomalyDetectionLibSVMParams.builder().gamma(gamma).nu(nu).build();
        anomalyDetection = new AnomalyDetectionLibSVM(parameters);

        Pair<Dataset<Event>, Dataset<Event>> pair = AnomalyDataGenerator.gaussianAnomaly(1000, 0.3);
        Dataset<Event> data = pair.getA();
        Dataset<Event> test = pair.getB();
        trainDataFrame = constructDataFrame(data, true, null);
        trainDataFrameInputDataset = new DataFrameInputDataset(trainDataFrame);
        predictionLabels = new ArrayList<>();
        predictionDataFrame = constructDataFrame(test, false, predictionLabels);
        predictionDataFrameInputDataset = new DataFrameInputDataset(predictionDataFrame);
    }

    private DataFrame constructDataFrame(Dataset<Event> data, boolean training, List<Event.EventType> labels) {
        Iterator<Example<Event>> iterator = data.iterator();
        List<ColumnMeta> columns = null;
        DataFrame dataFrame = null;
        while (iterator.hasNext()) {
            Example<Event> example = iterator.next();
            if (columns == null) {
                columns = new ArrayList<>();
                List<ColumnValue> columnValues = new ArrayList<>();
                for (Feature feature : example) {
                    columns.add(new ColumnMeta(feature.getName(), ColumnType.DOUBLE));
                    columnValues.add(new DoubleValue(feature.getValue()));
                }
                ColumnMeta[] columnMetas = columns.toArray(new ColumnMeta[columns.size()]);
                dataFrame = new DefaultDataFrame(columnMetas);
                addRow(columnValues, training, example, dataFrame, labels);
            } else {
                List<ColumnValue> columnValues = new ArrayList<>();
                for (Feature feature : example) {
                    columnValues.add(new DoubleValue(feature.getValue()));
                }
                addRow(columnValues, training, example, dataFrame, labels);
            }
        }
        return dataFrame;
    }

    private void addRow(List<ColumnValue> columnValues, boolean training, Example<Event> example, DataFrame dataFrame, List<Event.EventType> labels) {
        Row row = new Row(columnValues.toArray(new ColumnValue[columnValues.size()]));
        if (training) {
            if (example.getOutput().getType() == Event.EventType.EXPECTED) {
                dataFrame.appendRow(row);
            }
        } else if (example.getOutput().getType() != Event.EventType.UNKNOWN) {
            dataFrame.appendRow(row);
        }
        if (labels != null) {
            labels.add(example.getOutput().getType());
        }
    }

    @Test
    public void train() {
        MLModel model = anomalyDetection.train(trainDataFrameInputDataset);
        Assert.assertEquals(FunctionName.AD_LIBSVM.name(), model.getName());
        Assert.assertEquals(AnomalyDetectionLibSVM.VERSION, model.getVersion());
        Assert.assertNotNull(model.getContent());
    }

    @Test
    public void trainWithFullParams() {
        AnomalyDetectionLibSVMParams parameters = AnomalyDetectionLibSVMParams.builder().gamma(gamma).nu(nu).cost(1.0).coeff(0.01).epsilon(0.001).degree(1).kernelType(AnomalyDetectionLibSVMParams.ADKernelType.LINEAR).build();
        AnomalyDetectionLibSVM anomalyDetection = new AnomalyDetectionLibSVM(parameters);
        MLModel model = anomalyDetection.train(trainDataFrameInputDataset);
        Assert.assertEquals(FunctionName.AD_LIBSVM.name(), model.getName());
        Assert.assertEquals(AnomalyDetectionLibSVM.VERSION, model.getVersion());
        Assert.assertNotNull(model.getContent());

        parameters = parameters.toBuilder().kernelType(AnomalyDetectionLibSVMParams.ADKernelType.POLY).build();
        anomalyDetection = new AnomalyDetectionLibSVM(parameters);
        model = anomalyDetection.train(trainDataFrameInputDataset);
        Assert.assertEquals(FunctionName.AD_LIBSVM.name(), model.getName());

        parameters = parameters.toBuilder().kernelType(AnomalyDetectionLibSVMParams.ADKernelType.RBF).build();
        anomalyDetection = new AnomalyDetectionLibSVM(parameters);
        model = anomalyDetection.train(trainDataFrameInputDataset);
        Assert.assertEquals(FunctionName.AD_LIBSVM.name(), model.getName());

        parameters = parameters.toBuilder().kernelType(AnomalyDetectionLibSVMParams.ADKernelType.SIGMOID).build();
        anomalyDetection = new AnomalyDetectionLibSVM(parameters);
        model = anomalyDetection.train(trainDataFrameInputDataset);
        Assert.assertEquals(FunctionName.AD_LIBSVM.name(), model.getName());
    }

    @Test
    public void predict_NullModel() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("No model found for KMeans prediction");
        anomalyDetection.predict(predictionDataFrameInputDataset, null);
    }

    @Test
    public void predict() {
        MLModel model = anomalyDetection.train(trainDataFrameInputDataset);
        MLPredictionOutput output = (MLPredictionOutput) anomalyDetection.predict(predictionDataFrameInputDataset, model);
        DataFrame predictions = output.getPredictionResult();
        int i = 0;
        int truePositive = 0;
        int falsePositive = 0;
        int totalPositive = 0;
        for (Row row : predictions) {
            String type = row.getValue(1).stringValue();
            if (predictionLabels.get(i) == Event.EventType.ANOMALOUS) {
                totalPositive++;
                if ("ANOMALOUS".equals(type)) {
                    truePositive++;
                }
            } else if ("ANOMALOUS".equals(type)) {
                falsePositive++;
            }
            i++;
        }
        float precision = (float) truePositive / (truePositive + falsePositive);
        float recall = (float) truePositive / totalPositive;
        Assert.assertEquals(0.7, precision, 0.01);
        Assert.assertEquals(1.0, recall, 0.01);
    }

    @Test
    public void constructor_NegativeGamma() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("gamma should be positive");
        AnomalyDetectionLibSVMParams parameters = AnomalyDetectionLibSVMParams.builder().gamma(-1.0).build();
        new AnomalyDetectionLibSVM(parameters);
    }

    @Test
    public void constructor_NegativeNu() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("nu should be positive");
        AnomalyDetectionLibSVMParams parameters = AnomalyDetectionLibSVMParams.builder().nu(-1.0).build();
        new AnomalyDetectionLibSVM(parameters);
    }
}
