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


import org.opensearch.action.ActionFuture;
import org.opensearch.action.ActionListener;
import org.opensearch.action.support.PlainActionFuture;
import org.opensearch.ml.common.parameter.MLInput;
import org.opensearch.ml.common.parameter.MLOutput;

/**
 * A client to provide interfaces for machine learning jobs. This will be used by other plugins.
 */
public interface MachineLearningClient {

    /**
     * Do prediction machine learning job
     * @param modelId the trained model id
     * @param mlInput ML input
     * @return
     */
    default ActionFuture<MLOutput> predict(String modelId, MLInput mlInput) {
        PlainActionFuture<MLOutput> actionFuture = PlainActionFuture.newFuture();
        predict(modelId, mlInput, actionFuture);
        return actionFuture;
    }

    /**
     * Do prediction machine learning job
     * @param modelId the trained model id
     * @param mlInput ML input
     * @param listener a listener to be notified of the result
     */
    void predict(String modelId, MLInput mlInput, ActionListener<MLOutput> listener);

    /**
     *  Do the training machine learning job. The training job will be always async process. The job id will be returned in this method.
     * @param mlInput ML input
     * @return the result future
     */
    default ActionFuture<MLOutput> train(MLInput mlInput) {
        PlainActionFuture<MLOutput> actionFuture = PlainActionFuture.newFuture();
        train(mlInput, actionFuture);
        return actionFuture;
    }


    /**
     * Do the training machine learning job. The training job will be always async process. The job id will be returned in this method.
     * @param mlInput ML input
     * @param listener a listener to be notified of the result
     */
    void train(MLInput mlInput, ActionListener<MLOutput> listener);

    /**
     * Execute ML algorithm.
     * @param mlInput
     * @return
     */
    default ActionFuture<MLOutput> execute(MLInput mlInput) {
        PlainActionFuture<MLOutput> actionFuture = PlainActionFuture.newFuture();
        execute(mlInput, actionFuture);
        return actionFuture;
    }

    /**
     * Execute ML algorithm
     * @param mlInput
     * @param listener
     */
    void execute(MLInput mlInput, ActionListener<MLOutput> listener);
}
