/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.dataset;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Arrays;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;

public class TextDocsInputDataSetTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void writeTo_Success() throws IOException {
        TextDocsInputDataSet inputDataSet = TextDocsInputDataSet.builder().docs(Arrays.asList("doc1", "doc2")).build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        inputDataSet.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLInputDataType inputDataType = streamInput.readEnum(MLInputDataType.class);
        assertEquals(MLInputDataType.TEXT_DOCS, inputDataType);
        TextDocsInputDataSet inputDataSet1 = new TextDocsInputDataSet((streamInput));
        assertEquals(2, inputDataSet1.getDocs().size());
        assertEquals("doc1", inputDataSet1.getDocs().get(0));
        assertEquals("doc2", inputDataSet1.getDocs().get(1));
    }

    @Test
    public void writeTo_Success_NullValue() throws IOException {
        TextDocsInputDataSet inputDataSet = TextDocsInputDataSet.builder().docs(Arrays.asList("doc1", null)).build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        inputDataSet.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLInputDataType inputDataType = streamInput.readEnum(MLInputDataType.class);
        assertEquals(MLInputDataType.TEXT_DOCS, inputDataType);
        TextDocsInputDataSet inputDataSet1 = new TextDocsInputDataSet((streamInput));
        assertEquals(2, inputDataSet1.getDocs().size());
        assertEquals("doc1", inputDataSet1.getDocs().get(0));
        assertEquals(null, inputDataSet1.getDocs().get(1));
    }
}
