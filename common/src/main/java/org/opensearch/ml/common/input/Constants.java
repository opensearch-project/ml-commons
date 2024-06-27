/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input;

public class Constants {
    // train, predict, trainandpredict
    public static final String ACTION = "action";
    public static final String TRAIN = "train";
    public static final String PREDICT = "predict";
    public static final String TRAINANDPREDICT = "trainandpredict";
    // algorithm
    public static final String ALGORITHM = "algorithm";
    public static final String KMEANS = "kmeans";
    public static final String RCF = "rcf";
    // common parameters
    public static final String ASYNC = "async";
    public static final String MODELID = "modelid";
    // Only need to define the below key strings for KMEANS and AD, as
    // these strings are set to keywords in ast of PPL.
    // KMEANS constants
    public static final String KM_CENTROIDS = "centroid";
    public static final String KM_ITERATIONS = "iteration";
    public static final String KM_DISTANCE_TYPE = "distType";
    // AD constants
    public static final String AD_NUMBER_OF_TREES = "numberTrees";
    public static final String AD_SHINGLE_SIZE = "shingleSize";
    public static final String AD_SAMPLE_SIZE = "sampleSize";
    public static final String AD_OUTPUT_AFTER = "outputAfter";
    public static final String AD_TIME_DECAY = "timeDecay";
    public static final String AD_ANOMALY_RATE = "anomalyRate";
    public static final String AD_TIME_FIELD = "timeField";
    public static final String AD_TIME_ZONE = "timeZone";
    public static final String AD_TRAINING_DATA_SIZE = "trainingDataSize";
    public static final String AD_ANOMALY_SCORE_THRESHOLD = "anomalyScoreThreshold";
    public static final String AD_DATE_FORMAT = "dateFormat";

    public static final String TENANT_ID = "x-tenant-id";
}
