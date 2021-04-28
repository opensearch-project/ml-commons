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

package org.opensearch.ml.engine.clustering;

import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DataFrameBuilder;
import org.opensearch.ml.common.parameter.MLParameter;
import org.opensearch.ml.engine.MLAlgo;
import org.opensearch.ml.engine.Model;
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

public class KMeans implements MLAlgo {
    //The number of clusters.
    private int k = 2;
    //The number of iterations.
    private int iterations = 10;
    //The distance function.
    private KMeansTrainer.Distance distanceType = KMeansTrainer.Distance.EUCLIDEAN;
    //The number of threads.
    private int numThreads = Runtime.getRuntime().availableProcessors() + 1; //Assume cpu-bound.
    //The random seed.
    private long seed = System.currentTimeMillis();

    KMeans(List<MLParameter> parameters) {
        parameters.forEach(mlParameter ->
        {
            if (mlParameter.getName().equalsIgnoreCase("k")) {
                k = (int) mlParameter.getValue();
            } else if (mlParameter.getName().equalsIgnoreCase("iterations")) {
                iterations = (int) mlParameter.getValue();
            } else if (mlParameter.getName().equalsIgnoreCase("distance_type")) {
                int type = (int) mlParameter.getValue();
                switch (type) {
                    case 0:
                        distanceType = KMeansTrainer.Distance.EUCLIDEAN;
                        break;
                    case 1:
                        distanceType = KMeansTrainer.Distance.COSINE;
                        break;
                    case 2:
                        distanceType = KMeansTrainer.Distance.L1;
                        break;
                    default:
                        distanceType = KMeansTrainer.Distance.EUCLIDEAN;
                        break;
                }
            } else if (mlParameter.getName().equalsIgnoreCase("num_threads")) {
                numThreads = (int) mlParameter.getValue();
            } else if (mlParameter.getName().equalsIgnoreCase("seed")) {
                seed = (long) mlParameter.getValue();
            }
        });
    }

    @Override
    public DataFrame predict(DataFrame dataFrame, Model model) {
        if (model == null) {
            throw new RuntimeException("No model found for KMeans prediction.");
        }

        List<Prediction<ClusterID>> predictions;
        MutableDataset<ClusterID> predictionDataset = TribuoUtil.generateDataset(dataFrame, new ClusteringFactory(),
                "KMeans prediction data from opensearch", TribuoOutputType.CLUSTERID, null);
        KMeansModel kMeansModel = null;
        try {
            kMeansModel = (KMeansModel) ModelSerDeSer.deserialize(model.getContent());
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize model.", e.getCause());
        }

        predictions = kMeansModel.predict(predictionDataset);

        List<Map<String, Object>> listClusterID = new ArrayList<>();
        predictions.forEach(e -> listClusterID.add(Collections.singletonMap("Cluster ID", e.getOutput().getID())));

        return DataFrameBuilder.load(listClusterID);
    }

    @Override
    public Model train(DataFrame dataFrame) {
        MutableDataset<ClusterID> trainDataset = TribuoUtil.generateDataset(dataFrame, new ClusteringFactory(),
                "KMeans training data from opensearch", TribuoOutputType.CLUSTERID, null);
        KMeansTrainer trainer = new KMeansTrainer(k, iterations, distanceType, numThreads, seed);
        KMeansModel kMeansModel = trainer.train(trainDataset);
        Model model = new Model();
        model.setName("KMeans");
        model.setVersion(1);
        try {
            model.setContent(ModelSerDeSer.serialize(kMeansModel));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize model.", e.getCause());
        }

        return model;
    }
}
