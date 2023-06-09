/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.clustering;

import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DataFrameBuilder;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.parameter.clustering.RCFSummarizeParams;
import org.opensearch.common.collect.Tuple;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.input.parameter.MLAlgoParams;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.MLPredictionOutput;
import org.opensearch.ml.engine.TrainAndPredictable;
import org.opensearch.ml.engine.annotation.Function;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.utils.MathUtil;
import org.opensearch.ml.engine.utils.ModelSerDeSer;
import org.opensearch.ml.engine.utils.TribuoUtil;
import com.amazon.randomcutforest.returntypes.SampleSummary;
import com.amazon.randomcutforest.summarization.Summarizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.BiFunction;

@Function(FunctionName.RCF_SUMMARIZE)
public class RCFSummarize implements TrainAndPredictable {
    public static final String VERSION = "1.0.0";
    private static final RCFSummarizeParams.DistanceType DEFAULT_DISTANCE_TYPE = RCFSummarizeParams.DistanceType.L2;
    private static int DEFAULT_MAX_K = 10;
    private static boolean DEFAULT_PHASE1_REASSIGN = true;
    private static boolean DEFAULT_PARALLEL = false;
    private final Random rnd = new Random();

    // Parameters
    private RCFSummarizeParams parameters;
    private BiFunction<float[], float[], Double> distance;
    private SampleSummary summary;

    public RCFSummarize() {}

    public RCFSummarize(MLAlgoParams parameters) {
        this.parameters = parameters == null ? RCFSummarizeParams.builder().maxK(DEFAULT_MAX_K).initialK(DEFAULT_MAX_K).phase1Reassign(DEFAULT_PHASE1_REASSIGN).parallel(DEFAULT_PARALLEL).build() : (RCFSummarizeParams) parameters;
        validateParametersAndRefine();
        createDistance();
    }

    private void validateParametersAndRefine() {
        Boolean phase1Reassign = parameters.getPhase1Reassign();
        Boolean parallel = parameters.getParallel();
        Integer maxK = parameters.getMaxK();
        Integer initialK = parameters.getInitialK();
        RCFSummarizeParams.DistanceType distType = parameters.getDistanceType();

        if (maxK != null && maxK <= 0) {
            throw new IllegalArgumentException("max K should be positive");
        }

        if (initialK != null && initialK <= 0) {
            throw new IllegalArgumentException("initial K should be positive");
        }

        if (maxK == null) {
            maxK = DEFAULT_MAX_K;
        }

        if (initialK == null) {
            initialK = maxK;
        }

        if (distType == null) {
            distType = DEFAULT_DISTANCE_TYPE;
        }

        if (phase1Reassign == null) {
            phase1Reassign = false;
        }

        if (parallel == null) {
            parallel = false;
        }

        parameters = RCFSummarizeParams.builder().maxK(maxK).initialK(initialK).phase1Reassign(phase1Reassign).parallel(parallel).distanceType(distType).build();
    }

    private void createDistance() {
        RCFSummarizeParams.DistanceType distanceType = Optional.ofNullable(parameters.getDistanceType()).orElse(DEFAULT_DISTANCE_TYPE);
        switch (distanceType) {
            case L1:
                distance = Summarizer::L1distance;
                break;
            case L2:
                distance = Summarizer::L2distance;
                break;
            case LInfinity:
                distance = Summarizer::LInfinitydistance;
                break;
            default:
                distance = Summarizer::L2distance;
                break;
        }
    }

    @Override
    public MLModel train(MLInput mlInput) {
        DataFrame dataFrame = ((DataFrameInputDataset)mlInput.getInputDataset()).getDataFrame();
        Tuple<String[], float[][]> featureNamesValues = TribuoUtil.transformDataFrameFloat(dataFrame);
        SampleSummary summary = Summarizer.summarize(featureNamesValues.v2(),
                parameters.getMaxK(),
                parameters.getInitialK(),
                parameters.getPhase1Reassign(),
                distance,
                rnd.nextLong(),
                parameters.getParallel());

        MLModel model = MLModel.builder()
                .name(FunctionName.RCF_SUMMARIZE.name())
                .algorithm(FunctionName.RCF_SUMMARIZE)
                .version(VERSION)
                .content(ModelSerDeSer.serializeToBase64(new SerializableSummary(summary)))
                .modelState(MLModelState.TRAINED)
                .build();
        return model;
    }

    @Override
    public void initModel(MLModel model, Map<String, Object> params, Encryptor encryptor) {
        this.summary = ((SerializableSummary)ModelSerDeSer.deserialize(model)).getSummary();
    }

    @Override
    public void close() {
        this.summary = null;
    }

    @Override
    public MLOutput predict(MLInput mlInput) {
        Iterable<float[]> centroidsLst = Arrays.asList(summary.summaryPoints);
        DataFrame dataFrame = ((DataFrameInputDataset)mlInput.getInputDataset()).getDataFrame();
        Tuple<String[], float[][]> featureNamesValues = TribuoUtil.transformDataFrameFloat(dataFrame);
        List<Integer> predictions = new ArrayList<>();
        Arrays.stream(featureNamesValues.v2()).forEach(e->predictions.add(MathUtil.findNearest(e, centroidsLst, distance)));

        List<Map<String, Object>> listClusterID = new ArrayList<>();
        predictions.forEach(e -> listClusterID.add(Collections.singletonMap("ClusterID", e)));

        return MLPredictionOutput.builder().predictionResult(DataFrameBuilder.load(listClusterID)).build();
    }

    @Override
    public MLOutput predict(MLInput mlInput, MLModel model) {
        if (model == null) {
            throw new IllegalArgumentException("No model found for RCFSummarize prediction.");
        }

        summary = ((SerializableSummary)ModelSerDeSer.deserialize(model)).getSummary();
        return predict(mlInput);
    }

    @Override
    public MLOutput trainAndPredict(MLInput mlInput) {
        DataFrame dataFrame = ((DataFrameInputDataset)mlInput.getInputDataset()).getDataFrame();
        Tuple<String[], float[][]> featureNamesValues = TribuoUtil.transformDataFrameFloat(dataFrame);
        SampleSummary summary = Summarizer.summarize(featureNamesValues.v2(),
                parameters.getMaxK(),
                parameters.getInitialK(),
                parameters.getPhase1Reassign(),
                distance,
                rnd.nextLong(),
                parameters.getParallel());

        Iterable<float[]> centroidsLst = Arrays.asList(summary.summaryPoints);
        List<Integer> predictions = new ArrayList<>();
        Arrays.stream(featureNamesValues.v2()).forEach(e->predictions.add(MathUtil.findNearest(e, centroidsLst, distance)));

        List<Map<String, Object>> listClusterID = new ArrayList<>();
        predictions.forEach(e -> listClusterID.add(Collections.singletonMap("ClusterID", e)));

        return MLPredictionOutput.builder().predictionResult(DataFrameBuilder.load(listClusterID)).build();
    }
}

