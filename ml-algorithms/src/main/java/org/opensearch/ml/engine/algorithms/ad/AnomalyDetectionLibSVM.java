/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.ad;

import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DataFrameBuilder;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.input.parameter.ad.AnomalyDetectionLibSVMParams;
import org.opensearch.ml.common.input.parameter.MLAlgoParams;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.MLPredictionOutput;
import org.opensearch.ml.engine.Predictable;
import org.opensearch.ml.engine.Trainable;
import org.opensearch.ml.engine.annotation.Function;
import org.opensearch.ml.engine.contants.TribuoOutputType;
import org.opensearch.ml.engine.utils.ModelSerDeSer;
import org.opensearch.ml.engine.utils.TribuoUtil;
import org.tribuo.MutableDataset;
import org.tribuo.Prediction;
import org.tribuo.anomaly.AnomalyFactory;
import org.tribuo.anomaly.Event;
import org.tribuo.anomaly.libsvm.LibSVMAnomalyModel;
import org.tribuo.anomaly.libsvm.LibSVMAnomalyTrainer;
import org.tribuo.anomaly.libsvm.SVMAnomalyType;
import org.tribuo.common.libsvm.KernelType;
import org.tribuo.common.libsvm.LibSVMModel;
import org.tribuo.common.libsvm.SVMParameters;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Wrap Tribuo's anomaly detection based on one-class SVM (libSVM).
 *
 */
@Function(FunctionName.AD_LIBSVM)
public class AnomalyDetectionLibSVM implements Trainable, Predictable {
    public static final int VERSION = 1;
    private static double DEFAULT_GAMMA = 1.0;
    private static double DEFAULT_NU = 0.1;
    private static KernelType DEFAULT_KERNEL_TYPE = KernelType.RBF;

    private AnomalyDetectionLibSVMParams parameters;
    private LibSVMModel libSVMAnomalyModel = null;

    public AnomalyDetectionLibSVM() {}

    public AnomalyDetectionLibSVM(MLAlgoParams parameters) {
        this.parameters = parameters == null ? AnomalyDetectionLibSVMParams.builder().build() : (AnomalyDetectionLibSVMParams)parameters;
        validateParameters();
    }

    private void validateParameters() {

        if (parameters.getGamma() != null && parameters.getGamma() <= 0) {
            throw new IllegalArgumentException("gamma should be positive.");
        }

        if (parameters.getNu() != null && parameters.getNu() <= 0) {
            throw new IllegalArgumentException("nu should be positive.");
        }

    }

    @Override
    public void initModel(MLModel model, Map<String, Object> params) {
        this.libSVMAnomalyModel = (LibSVMModel) ModelSerDeSer.deserialize(model);
    }

    @Override
    public void close() {
        this.libSVMAnomalyModel = null;
    }

    @Override
    public MLOutput predict(MLInputDataset inputDataset) {
        DataFrame dataFrame = ((DataFrameInputDataset)inputDataset).getDataFrame();
        if (libSVMAnomalyModel == null) {
            throw new IllegalArgumentException("model not loaded");
        }
        List<Prediction<Event>> predictions;
        MutableDataset<Event> predictionDataset = TribuoUtil.generateDataset(dataFrame, new AnomalyFactory(),
                "Anomaly detection LibSVM prediction data from OpenSearch", TribuoOutputType.ANOMALY_DETECTION_LIBSVM);
        predictions = libSVMAnomalyModel.predict(predictionDataset);

        List<Map<String, Object>> adResults = new ArrayList<>();
        predictions.forEach(e -> {
            Map<String, Object> result = new HashMap<>();
            result.put("score", e.getOutput().getScore());
            result.put("anomaly_type", e.getOutput().getType().name());
            adResults.add(result);
        });

        return MLPredictionOutput.builder().predictionResult(DataFrameBuilder.load(adResults)).build();
    }

    @Override
    public MLOutput predict(MLInputDataset inputDataset, MLModel model) {
        if (model == null) {
            throw new IllegalArgumentException("No model found for KMeans prediction.");
        }

        libSVMAnomalyModel = (LibSVMModel) ModelSerDeSer.deserialize(model);
        return predict(inputDataset);
    }

    @Override
    public MLModel train(MLInputDataset inputDataset) {
        DataFrame dataFrame = ((DataFrameInputDataset)inputDataset).getDataFrame();
        KernelType kernelType = parseKernelType();
        SVMParameters params = new SVMParameters<>(new SVMAnomalyType(SVMAnomalyType.SVMMode.ONE_CLASS), kernelType);
        Double gamma = Optional.ofNullable(parameters.getGamma()).orElse(DEFAULT_GAMMA);
        Double nu = Optional.ofNullable(parameters.getNu()).orElse(DEFAULT_NU);
        params.setGamma(gamma);
        params.setNu(nu);
        if (parameters.getCost() != null) {
            params.setCost(parameters.getCost());
        }
        if (parameters.getCoeff() != null) {
            params.setCoeff(parameters.getCoeff());
        }
        if (parameters.getEpsilon() != null) {
            params.setEpsilon(parameters.getEpsilon());
        }
        if (parameters.getDegree() != null) {
            params.setDegree(parameters.getDegree());
        }
        MutableDataset<Event> data = TribuoUtil.generateDataset(dataFrame, new AnomalyFactory(),
                "Anomaly detection LibSVM training data from OpenSearch", TribuoOutputType.ANOMALY_DETECTION_LIBSVM);

        LibSVMAnomalyTrainer trainer = new LibSVMAnomalyTrainer(params);

        LibSVMModel libSVMModel = trainer.train(data);
        ((LibSVMAnomalyModel)libSVMModel).getNumberOfSupportVectors();

        MLModel model = MLModel.builder()
                .name(FunctionName.AD_LIBSVM.name())
                .algorithm(FunctionName.AD_LIBSVM)
                .version(VERSION)
                .content(ModelSerDeSer.serializeToBase64(libSVMModel))
                .build();
        return model;
    }

    private KernelType parseKernelType() {
        KernelType kernelType = DEFAULT_KERNEL_TYPE;
        if (parameters.getKernelType() == null) {
            return kernelType;
        }
        switch (parameters.getKernelType()){
            case LINEAR:
                kernelType = KernelType.LINEAR;
                break;
            case POLY:
                kernelType = KernelType.POLY;
                break;
            case RBF:
                kernelType = KernelType.RBF;
                break;
            case SIGMOID:
                kernelType = KernelType.SIGMOID;
                break;
            default:
                break;
        }
        return kernelType;
    }
}
