/*
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  The OpenSearch Contributors require contributions made to
 *  this file be licensed under the Apache-2.0 license or a
 *  compatible open source license.
 *
 *  Modifications Copyright OpenSearch Contributors. See
 *  GitHub history for details.
 */

package org.opensearch.ml.client;


import java.util.List;

import org.opensearch.action.ActionFuture;
import org.opensearch.action.ActionListener;

import org.opensearch.action.support.PlainActionFuture;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.parameter.MLParameter;

/**
 * A client to provide interfaces for machine learning jobs. This will be used by other plugins.
 */
public interface MachineLearningClient {

    /**
     * Do uploading custom models job
     *
     * @param name      name for the custom model
     * @param format    model format, for now only support "PMML"
     * @param algorithm algorithm name
     * @param body      the base64 encoded string of the model body
     * @return the result future
     */
    default ActionFuture<String> upload(String name, String format, String algorithm, String body) {
        PlainActionFuture<String> actionFuture = PlainActionFuture.newFuture();
        upload(name, format, algorithm, body, actionFuture);
        return actionFuture;
    }

    /**
     * Do uploading custom models job
     *
     * @param name      name for the custom model
     * @param format    model format, for now only support "PMML"
     * @param algorithm algorithm name
     * @param body      the base64 encoded string of the model body
     * @param listener  a listener to be notified of the result
     */
    void upload(String name, String format, String algorithm, String body, ActionListener<String> listener);

    /**
     * Do searching models job
     *
     * @param modelId   model id
     * @param name      model name
     * @param format    model format
     * @param algorithm algorithm name
     * @return the result future
     */
    default ActionFuture<String> search(String modelId, String name, String format, String algorithm) {
        PlainActionFuture<String> actionFuture = PlainActionFuture.newFuture();
        search(modelId, name, format, algorithm, actionFuture);
        return actionFuture;
    }

    /**
     * Do searching models job
     *
     * @param modelId model id
     * @param name      model name
     * @param format    model format
     * @param algorithm algorithm name
     */
    void search(String modelId, String name, String format, String algorithm, ActionListener<String> listener);

    /**
     * Do prediction machine learning job
     *
     * @param algorithm algorithm name
     * @param inputData input data frame
     * @return the result future
     */
    default ActionFuture<DataFrame> predict(String algorithm, DataFrame inputData) {
        return predict(algorithm, null, inputData, (String) null);
    }

    /**
     * Do prediction machine learning job
     *
     * @param algorithm  algorithm name
     * @param parameters parameters of ml algorithm
     * @param inputData  input data frame
     * @return the result future
     */
    default ActionFuture<DataFrame> predict(String algorithm, List<MLParameter> parameters, DataFrame inputData) {
        return predict(algorithm, parameters, inputData, (String) null);
    }

    /**
     * Do prediction machine learning job
     *
     * @param algorithm  algorithm name
     * @param parameters parameters of ml algorithm
     * @param inputData  input data frame
     * @param modelId    the trained model id
     * @return the result future
     */
    default ActionFuture<DataFrame> predict(String algorithm, List<MLParameter> parameters, DataFrame inputData, String modelId) {
        PlainActionFuture<DataFrame> actionFuture = PlainActionFuture.newFuture();
        predict(algorithm, parameters, inputData, modelId, actionFuture);
        return actionFuture;
    }

    /**
     * Do prediction machine learning job
     *
     * @param algorithm algorithm name
     * @param inputData input data frame
     * @param listener  a listener to be notified of the result
     */
    default void predict(String algorithm, DataFrame inputData, ActionListener<DataFrame> listener) {
        predict(algorithm, null, inputData, null, listener);
    }

    /**
     * Do prediction machine learning job
     *
     * @param algorithm  algorithm name
     * @param parameters parameters of ml algorithm
     * @param inputData  input data frame
     * @param listener   a listener to be notified of the result
     */
    default void predict(String algorithm, List<MLParameter> parameters, DataFrame inputData, ActionListener<DataFrame> listener) {
        predict(algorithm, parameters, inputData, null, listener);
    }

    /**
     * Do prediction machine learning job
     *
     * @param algorithm  algorithm name
     * @param parameters parameters of ml algorithm
     * @param inputData  input data frame
     * @param modelId    the trained model id
     * @param listener   a listener to be notified of the result
     */
    default void predict(String algorithm, List<MLParameter> parameters, DataFrame inputData, String modelId, ActionListener<DataFrame> listener) {
        predict(algorithm, parameters, DataFrameInputDataset.builder().dataFrame(inputData).build(), modelId, listener);
    }

    /**
     * Do prediction machine learning job
     *
     * @param algorithm  algorithm name
     * @param parameters parameters of ml algorithm
     * @param inputData  input data set
     * @param modelId    the trained model id
     * @param listener   a listener to be notified of the result
     */
    void predict(String algorithm, List<MLParameter> parameters, MLInputDataset inputData, String modelId, ActionListener<DataFrame> listener);

    /**
     * Do the training machine learning job. The training job will be always async process. The job id will be returned in this method.
     *
     * @param algorithm  algorithm name
     * @param parameters parameters of ml algorithm
     * @param inputData  input data frame
     * @return the result future
     */
    default ActionFuture<String> train(String algorithm, List<MLParameter> parameters, DataFrame inputData) {
        PlainActionFuture<String> actionFuture = PlainActionFuture.newFuture();
        train(algorithm, parameters, inputData, actionFuture);
        return actionFuture;
    }

    /**
     * Do the training machine learning job. The training job will be always async process. The job id will be returned in this method.
     *
     * @param algorithm  algorithm name
     * @param parameters parameters of ml algorithm
     * @param inputData  input data frame
     * @param listener   a listener to be notified of the result
     */
    default void train(String algorithm, List<MLParameter> parameters, DataFrame inputData, ActionListener<String> listener) {
        train(algorithm, parameters, DataFrameInputDataset.builder().dataFrame(inputData).build(), listener);
    }


    /**
     * Do the training machine learning job. The training job will be always async process. The job id will be returned in this method.
     *
     * @param algorithm  algorithm name
     * @param parameters parameters of ml algorithm
     * @param inputData  input data set
     * @param listener   a listener to be notified of the result
     */
    void train(String algorithm, List<MLParameter> parameters, MLInputDataset inputData, ActionListener<String> listener);

}
