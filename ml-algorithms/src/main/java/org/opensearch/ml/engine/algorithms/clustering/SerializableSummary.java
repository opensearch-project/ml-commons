/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.engine.algorithms.clustering;

import java.io.Serializable;

import com.amazon.randomcutforest.returntypes.SampleSummary;

import lombok.Data;

@Data
public class SerializableSummary implements Serializable {
    private float[][] summaryPoints;
    private float[] mean;
    private float[] median;
    private float[] deviation;
    private float[] lower;
    private float[] upper;
    private float[] relativeWeight;
    private double weightOfSamples;

    public SerializableSummary() {}

    public SerializableSummary(SampleSummary s) {
        summaryPoints = s.summaryPoints;
        mean = s.mean;
        deviation = s.deviation;
        lower = s.lower;
        upper = s.upper;
        relativeWeight = s.relativeWeight;
        weightOfSamples = s.weightOfSamples;
        median = s.median;
    }

    public SampleSummary getSummary() {
        SampleSummary summary = new SampleSummary(0);
        summary.summaryPoints = summaryPoints;
        summary.deviation = deviation;
        summary.lower = lower;
        summary.upper = upper;
        summary.mean = mean;
        summary.median = median;
        summary.relativeWeight = relativeWeight;
        summary.weightOfSamples = weightOfSamples;

        return summary;
    }
}
