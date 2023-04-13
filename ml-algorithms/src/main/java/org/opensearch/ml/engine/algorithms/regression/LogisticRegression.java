/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.regression;

import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DataFrameBuilder;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.input.parameter.MLAlgoParams;
import org.opensearch.ml.common.input.parameter.regression.LogisticRegressionParams;
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
import org.tribuo.classification.Label;
import org.tribuo.classification.LabelFactory;
import org.tribuo.classification.sgd.LabelObjective;
import org.tribuo.classification.sgd.linear.LinearSGDTrainer;
import org.tribuo.classification.sgd.objectives.Hinge;
import org.tribuo.classification.sgd.objectives.LogMulticlass;
import org.tribuo.math.StochasticGradientOptimiser;
import org.tribuo.math.optimisers.AdaDelta;
import org.tribuo.math.optimisers.AdaGrad;
import org.tribuo.math.optimisers.Adam;
import org.tribuo.math.optimisers.RMSProp;
import org.tribuo.math.optimisers.SGD;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.opensearch.ml.engine.utils.ModelSerDeSer.serializeToBase64;

@Function(FunctionName.LOGISTIC_REGRESSION)
public class LogisticRegression implements Trainable, Predictable {
    public static final String VERSION = "1.0.0";
    private static final LogisticRegressionParams.ObjectiveType DEFAULT_OBJECTIVE_TYPE = LogisticRegressionParams.ObjectiveType.LOGMULTICLASS;
    private static final LogisticRegressionParams.OptimizerType DEFAULT_OPTIMIZER_TYPE = LogisticRegressionParams.OptimizerType.ADA_GRAD;
    private static final LogisticRegressionParams.MomentumType DEFAULT_MOMENTUM_TYPE = LogisticRegressionParams.MomentumType.STANDARD;
    private static final double DEFAULT_LEARNING_RATE = 1.0;

    //AdaGrad, AdaDelta, AdaGradRDA, Adam, RMSProp
    private static final double DEFAULT_EPSILON = 0.1;
    private static final int DEFAULT_EPOCHS = 5;
    private static final int DEFAULT_LOGGING_INTERVAL = 1000;
    private static final int DEFAULT_BATCH_SIZE = 1;
    private static final Long DEFAULT_SEED = Trainer.DEFAULT_SEED;
    private static final double DEFAULT_MOMENTUM_FACTOR = 0;
    private static final double DEFAULT_BETA1 = 0.9;
    private static final double DEFAULT_BETA2 = 0.99;
    //RMSProp
    private static final double DEFAULT_DECAY_RATE = 0.9;

    private int epochs;
    private int loggingInterval;
    private int minibatchSize;
    private long seed;

    private LogisticRegressionParams parameters;
    private StochasticGradientOptimiser optimiser;
    private LabelObjective objective;
    private org.tribuo.Model<Label> classificationModel;

    /**
     * Initialize a linear regression algorithm.
     * @param parameters the parameters for linear regression algorithm
     */
    public LogisticRegression(MLAlgoParams parameters) {
        this.parameters = parameters == null ? LogisticRegressionParams.builder().build() : (LogisticRegressionParams)parameters;
        validateParameters();
        createObjective();
        createOptimiser();
    }

    private void validateParameters() {
        if (parameters.getLearningRate() != null && parameters.getLearningRate() < 0) {
            throw new IllegalArgumentException("Learning rate should not be negative.");
        }

        if (parameters.getEpsilon() != null && parameters.getEpsilon() < 0) {
            throw new IllegalArgumentException("Epsilon should not be negative.");
        }

        if (parameters.getEpochs() != null && parameters.getEpochs() < 0) {
            throw new IllegalArgumentException("Epochs should not be negative.");
        }

        if (parameters.getBatchSize() != null && parameters.getBatchSize() < 0) {
            throw new IllegalArgumentException("MiniBatchSize should not be negative.");
        }

        // loggingInterval: Log the loss after this many iterations. If -1 don't log anything.
        if (parameters.getLoggingInterval() != null && parameters.getLoggingInterval() < -1) {
            throw new IllegalArgumentException("Invalid Logging intervals");
        }

        epochs = Optional.ofNullable(parameters.getEpochs()).orElse(DEFAULT_EPOCHS);
        loggingInterval = Optional.ofNullable(parameters.getLoggingInterval()).orElse(DEFAULT_LOGGING_INTERVAL);
        minibatchSize = Optional.ofNullable(parameters.getBatchSize()).orElse(DEFAULT_BATCH_SIZE);
        seed = Optional.ofNullable(parameters.getSeed()).orElse(DEFAULT_SEED);
    }

    private void createObjective() {
        LogisticRegressionParams.ObjectiveType objectiveType = Optional.ofNullable(parameters.getObjectiveType()).orElse(DEFAULT_OBJECTIVE_TYPE);
        switch (objectiveType) {
            case HINGE:
                objective = new Hinge();
                break;
            default:
                objective = new LogMulticlass();
                break;
        }
    }

    private void createOptimiser() {
        LogisticRegressionParams.OptimizerType optimizerType = Optional.ofNullable(parameters.getOptimizerType()).orElse(DEFAULT_OPTIMIZER_TYPE);
        Double learningRate = Optional.ofNullable(parameters.getLearningRate()).orElse(DEFAULT_LEARNING_RATE);
        Double epsilon = Optional.ofNullable(parameters.getEpsilon()).orElse(DEFAULT_EPSILON);
        Double momentumFactor = Optional.ofNullable(parameters.getMomentumFactor()).orElse(DEFAULT_MOMENTUM_FACTOR);
        LogisticRegressionParams.MomentumType momentumType = Optional.ofNullable(parameters.getMomentumType()).orElse(DEFAULT_MOMENTUM_TYPE);
        Double beta1 = Optional.ofNullable(parameters.getBeta1()).orElse(DEFAULT_BETA1);
        Double beta2 = Optional.ofNullable(parameters.getBeta2()).orElse(DEFAULT_BETA2);
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
            case ADA_DELTA:
                optimiser = new AdaDelta(momentumFactor, epsilon);
                break;
            case ADAM:
                optimiser = new Adam(learningRate, beta1, beta2, epsilon);
                break;
            case RMS_PROP:
                optimiser = new RMSProp(learningRate, momentumFactor, epsilon, decayRate);
                break;
            case SIMPLE_SGD:
                optimiser = SGD.getSimpleSGD(learningRate, momentumFactor, momentum);
                break;
            default:
                //Use default SGD with a constant learning rate.
                optimiser = new AdaGrad(learningRate, epsilon);
                break;
        }
    }

    @Override
    public MLModel train(MLInput mlInput) {
        DataFrame dataFrame = ((DataFrameInputDataset)mlInput.getInputDataset()).getDataFrame();
        MutableDataset<Label> trainDataset = TribuoUtil.generateDatasetWithTarget(dataFrame, new LabelFactory(),
                "Logistic regression training data from OpenSearch", TribuoOutputType.LABEL, parameters.getTarget());
        // LinearSGDTrainer(objective=LogMulticlass,optimiser=AdaGrad(initialLearningRate=1.0,epsilon=0.1,initialValue=0.0),epochs=5,minibatchSize=1,seed=12345)
        Trainer<Label> logisticRegressionTrainer = new LinearSGDTrainer(objective, optimiser, epochs, loggingInterval, minibatchSize, seed);
        org.tribuo.Model<Label> classificationModel = logisticRegressionTrainer.train(trainDataset);

        MLModel model = MLModel.builder()
                .name(FunctionName.LOGISTIC_REGRESSION.name())
                .algorithm(FunctionName.LOGISTIC_REGRESSION)
                .version(VERSION)
                .content(serializeToBase64(classificationModel))
                .modelState(MLModelState.TRAINED)
                .build();
        return model;
    }

    @Override
    public void initModel(MLModel model, Map<String, Object> params, Encryptor encryptor) {
        this.classificationModel = (org.tribuo.Model<Label>)ModelSerDeSer.deserialize(model);
    }

    @Override
    public void close() {
        this.classificationModel = null;
    }

    @Override
    public MLOutput predict(MLInput mlInput) {
        DataFrame dataFrame = ((DataFrameInputDataset)mlInput.getInputDataset()).getDataFrame();
        MutableDataset<Label> predictionDataset = TribuoUtil.generateDataset(dataFrame, new LabelFactory(),
                "Logistic regression prediction data from OpenSearch", TribuoOutputType.LABEL);

        List<Prediction<Label>> predictions = classificationModel.predict(predictionDataset);
        List<Map<String, Object>> listPrediction = new ArrayList<>();
        predictions.forEach(e -> listPrediction.add(Collections.singletonMap("result", e.getOutput().getLabel())));

        return MLPredictionOutput.builder().predictionResult(DataFrameBuilder.load(listPrediction)).build();
    }

    @Override
    public MLOutput predict(MLInput mlInput, MLModel model) {
        if (model == null) {
            throw new IllegalArgumentException("No model found for logistic regression prediction.");
        }

        classificationModel = (org.tribuo.Model<Label>)ModelSerDeSer.deserialize(model);
        return predict(mlInput);
    }
}
