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
import org.opensearch.ml.common.parameter.MLParameter;
import org.opensearch.ml.engine.MLAlgo;
import org.opensearch.ml.engine.Model;
import org.opensearch.ml.engine.utils.ModelSerDeSer;
import org.opensearch.ml.engine.contants.TribuoOutputType;
import org.opensearch.ml.engine.utils.TribuoUtil;
import org.tribuo.MutableDataset;
import org.tribuo.clustering.ClusterID;
import org.tribuo.clustering.ClusteringFactory;
import org.tribuo.clustering.kmeans.KMeansModel;
import org.tribuo.clustering.kmeans.KMeansTrainer;

import java.io.IOException;
import java.util.List;

public class KMeans implements MLAlgo {
    private int k = 5;
    private int iterations = 10;
    private KMeansTrainer.Distance distanceType = KMeansTrainer.Distance.EUCLIDEAN;
    private int numThreads = Runtime.getRuntime().availableProcessors() + 1; //Assume cpu-bound.
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
                seed = (int) mlParameter.getValue();
            }
        });
    }

    @Override
    public DataFrame predict(DataFrame dataFrame, String model) {
        //TODO
        throw new RuntimeException("Unsupported predict");
    }

    @Override
    public Model train(DataFrame dataFrame) throws IOException {
        MutableDataset<ClusterID> trainDataset = TribuoUtil.generateDataset(dataFrame, new ClusteringFactory(), "KMeans training data from opensearch", TribuoOutputType.CLUSTERID);
        KMeansTrainer trainer = new KMeansTrainer(k, iterations, distanceType, numThreads, seed);
        KMeansModel kMeansModel = trainer.train(trainDataset);
        Model model = new Model();
        model.setName("KMeans");
        model.setVersion(1);
        model.setContent(ModelSerDeSer.serialize(kMeansModel));

        return model;
    }
}
