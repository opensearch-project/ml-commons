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

package org.opensearch.ml.engine.algorithms.ad;

import com.oracle.labs.mlrg.olcut.util.Pair;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.ml.common.dataframe.ColumnMeta;
import org.opensearch.ml.common.dataframe.ColumnType;
import org.opensearch.ml.common.dataframe.ColumnValue;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DefaultDataFrame;
import org.opensearch.ml.common.dataframe.DoubleValue;
import org.opensearch.ml.common.dataframe.Row;
import org.opensearch.ml.common.parameter.AnomalyDetectionParams;
import org.opensearch.ml.common.parameter.FunctionName;
import org.opensearch.ml.common.parameter.MLPredictionOutput;
import org.opensearch.ml.engine.Model;
import org.tribuo.Dataset;
import org.tribuo.Example;
import org.tribuo.Feature;
import org.tribuo.Prediction;
import org.tribuo.anomaly.Event;
import org.tribuo.anomaly.example.AnomalyDataGenerator;
import org.tribuo.anomaly.libsvm.LibSVMAnomalyModel;
import org.tribuo.anomaly.libsvm.LibSVMAnomalyTrainer;
import org.tribuo.anomaly.libsvm.SVMAnomalyType;
import org.tribuo.common.libsvm.KernelType;
import org.tribuo.common.libsvm.LibSVMModel;
import org.tribuo.common.libsvm.SVMParameters;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AnomalyDetectionTest {

    private AnomalyDetectionParams parameters;
    private AnomalyDetection anomalyDetection;
    private DataFrame trainDataFrame;
    private DataFrame predictionDataFrame;
    private List<Event.EventType> predictionLabels;
    private double gamma = 1.0;
    private double nu = 0.1;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() {
        parameters = AnomalyDetectionParams.builder().gamma(gamma).nu(nu).build();
        anomalyDetection = new AnomalyDetection(parameters);

        Pair<Dataset<Event>, Dataset<Event>> pair = AnomalyDataGenerator.gaussianAnomaly(1000, 0.3);
        Dataset<Event> data = pair.getA();
        Dataset<Event> test = pair.getB();
        trainDataFrame = constructDataFrame(data, true, null);
        predictionLabels = new ArrayList<>();
        predictionDataFrame = constructDataFrame(test, false, predictionLabels);
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
        Model model = anomalyDetection.train(trainDataFrame);
        Assert.assertEquals(FunctionName.ANOMALY_DETECTION.name(), model.getName());
        Assert.assertEquals(AnomalyDetection.VERSION, model.getVersion());
        Assert.assertNotNull(model.getContent());
    }

    @Test
    public void predict_NullModel() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("No model found for KMeans prediction");
        anomalyDetection.predict(predictionDataFrame, null);
    }

    @Test
    public void predict() {
        Model model = anomalyDetection.train(trainDataFrame);
        MLPredictionOutput output = (MLPredictionOutput) anomalyDetection.predict(predictionDataFrame, model);
        DataFrame predictions = output.getPredictionResult();
        int i = 0;
        int truePositive = 0;
        int falsePositive = 0;
        int totalPositive = 0;
        for (Row row : predictions) {
            String type = row.getValue(0).stringValue();
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

}
