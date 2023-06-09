/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.regression;

import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DataFrameBuilder;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.parameter.regression.LinearRegressionParams;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.input.parameter.MLAlgoParams;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.MLPredictionOutput;
import org.opensearch.ml.engine.Predictable;
import org.opensearch.ml.engine.Trainable;
import org.opensearch.ml.engine.annotation.Function;
import org.opensearch.ml.engine.contants.TribuoOutputType;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.utils.ModelSerDeSer;
import org.opensearch.ml.engine.utils.TribuoUtil;
import org.tribuo.MutableDataset;
import org.tribuo.Prediction;
import org.tribuo.Trainer;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.opensearch.ml.engine.utils.ModelSerDeSer.serializeToBase64;

@Function(FunctionName.LINEAR_REGRESSION)
public class LinearRegression implements Trainable, Predictable {
    public static final String VERSION = "1.0.0";
    private static final LinearRegressionParams.ObjectiveType DEFAULT_OBJECTIVE_TYPE = LinearRegressionParams.ObjectiveType.SQUARED_LOSS;
    private static final LinearRegressionParams.OptimizerType DEFAULT_OPTIMIZER_TYPE = LinearRegressionParams.OptimizerType.SIMPLE_SGD;
    private static final double DEFAULT_LEARNING_RATE = 0.01;
    //Momentum
    private static final double DEFAULT_MOMENTUM_FACTOR = 0;
    private static final LinearRegressionParams.MomentumType DEFAULT_MOMENTUM_TYPE = LinearRegressionParams.MomentumType.STANDARD;
    //AdaGrad, AdaDelta, AdaGradRDA, Adam, RMSProp
    private static final double DEFAULT_EPSILON = 1e-6;
    //Adam
    private static final double DEFAULT_BETA1 = 0.9;
    private static final double DEFAULT_BETA2 = 0.99;
    //RMSProp
    private static final double DEFAULT_DECAY_RATE = 0.9;

    private static final int DEFAULT_EPOCHS = 1000;
    private static final int DEFAULT_INTERVAL = -1;
    private static final int DEFAULT_BATCH_SIZE = 1;
    private static final Long DEFAULT_SEED = Trainer.DEFAULT_SEED;

    private LinearRegressionParams parameters;
    private StochasticGradientOptimiser optimiser;
    private RegressionObjective objective;

    private int loggingInterval;
    private int minibatchSize;
    private long seed;
    private org.tribuo.Model<Regressor> regressionModel;

    public LinearRegression() {}

    /**
     * Initialize a linear regression algorithm.
     * @param parameters the parameters for linear regression algorithm
     */
    public LinearRegression(MLAlgoParams parameters) {
        this.parameters = parameters == null ? LinearRegressionParams.builder().build() : (LinearRegressionParams)parameters;
        validateParameters();
        createObjective();
        createOptimiser();
    }

    private void createObjective() {
        LinearRegressionParams.ObjectiveType objectiveType = Optional.ofNullable(parameters.getObjectiveType()).orElse(DEFAULT_OBJECTIVE_TYPE);
        switch (objectiveType) {
            case ABSOLUTE_LOSS:
                //Use l1 loss function.
                objective = new AbsoluteLoss();
                break;
            case HUBER:
                //Use a mix of l1 and l2 loss function.
                objective = new Huber();
                break;
            default:
                //Use default l2 loss function.
                objective = new SquaredLoss();
                break;
        }
    }


    private void createOptimiser() {
        LinearRegressionParams.OptimizerType optimizerType = Optional.ofNullable(parameters.getOptimizerType()).orElse(DEFAULT_OPTIMIZER_TYPE);
        Double learningRate = Optional.ofNullable(parameters.getLearningRate()).orElse(DEFAULT_LEARNING_RATE);
        Double momentumFactor = Optional.ofNullable(parameters.getMomentumFactor()).orElse(DEFAULT_MOMENTUM_FACTOR);
        Double epsilon = Optional.ofNullable(parameters.getEpsilon()).orElse(DEFAULT_EPSILON);
        Double beta1 = Optional.ofNullable(parameters.getBeta1()).orElse(DEFAULT_BETA1);
        Double beta2 = Optional.ofNullable(parameters.getBeta2()).orElse(DEFAULT_BETA2);
        LinearRegressionParams.MomentumType momentumType = Optional.ofNullable(parameters.getMomentumType()).orElse(DEFAULT_MOMENTUM_TYPE);
        Double decayRate = Optional.ofNullable(parameters.getDecayRate()).orElse(DEFAULT_DECAY_RATE);

        SGD.Momentum momentum;
        switch (momentumType) {
            case NESTEROV:
                momentum = SGD.Momentum.NESTEROV;
                break;
            default:
                momentum = SGD.Momentum.STANDARD;
                break;
        }
        switch (optimizerType) {
            case LINEAR_DECAY_SGD:
                optimiser = SGD.getLinearDecaySGD(learningRate, momentumFactor, momentum);
                break;
            case SQRT_DECAY_SGD:
                optimiser = SGD.getSqrtDecaySGD(learningRate, momentumFactor, momentum);
                break;
            case ADA_GRAD:
                optimiser = new AdaGrad(learningRate, epsilon);
                break;
            case ADA_DELTA:
                optimiser = new AdaDelta(momentumFactor, epsilon);
                break;
            case ADAM:
                optimiser = new Adam(learningRate, beta1, beta2, epsilon);
                break;
            case RMS_PROP:
                optimiser = new RMSProp(learningRate, momentumFactor, epsilon, decayRate);
                break;
            default:
                //Use default SGD with a constant learning rate.
                optimiser = SGD.getSimpleSGD(learningRate, momentumFactor, momentum);
                break;
        }
    }

    private void validateParameters() {
        if (parameters.getLearningRate() != null && parameters.getLearningRate() < 0) {
            throw new IllegalArgumentException("Learning rate should not be negative.");
        }

        if (parameters.getMomentumFactor() != null && parameters.getMomentumFactor() < 0) {
            throw new IllegalArgumentException("MomentumFactor should not be negative.");
        }

        if (parameters.getEpsilon() != null && parameters.getEpsilon() < 0) {
            throw new IllegalArgumentException("Epsilon should not be negative.");
        }

        if (parameters.getBeta1() != null && (parameters.getBeta1() <= 0 || parameters.getBeta1() >= 1)) {
            throw new IllegalArgumentException("Beta1 should be in an open interval (0,1).");
        }

        if (parameters.getBeta2() != null && (parameters.getBeta2() <= 0 || parameters.getBeta2() >= 1)) {
            throw new IllegalArgumentException("Beta2 should be in an open interval (0,1).");
        }

        if (parameters.getDecayRate() != null && parameters.getDecayRate() < 0) {
            throw new IllegalArgumentException("DecayRate should not be negative.");
        }

        if (parameters.getEpochs() != null && parameters.getEpochs() < 0) {
            throw new IllegalArgumentException("Epochs should not be negative.");
        }

        if (parameters.getBatchSize() != null && parameters.getBatchSize() < 0) {
            throw new IllegalArgumentException("MiniBatchSize should not be negative.");
        }

        if (parameters.getLoggingInterval() != null && parameters.getLoggingInterval() < -1) {
            throw new IllegalArgumentException("Invalid Logging intervals");
        }

        loggingInterval = Optional.ofNullable(parameters.getLoggingInterval()).orElse(DEFAULT_INTERVAL);
        minibatchSize = Optional.ofNullable(parameters.getBatchSize()).orElse(DEFAULT_BATCH_SIZE);
        seed = Optional.ofNullable(parameters.getSeed()).orElse(DEFAULT_SEED);
    }


    @Override
    public void initModel(MLModel model, Map<String, Object> params, Encryptor encryptor) {
        this.regressionModel = (org.tribuo.Model<Regressor>) ModelSerDeSer.deserialize(model);
    }

    @Override
    public void close() {
        this.regressionModel = null;
    }

    @Override
    public MLOutput predict(MLInput mlInput) {
        if (regressionModel == null) {
            throw new IllegalArgumentException("model not deployed");
        }
        DataFrame dataFrame = ((DataFrameInputDataset)mlInput.getInputDataset()).getDataFrame();
        MutableDataset<Regressor> predictionDataset = TribuoUtil.generateDataset(dataFrame, new RegressionFactory(),
                "Linear regression prediction data from opensearch", TribuoOutputType.REGRESSOR);
        List<Prediction<Regressor>> predictions = regressionModel.predict(predictionDataset);
        List<Map<String, Object>> listPrediction = new ArrayList<>();
        predictions.forEach(e -> listPrediction.add(Collections.singletonMap(e.getOutput().getNames()[0], e.getOutput().getValues()[0])));

        return MLPredictionOutput.builder().predictionResult(DataFrameBuilder.load(listPrediction)).build();
    }

    @Override
    public MLOutput predict(MLInput mlInput, MLModel model) {
        if (model == null) {
            throw new IllegalArgumentException("No model found for linear regression prediction.");
        }

        regressionModel = (org.tribuo.Model<Regressor>) ModelSerDeSer.deserialize(model);
        return predict(mlInput);
    }

    @Override
    public MLModel train(MLInput mlInput) {
        DataFrame dataFrame = ((DataFrameInputDataset)mlInput.getInputDataset()).getDataFrame();
        MutableDataset<Regressor> trainDataset = TribuoUtil.generateDatasetWithTarget(dataFrame, new RegressionFactory(),
                "Linear regression training data from opensearch", TribuoOutputType.REGRESSOR, parameters.getTarget());
        Integer epochs = Optional.ofNullable(parameters.getEpochs()).orElse(DEFAULT_EPOCHS);
        LinearSGDTrainer linearSGDTrainer = new LinearSGDTrainer(objective, optimiser, epochs, loggingInterval, minibatchSize, seed);
        org.tribuo.Model<Regressor> regressionModel = linearSGDTrainer.train(trainDataset);
        MLModel model = MLModel.builder()
                .name(FunctionName.LINEAR_REGRESSION.name())
                .algorithm(FunctionName.LINEAR_REGRESSION)
                .version(VERSION)
                .content(serializeToBase64(regressionModel))
                .modelState(MLModelState.TRAINED)
                .build();

        return model;
    }
}
