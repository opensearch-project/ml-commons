/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.clustering;

import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DataFrameBuilder;
import org.opensearch.ml.common.input.parameter.clustering.KMeansParams;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.input.parameter.MLAlgoParams;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.MLPredictionOutput;
import org.opensearch.ml.common.Model;
import org.opensearch.ml.engine.TrainAndPredictable;
import org.opensearch.ml.engine.annotation.Function;
import org.opensearch.ml.engine.utils.ModelSerDeSer;
import org.opensearch.ml.engine.contants.TribuoOutputType;
import org.opensearch.ml.engine.utils.TribuoUtil;
import org.tribuo.MutableDataset;
import org.tribuo.Prediction;
import org.tribuo.clustering.ClusterID;
import org.tribuo.clustering.ClusteringFactory;
import org.tribuo.clustering.kmeans.KMeansModel;
import org.tribuo.clustering.kmeans.KMeansTrainer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tribuo Kmean can only run with Java8. We see such error when run with Java11
 * access denied ("java.lang.RuntimePermission" "modifyThreadGroup")
 * Check more details in these Github issues
 * https://github.com/opensearch-project/ml-commons/issues/67
 * https://github.com/oracle/tribuo/issues/158
 */
@Function(FunctionName.KMEANS)
public class KMeans implements TrainAndPredictable {
    private static final KMeansParams.DistanceType DEFAULT_DISTANCE_TYPE = KMeansParams.DistanceType.EUCLIDEAN;
    private static int DEFAULT_CENTROIDS = 2;
    private static int DEFAULT_ITERATIONS = 10;

    //The number of threads.
    private KMeansParams parameters;

    private int numThreads = Math.max(Runtime.getRuntime().availableProcessors() / 2, 1); //Assume cpu-bound.
    //The random seed.
    private long seed = System.currentTimeMillis();
    private KMeansTrainer.Distance distance;

    public KMeans() {}

    public KMeans(MLAlgoParams parameters) {
        this.parameters = parameters == null ? KMeansParams.builder().build() : (KMeansParams)parameters;
        validateParameters();
        createDistance();
    }

    private void validateParameters() {

        if (parameters.getCentroids() != null && parameters.getCentroids() <= 0) {
            throw new IllegalArgumentException("K should be positive.");
        }

        if (parameters.getIterations() != null && parameters.getIterations() <= 0) {
            throw new IllegalArgumentException("Iterations should be positive.");
        }

    }

    private void createDistance() {
        KMeansParams.DistanceType distanceType = Optional.ofNullable(parameters.getDistanceType()).orElse(DEFAULT_DISTANCE_TYPE);
        switch (distanceType) {
            case COSINE:
                distance = KMeansTrainer.Distance.COSINE;
                break;
            case L1:
                distance = KMeansTrainer.Distance.L1;
                break;
            default:
                distance = KMeansTrainer.Distance.EUCLIDEAN;
                break;
        }
    }

    @Override
    public MLOutput predict(DataFrame dataFrame, Model model) {
        if (model == null) {
            throw new IllegalArgumentException("No model found for KMeans prediction.");
        }

        List<Prediction<ClusterID>> predictions;
        MutableDataset<ClusterID> predictionDataset = TribuoUtil.generateDataset(dataFrame, new ClusteringFactory(),
                "KMeans prediction data from opensearch", TribuoOutputType.CLUSTERID);
        KMeansModel kMeansModel = (KMeansModel) ModelSerDeSer.deserialize(model.getContent());
        predictions = kMeansModel.predict(predictionDataset);

        List<Map<String, Object>> listClusterID = new ArrayList<>();
        predictions.forEach(e -> listClusterID.add(Collections.singletonMap("ClusterID", e.getOutput().getID())));

        return MLPredictionOutput.builder().predictionResult(DataFrameBuilder.load(listClusterID)).build();
    }

    @Override
    public Model train(DataFrame dataFrame) {
        MutableDataset<ClusterID> trainDataset = TribuoUtil.generateDataset(dataFrame, new ClusteringFactory(),
                "KMeans training data from opensearch", TribuoOutputType.CLUSTERID);
        Integer centroids = Optional.ofNullable(parameters.getCentroids()).orElse(DEFAULT_CENTROIDS);
        Integer iterations = Optional.ofNullable(parameters.getIterations()).orElse(DEFAULT_ITERATIONS);
        KMeansTrainer trainer = new KMeansTrainer(centroids, iterations, distance, numThreads, seed);
        KMeansModel kMeansModel = trainer.train(trainDataset);
        Model model = new Model();
        model.setName(FunctionName.KMEANS.name());
        model.setVersion(1);
        model.setContent(ModelSerDeSer.serialize(kMeansModel));

        return model;
    }

    @Override
    public MLOutput trainAndPredict(DataFrame dataFrame) {
        MutableDataset<ClusterID> trainDataset = TribuoUtil.generateDataset(dataFrame, new ClusteringFactory(),
                "KMeans training and predicting data from opensearch", TribuoOutputType.CLUSTERID);
        Integer centroids = Optional.ofNullable(parameters.getCentroids()).orElse(DEFAULT_CENTROIDS);
        Integer iterations = Optional.ofNullable(parameters.getIterations()).orElse(DEFAULT_ITERATIONS);
        KMeansTrainer trainer = new KMeansTrainer(centroids, iterations, distance, numThreads, seed);
        KMeansModel kMeansModel = trainer.train(trainDataset); // won't store model in index

        List<Prediction<ClusterID>> predictions = kMeansModel.predict(trainDataset);
        List<Map<String, Object>> listClusterID = new ArrayList<>();
        predictions.forEach(e -> listClusterID.add(Collections.singletonMap("ClusterID", e.getOutput().getID())));

        return MLPredictionOutput.builder().predictionResult(DataFrameBuilder.load(listClusterID)).build();
    }
}
