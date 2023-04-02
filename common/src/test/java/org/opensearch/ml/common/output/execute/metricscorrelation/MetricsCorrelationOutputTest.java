///*
// * Copyright OpenSearch Contributors
// * SPDX-License-Identifier: Apache-2.0
// */
//
//package org.opensearch.ml.common.output.execute.metricscorrelation;
//
//import org.junit.Before;
//import org.junit.Test;
//import org.opensearch.common.Strings;
//import org.opensearch.common.io.stream.BytesStreamOutput;
//import org.opensearch.common.io.stream.StreamInput;
//import org.opensearch.common.xcontent.XContentFactory;
//import org.opensearch.common.xcontent.XContentType;
//import org.opensearch.core.xcontent.ToXContent;
//import org.opensearch.core.xcontent.XContentBuilder;
//import org.opensearch.ml.common.output.execute.metrics_correlation.MetricsCorrelationOutput;
//import org.opensearch.ml.common.output.execute.samplecalculator.LocalSampleCalculatorOutput;
//
//import java.io.IOException;
//
//import static org.junit.Assert.assertEquals;
//
//public class MetricsCorrelationOutputTest {
//
//    MetricsCorrelationOutput output;
//    @Before
//    public void setUp() {
//        output = MetricsCorrelationOutput.builder().modelId("testModelId").build();
//    }
//
//    @Test
//    public void toXContent() throws IOException {
//        XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
//        builder.startObject();
//        output.toXContent(builder, ToXContent.EMPTY_PARAMS);
//        builder.endObject();
//        String jsonStr = Strings.toString(builder);
//        assertEquals("{\"result\":\"testModelId\"}", jsonStr);
//    }
//
//    @Test
//    public void toXContent_EmptyOutput() throws IOException {
//        MetricsCorrelationOutput output = MetricsCorrelationOutput.builder().build();
//        XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
//        builder.startObject();
//        output.toXContent(builder, ToXContent.EMPTY_PARAMS);
//        builder.endObject();
//        String jsonStr = Strings.toString(builder);
//        assertEquals("{}", jsonStr);
//    }
//
//    @Test
//    public void readInputStream_Success() throws IOException {
//        readInputStream(output);
//    }
//
//    private void readInputStream(MetricsCorrelationOutput output) throws IOException {
//        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
//        output.writeTo(bytesStreamOutput);
//
//        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
//        MetricsCorrelationOutput parsedOutput = new MetricsCorrelationOutput(streamInput);
//        assertEquals(output, parsedOutput);
//    }
//}
