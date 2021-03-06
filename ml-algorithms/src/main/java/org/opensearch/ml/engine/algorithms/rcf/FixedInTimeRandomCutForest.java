/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.rcf;

import com.amazon.randomcutforest.config.ForestMode;
import com.amazon.randomcutforest.config.Precision;
import com.amazon.randomcutforest.parkservices.AnomalyDescriptor;
import com.amazon.randomcutforest.parkservices.ThresholdedRandomCutForest;
import com.amazon.randomcutforest.parkservices.state.ThresholdedRandomCutForestMapper;
import com.amazon.randomcutforest.parkservices.state.ThresholdedRandomCutForestState;
import lombok.extern.log4j.Log4j2;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.Model;
import org.opensearch.ml.common.dataframe.ColumnMeta;
import org.opensearch.ml.common.dataframe.ColumnType;
import org.opensearch.ml.common.dataframe.ColumnValue;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DataFrameBuilder;
import org.opensearch.ml.common.dataframe.Row;
import org.opensearch.ml.common.exception.MLValidationException;
import org.opensearch.ml.common.input.parameter.MLAlgoParams;
import org.opensearch.ml.common.input.parameter.rcf.FitRCFParams;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.MLPredictionOutput;
import org.opensearch.ml.engine.TrainAndPredictable;
import org.opensearch.ml.engine.annotation.Function;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;

/**
 * MLCommons doesn't support update trained model. So the trained RCF model in MLCommons
 * will be fixed in some time rather than updated by prediction data. We call it FIT(fixed
 * in time) RCF.
 */
@Log4j2
@Function(FunctionName.FIT_RCF)
public class FixedInTimeRandomCutForest implements TrainAndPredictable {
    private static final int DEFAULT_NUMBER_OF_TREES = 30;
    private static final int DEFAULT_SHINGLE_SIZE = 8;
    private static final int DEFAULT_OUTPUT_AFTER = 32;
    private static final int DEFAULT_SAMPLES_SIZE = 256; // how many nodes per tree
    private static final double DEFAULT_TIME_DECAY = 0.0001;
    private static final double DEFAULT_ANOMALY_RATE = 0.005;
    private static final String DEFAULT_TIME_ZONE = "UTC";

    private Integer numberOfTrees;
    private Integer shingleSize;
    private Integer sampleSize;
    private Integer outputAfter;
    private Double timeDecay;
    private Double anomalyRate;
    private String timeField;
    private String dateFormat;
    private String timeZone;

    private DateFormat simpleDateFormat;
    private static final ThresholdedRandomCutForestMapper trcfMapper = new ThresholdedRandomCutForestMapper();

    public FixedInTimeRandomCutForest(){}

    public FixedInTimeRandomCutForest(MLAlgoParams parameters) {
        if (parameters == null) {
            throw new MLValidationException("Parameters can't be null");
        }
        FitRCFParams rcfParams = (FitRCFParams) parameters;
        if (rcfParams.getTimeField() == null) {
            throw new MLValidationException("Time field can't be null");
        }
        this.numberOfTrees = Optional.ofNullable(rcfParams.getNumberOfTrees()).orElse(DEFAULT_NUMBER_OF_TREES);
        this.shingleSize = Optional.ofNullable(rcfParams.getShingleSize()).orElse(DEFAULT_SHINGLE_SIZE);
        this.sampleSize = Optional.ofNullable(rcfParams.getSampleSize()).orElse(DEFAULT_SAMPLES_SIZE);
        this.outputAfter = Optional.ofNullable(rcfParams.getOutputAfter()).orElse(DEFAULT_OUTPUT_AFTER);
        this.timeDecay = Optional.ofNullable(rcfParams.getTimeDecay()).orElse(DEFAULT_TIME_DECAY);
        this.anomalyRate = Optional.ofNullable(rcfParams.getAnomalyRate()).orElse(DEFAULT_ANOMALY_RATE);
        this.timeField = rcfParams.getTimeField();

        this.dateFormat = rcfParams.getDateFormat();
        this.timeZone = Optional.ofNullable(rcfParams.getTimeZone()).orElse(DEFAULT_TIME_ZONE);
        if (dateFormat != null) {
            simpleDateFormat = new SimpleDateFormat(dateFormat);
            simpleDateFormat.setTimeZone(TimeZone.getTimeZone(timeZone));
        }
    }

    @Override
    public MLOutput predict(DataFrame dataFrame, Model model) {
        if (model == null) {
            throw new IllegalArgumentException("No model found for FIT RCF prediction.");
        }
        ThresholdedRandomCutForestState state = RCFModelSerDeSer.deserializeTRCF(model.getContent());
        ThresholdedRandomCutForest forest = trcfMapper.toModel(state);
        List<Map<String, Object>> predictResult = process(dataFrame, forest);
        return MLPredictionOutput.builder().predictionResult(DataFrameBuilder.load(predictResult)).build();
    }

    @Override
    public Model train(DataFrame dataFrame) {
        ThresholdedRandomCutForest forest = createThresholdedRandomCutForest(dataFrame);
        process(dataFrame, forest);
        Model model = new Model();
        model.setName(FunctionName.FIT_RCF.name());
        model.setVersion(1);
        ThresholdedRandomCutForestState state = trcfMapper.toState(forest);
        model.setContent(RCFModelSerDeSer.serializeTRCF(state));
        return model;
    }

    @Override
    public MLOutput trainAndPredict(DataFrame dataFrame) {
        ThresholdedRandomCutForest forest = createThresholdedRandomCutForest(dataFrame);
        List<Map<String, Object>> predictResult = process(dataFrame, forest);
        return MLPredictionOutput.builder().predictionResult(DataFrameBuilder.load(predictResult)).build();
    }

    private List<Map<String, Object>> process(DataFrame dataFrame, ThresholdedRandomCutForest forest) {
        List<Double> pointList = new ArrayList<>();
        ColumnMeta[] columnMetas = dataFrame.columnMetas();
        List<Map<String, Object>> predictResult = new ArrayList<>();
        for (int rowNum = 0; rowNum< dataFrame.size(); rowNum++) {
            Row row = dataFrame.getRow(rowNum);
            long timestamp = -1;
            for (int i = 0; i < columnMetas.length; i++) {
                ColumnMeta columnMeta = columnMetas[i];
                ColumnValue value = row.getValue(i);

                // TODO: sort dataframe by time field with asc order. Currently consider the date already sorted by time.
                if (timeField != null && timeField.equals(columnMeta.getName())) {
                    ColumnType columnType = columnMeta.getColumnType();
                    if (columnType == ColumnType.LONG ) {
                        timestamp = value.longValue();
                    } else if (columnType == ColumnType.STRING) {
                        try {
                            timestamp = simpleDateFormat.parse(value.stringValue()).getTime();
                        } catch (ParseException e) {
                            log.error("Failed to parse timestamp " + value.stringValue(), e);
                            throw new MLValidationException("Failed to parse timestamp " + value.stringValue());
                        }
                    } else  {
                        throw new MLValidationException("Wrong data type of time field. Should use LONG or STRING, but got " + columnType);
                    }
                } else {
                    pointList.add(value.doubleValue());
                }
            }
            double[] point = pointList.stream().mapToDouble(d -> d).toArray();
            pointList.clear();
            Map<String, Object> result = new HashMap<>();

            AnomalyDescriptor process = forest.process(point, timestamp);
            result.put(timeField, timestamp);
            result.put("score", process.getRCFScore());
            result.put("anomaly_grade", process.getAnomalyGrade());
            predictResult.add(result);
        }
        return predictResult;
    }

    private ThresholdedRandomCutForest createThresholdedRandomCutForest(DataFrame dataFrame) {
        //TODO: add memory estimation of RCF. Will be better if support memory estimation in RCF
        ThresholdedRandomCutForest forest = ThresholdedRandomCutForest.builder()
                .dimensions(shingleSize * (dataFrame.columnMetas().length - 1))
                .sampleSize(sampleSize)
                .numberOfTrees(numberOfTrees)
                .timeDecay(timeDecay)
                .outputAfter(outputAfter)
                .initialAcceptFraction(outputAfter * 1.0d / sampleSize)
                .parallelExecutionEnabled(false)
                .compact(true)
                .precision(Precision.FLOAT_32)
                .boundingBoxCacheFraction(1)
                .shingleSize(shingleSize)
                .internalShinglingEnabled(true)
                .anomalyRate(anomalyRate)
                .forestMode(ForestMode.STANDARD) //TODO: support different ForestMode
                .build();
        return forest;
    }

}
