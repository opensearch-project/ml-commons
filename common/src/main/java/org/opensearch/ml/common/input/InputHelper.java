/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input;

import static org.opensearch.ml.common.FunctionName.BATCH_RCF;
import static org.opensearch.ml.common.FunctionName.FIT_RCF;
import static org.opensearch.ml.common.FunctionName.KMEANS;
import static org.opensearch.ml.common.input.Constants.ACTION;
import static org.opensearch.ml.common.input.Constants.AD_ANOMALY_RATE;
import static org.opensearch.ml.common.input.Constants.AD_ANOMALY_SCORE_THRESHOLD;
import static org.opensearch.ml.common.input.Constants.AD_DATE_FORMAT;
import static org.opensearch.ml.common.input.Constants.AD_NUMBER_OF_TREES;
import static org.opensearch.ml.common.input.Constants.AD_OUTPUT_AFTER;
import static org.opensearch.ml.common.input.Constants.AD_SAMPLE_SIZE;
import static org.opensearch.ml.common.input.Constants.AD_SHINGLE_SIZE;
import static org.opensearch.ml.common.input.Constants.AD_TIME_DECAY;
import static org.opensearch.ml.common.input.Constants.AD_TIME_FIELD;
import static org.opensearch.ml.common.input.Constants.AD_TIME_ZONE;
import static org.opensearch.ml.common.input.Constants.AD_TRAINING_DATA_SIZE;
import static org.opensearch.ml.common.input.Constants.ALGORITHM;
import static org.opensearch.ml.common.input.Constants.KM_CENTROIDS;
import static org.opensearch.ml.common.input.Constants.KM_DISTANCE_TYPE;
import static org.opensearch.ml.common.input.Constants.KM_ITERATIONS;

import java.util.Locale;
import java.util.Map;

import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.input.parameter.MLAlgoParams;
import org.opensearch.ml.common.input.parameter.clustering.KMeansParams;
import org.opensearch.ml.common.input.parameter.rcf.BatchRCFParams;
import org.opensearch.ml.common.input.parameter.rcf.FitRCFParams;

public class InputHelper {
    public static String getAction(Map<String, Object> arguments) {
        return (String) arguments.get(ACTION);
    }

    public static FunctionName getFunctionName(Map<String, Object> arguments) {
        String algo = (String) arguments.get(ALGORITHM);
        if (algo == null) {
            throw new IllegalArgumentException("The parameter algorithm is required.");
        }
        switch (algo.toLowerCase(Locale.ROOT)) {
            case Constants.KMEANS:
                return KMEANS;
            case Constants.RCF:
                return arguments.get(AD_TIME_FIELD) == null ? BATCH_RCF : FIT_RCF;
            default:
                throw new IllegalArgumentException(String.format("unsupported algorithm: %s.", algo));
        }
    }

    public static MLAlgoParams convertArgumentToMLParameter(Map<String, Object> arguments, FunctionName func) {
        switch (func) {
            case KMEANS:
                return buildKMeansParameters(arguments);
            case BATCH_RCF:
                return buildBatchRCFParameters(arguments);
            case FIT_RCF:
                return buildFitRCFParameters(arguments);
            default:
                throw new IllegalArgumentException(String.format("unsupported algorithm: %s.", func));
        }
    }

    private static MLAlgoParams buildKMeansParameters(Map<String, Object> arguments) {
        return KMeansParams
            .builder()
            .centroids((Integer) arguments.get(KM_CENTROIDS))
            .iterations((Integer) arguments.get(KM_ITERATIONS))
            .distanceType(
                arguments.containsKey(KM_DISTANCE_TYPE)
                    ? KMeansParams.DistanceType.valueOf(((String) arguments.get(KM_DISTANCE_TYPE)).toUpperCase(Locale.ROOT))
                    : null
            )
            .build();
    }

    private static MLAlgoParams buildBatchRCFParameters(Map<String, Object> arguments) {
        return BatchRCFParams
            .builder()
            .numberOfTrees((Integer) arguments.get(AD_NUMBER_OF_TREES))
            .sampleSize((Integer) arguments.get(AD_SAMPLE_SIZE))
            .outputAfter((Integer) arguments.get(AD_OUTPUT_AFTER))
            .trainingDataSize((Integer) arguments.get(AD_TRAINING_DATA_SIZE))
            .anomalyScoreThreshold((Double) arguments.get(AD_ANOMALY_SCORE_THRESHOLD))
            .build();
    }

    private static MLAlgoParams buildFitRCFParameters(Map<String, Object> arguments) {
        return FitRCFParams
            .builder()
            .numberOfTrees((Integer) arguments.get(AD_NUMBER_OF_TREES))
            .shingleSize((Integer) arguments.get(AD_SHINGLE_SIZE))
            .sampleSize((Integer) arguments.get(AD_SAMPLE_SIZE))
            .outputAfter((Integer) arguments.get(AD_OUTPUT_AFTER))
            .timeDecay((Double) arguments.get(AD_TIME_DECAY))
            .anomalyRate((Double) arguments.get(AD_ANOMALY_RATE))
            .timeField((String) arguments.get(AD_TIME_FIELD))
            .dateFormat(arguments.containsKey(AD_DATE_FORMAT) ? ((String) arguments.get(AD_DATE_FORMAT)) : "yyyy-MM-dd HH:mm:ss")
            .timeZone((String) arguments.get(AD_TIME_ZONE))
            .build();
    }
}
