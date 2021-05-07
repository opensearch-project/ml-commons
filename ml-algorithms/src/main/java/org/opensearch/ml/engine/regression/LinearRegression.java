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

package org.opensearch.ml.engine.regression;

import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.parameter.MLParameter;
import org.opensearch.ml.engine.MLAlgo;
import org.opensearch.ml.engine.Model;
import org.opensearch.ml.engine.contants.TribuoOutputType;
import org.opensearch.ml.engine.exceptions.ModelSerDeSerException;
import org.opensearch.ml.engine.utils.ModelSerDeSer;
import org.opensearch.ml.engine.utils.TribuoUtil;
import org.tribuo.MutableDataset;
import org.tribuo.math.StochasticGradientOptimiser;
import org.tribuo.math.optimisers.AdaDelta;
import org.tribuo.math.optimisers.AdaGrad;
import org.tribuo.math.optimisers.Adam;
import org.tribuo.math.optimisers.RMSProp;
import org.tribuo.math.optimisers.SGD;
import org.tribuo.regression.RegressionFactory;
import org.tribuo.regression.Regressor;
import org.tribuo.regression.sgd.RegressionObjective;
import org.tribuo.regression.sgd.linear.LinearSGDTrainer;
import org.tribuo.regression.sgd.objectives.AbsoluteLoss;
import org.tribuo.regression.sgd.objectives.Huber;
import org.tribuo.regression.sgd.objectives.SquaredLoss;

import java.util.List;

public class LinearRegression implements MLAlgo {
    private String target;
    private RegressionObjective objective = new SquaredLoss();
    private int optimizerType = 0;
    private double learningRate = 0.01;
    //Momentum
    private SGD.Momentum momentumType = SGD.Momentum.NONE;
    private double momentumFactor = 0.0;
    //AdaGrad, AdaDelta, AdaGradRDA, Adam, RMSProp
    private double epsilon = 1e-6;
    //Adam
    private double beta1 = 0.9;
    private double beta2 = 0.99;
    //RMSProp
    private double decayRate = 0.9;

    private int epochs = 10;
    private int interval = -1;
    private int miniBatchSize = 1;
    private long seed = System.currentTimeMillis();
    private StochasticGradientOptimiser optimiser = SGD.getSimpleSGD(learningRate, momentumFactor, momentumType);


    LinearRegression(List<MLParameter> parameters) {
        parameters.forEach(mlParameter ->
        {
            if (mlParameter.getName().equalsIgnoreCase("objective")) {
                int type = (int) mlParameter.getValue();
                switch (type) {
                    case 0:
                        //Use default l2 loss function.
                        break;
                    case 1:
                        //Use l1 loss function.
                        objective = new AbsoluteLoss();
                        break;
                    case 2:
                        //Use a mix of l1 and l2 loss function.
                        objective = new Huber();
                        break;
                    default:
                        //Use default l2 loss function.
                        break;
                }
            } else if (mlParameter.getName().equalsIgnoreCase("optimiser")) {
                optimizerType = (int) mlParameter.getValue();
            } else if (mlParameter.getName().equalsIgnoreCase("learning_rate")) {
                learningRate = (double) mlParameter.getValue();
            } else if (mlParameter.getName().equalsIgnoreCase("momentum_type")) {
                int type = (int) mlParameter.getValue();
                switch (type) {
                    case 0:
                        momentumType = SGD.Momentum.STANDARD;
                        break;
                    case 1:
                        momentumType = SGD.Momentum.NESTEROV;
                        break;
                    default:
                        break;
                }
            } else if (mlParameter.getName().equalsIgnoreCase("momentum_factor")) {
                momentumFactor = (double) mlParameter.getValue();
            } else if (mlParameter.getName().equalsIgnoreCase("epsilon")) {
                epsilon = (double) mlParameter.getValue();
            } else if (mlParameter.getName().equalsIgnoreCase("beta1")) {
                beta1 = (double) mlParameter.getValue();
            } else if (mlParameter.getName().equalsIgnoreCase("beta2")) {
                beta2 = (double) mlParameter.getValue();
            } else if (mlParameter.getName().equalsIgnoreCase("decay_rate")) {
                decayRate = (double) mlParameter.getValue();
            } else if (mlParameter.getName().equalsIgnoreCase("epochs")) {
                epochs = (int) mlParameter.getValue();
            } else if (mlParameter.getName().equalsIgnoreCase("mini_batch_size")) {
                miniBatchSize = (int) mlParameter.getValue();
            } else if (mlParameter.getName().equalsIgnoreCase("seed")) {
                seed = (long) mlParameter.getValue();
            } else if (mlParameter.getName().equalsIgnoreCase("target")) {
                target = (String) mlParameter.getValue();
            }
        });
        switch (optimizerType) {
            case 0:
                //Use default SGD with a constant learning rate.
                break;
            case 1:
                optimiser = SGD.getLinearDecaySGD(learningRate, momentumFactor, momentumType);
                break;
            case 2:
                optimiser = SGD.getSqrtDecaySGD(learningRate, momentumFactor, momentumType);
                break;
            case 3:
                optimiser = new AdaGrad(learningRate, epsilon);
                break;
            case 4:
                optimiser = new AdaDelta(momentumFactor, epsilon);
                break;
            case 5:
                optimiser = new Adam(learningRate, beta1, beta2, epsilon);
                break;
            case 6:
                optimiser = new RMSProp(learningRate, momentumFactor, epsilon, decayRate);
                break;
            default:
                //Use default SGD with a constant learning rate.
                break;
        }

        validateParameters();
    }

    private void validateParameters() {
        if (learningRate < 0) {
            throw new IllegalArgumentException("Learning rate should not be negative.");
        }

        if (momentumFactor < 0) {
            throw new IllegalArgumentException("MomentumFactor should not be negative.");
        }

        if (epsilon < 0) {
            throw new IllegalArgumentException("Epsilon should not be negative.");
        }

        if (beta1 <= 0 || beta1 >= 1) {
            throw new IllegalArgumentException("Beta1 should be in an open interval (0,1).");
        }

        if (beta2 <= 0 || beta2 >= 1) {
            throw new IllegalArgumentException("Beta2 should be in an open interval (0,1).");
        }

        if (decayRate < 0) {
            throw new IllegalArgumentException("DecayRate should not be negative.");
        }

        if (epochs < 0) {
            throw new IllegalArgumentException("Epochs should not be negative.");
        }

        if (miniBatchSize < 0) {
            throw new IllegalArgumentException("MiniBatchSize should not be negative.");
        }
    }

    @Override
    public DataFrame predict(DataFrame dataFrame, Model model) {
        //TODO
        throw new RuntimeException("Unsupported predict.");
    }

    @Override
    public Model train(DataFrame dataFrame) {
        MutableDataset<Regressor> trainDataset = TribuoUtil.generateDatasetWithTarget(dataFrame, new RegressionFactory(),
                "Linear regression training data from opensearch", TribuoOutputType.REGRESSOR, target);
        LinearSGDTrainer linearSGDTrainer = new LinearSGDTrainer(objective, optimiser, epochs, interval, miniBatchSize, seed);
        org.tribuo.Model<Regressor> regressionModel = linearSGDTrainer.train(trainDataset);
        Model model = new Model();
        model.setName("LinearRegression");
        model.setVersion(1);
        model.setContent(ModelSerDeSer.serialize(regressionModel));

        return model;
    }
}
