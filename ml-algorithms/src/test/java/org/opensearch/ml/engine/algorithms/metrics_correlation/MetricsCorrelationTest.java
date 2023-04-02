///*
// * Copyright OpenSearch Contributors
// * SPDX-License-Identifier: Apache-2.0
// */
//
//package org.opensearch.ml.engine.algorithms.metrics_correlation;
//
//import org.junit.Assert;
//import org.junit.Before;
//import org.junit.Rule;
//import org.junit.Test;
//import org.junit.rules.ExpectedException;
//import org.mockito.Mock;
//import org.opensearch.client.Client;
//import org.opensearch.common.settings.Settings;
//import org.opensearch.ml.common.input.execute.metricscorrelation.MetricsCorrelationInput;
//import org.opensearch.ml.common.input.execute.samplecalculator.LocalSampleCalculatorInput;
//import org.opensearch.ml.common.output.execute.metrics_correlation.MetricsCorrelationOutput;
//import org.opensearch.ml.common.output.execute.samplecalculator.LocalSampleCalculatorOutput;
//import org.opensearch.ml.engine.algorithms.sample.LocalSampleCalculator;
//
//import java.util.Arrays;
//
//public class MetricsCorrelationTest {
//    @Rule
//    public ExpectedException exceptionRule = ExpectedException.none();
//    @Mock
//    Client client;
//    @Mock
//    Settings settings;
//    private MetricsCorrelation metricsCorrelation;
//    private MetricsCorrelationInput input;
//
//    @Before
//    public void setUp() {
//        metricsCorrelation = new MetricsCorrelation(client, settings);
//        input = new MetricsCorrelationInput("sum", Arrays.asList(1.0F, 2.0F, 3.0F));
//    }
//}
