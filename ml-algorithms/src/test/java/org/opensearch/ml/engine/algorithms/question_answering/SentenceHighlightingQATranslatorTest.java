/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.engine.algorithms.question_answering;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.engine.algorithms.question_answering.QAConstants.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.algorithms.question_answering.sentence.DefaultSentenceSegmenter;
import org.opensearch.ml.engine.algorithms.question_answering.sentence.Sentence;
import org.opensearch.ml.engine.algorithms.question_answering.sentence.SentenceSegmenter;

import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import ai.djl.translate.TranslatorContext;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class SentenceHighlightingQATranslatorTest {

    private SentenceHighlightingQATranslator translator;
    private TranslatorContext translatorContext;
    private List<Sentence> sentences;
    private String question;

    @Before
    public void setUp() {
        // Create test data
        question = "What are the impacts of climate change?";
        String textContext = "Many coastal cities face increased flooding during storms. "
            + "Farmers are experiencing unpredictable growing seasons and crop failures. "
            + "Scientists predict these environmental shifts will continue to accelerate. "
            + "Global temperatures have risen significantly over the past century. "
            + "Polar ice caps are melting at an alarming rate.";

        // Create real sentences using the actual segmenter
        SentenceSegmenter segmenter = new DefaultSentenceSegmenter();
        sentences = segmenter.segment(textContext);

        // Create mocks
        translator = SentenceHighlightingQATranslator.builder().build();
        translatorContext = mock(TranslatorContext.class);
        NDManager manager = mock(NDManager.class);
        Input input = mock(Input.class);
    }

    @Test
    public void testProcessInput_BasicValidation() {
        // This test verifies that the translator is properly initialized
        assertNotNull(translator);
        assertNotNull(translator.getSegmenter());
        assertEquals(DefaultSentenceSegmenter.class, translator.getSegmenter().getClass());
        // Removed check for gson as we're now using StringUtils.toJson instead of a local Gson instance
    }

    @Test
    public void testProcessOutput_WithRelevantSentences() {
        // Mock context with sentences
        when(translatorContext.getAttachment(KEY_SENTENCES)).thenReturn(sentences);
        
        // Mock model output - 1 means relevant, 0 means not relevant
        // Sentences 0, 3, and 4 are relevant (value 1)
        NDArray mockOutput = mock(NDArray.class);
        when(mockOutput.getShape()).thenReturn(new Shape(5));
        when(mockOutput.getLong(0)).thenReturn(1L); // Relevant
        when(mockOutput.getLong(1)).thenReturn(0L); // Not relevant
        when(mockOutput.getLong(2)).thenReturn(0L); // Not relevant
        when(mockOutput.getLong(3)).thenReturn(1L); // Relevant
        when(mockOutput.getLong(4)).thenReturn(1L); // Relevant
        
        NDList mockList = new NDList(mockOutput);
        
        // Process output
        Output output = translator.processOutput(translatorContext, mockList);
        
        // Verify results
        assertNotNull(output);
        byte[] bytes = output.getData().getAsBytes();
        ModelTensors tensorOutput = ModelTensors.fromBytes(bytes);
        List<ModelTensor> modelTensorsList = tensorOutput.getMlModelTensors();
        
        // Should have one tensor with the highlights
        assertEquals(1, modelTensorsList.size());
        ModelTensor highlightsTensor = modelTensorsList.get(0);
        assertEquals(FIELD_HIGHLIGHTS, highlightsTensor.getName());
        
        // Get the highlights from the dataAsMap
        @SuppressWarnings("unchecked")
        Map<String, Object> dataMap = (Map<String, Object>) highlightsTensor.getDataAsMap();
        assertNotNull(dataMap);
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> highlights = (List<Map<String, Object>>) dataMap.get(FIELD_HIGHLIGHTS);
        assertNotNull(highlights);
        
        // Should have 3 highlighted sentences (0, 3, 4)
        assertEquals(3, highlights.size());
        
        // Verify the first highlighted sentence
        @SuppressWarnings("unchecked")
        Map<String, Object> firstHighlight = (Map<String, Object>) highlights.get(0);
        assertEquals(0, ((Number) firstHighlight.get(FIELD_POSITION)).intValue());
        assertEquals("Many coastal cities face increased flooding during storms.", firstHighlight.get(FIELD_TEXT));
        assertEquals(0, ((Number) firstHighlight.get(FIELD_START)).intValue());
        assertEquals(58, ((Number) firstHighlight.get(FIELD_END)).intValue());
    }

    @Test
    public void testProcessOutput_WithNoRelevantSentences() {
        // Mock context with sentences
        when(translatorContext.getAttachment(KEY_SENTENCES)).thenReturn(sentences);
        
        // Mock model output - all sentences are not relevant (value 0)
        NDArray mockOutput = mock(NDArray.class);
        when(mockOutput.getShape()).thenReturn(new Shape(5));
        when(mockOutput.getLong(0)).thenReturn(0L); // Not relevant
        when(mockOutput.getLong(1)).thenReturn(0L); // Not relevant
        when(mockOutput.getLong(2)).thenReturn(0L); // Not relevant
        when(mockOutput.getLong(3)).thenReturn(0L); // Not relevant
        when(mockOutput.getLong(4)).thenReturn(0L); // Not relevant
        
        NDList mockList = new NDList(mockOutput);
        
        // Process output
        Output output = translator.processOutput(translatorContext, mockList);
        
        // Verify results
        assertNotNull(output);
        byte[] bytes = output.getData().getAsBytes();
        ModelTensors tensorOutput = ModelTensors.fromBytes(bytes);
        List<ModelTensor> modelTensorsList = tensorOutput.getMlModelTensors();
        
        // Should have one tensor with the highlights
        assertEquals(1, modelTensorsList.size());
        ModelTensor highlightsTensor = modelTensorsList.get(0);
        assertEquals(FIELD_HIGHLIGHTS, highlightsTensor.getName());
        
        // Get the highlights from the dataAsMap
        @SuppressWarnings("unchecked")
        Map<String, Object> dataMap = (Map<String, Object>) highlightsTensor.getDataAsMap();
        assertNotNull(dataMap);
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> highlights = (List<Map<String, Object>>) dataMap.get(FIELD_HIGHLIGHTS);
        assertNotNull(highlights);
        
        // Should have 0 highlighted sentences
        assertEquals(0, highlights.size());
    }

    @Test
    public void testProcessOutput_WithEmptyNDList() {
        // Mock context with sentences
        when(translatorContext.getAttachment(KEY_SENTENCES)).thenReturn(sentences);
        
        // Process output with empty NDList
        Output output = translator.processOutput(translatorContext, new NDList());
        
        // Verify results
        assertNotNull(output);
        byte[] bytes = output.getData().getAsBytes();
        ModelTensors tensorOutput = ModelTensors.fromBytes(bytes);
        List<ModelTensor> modelTensorsList = tensorOutput.getMlModelTensors();
        
        // Should have one tensor with the error
        assertEquals(1, modelTensorsList.size());
        ModelTensor errorTensor = modelTensorsList.get(0);
        
        // Get the error from the dataAsMap
        @SuppressWarnings("unchecked")
        Map<String, Object> dataMap = (Map<String, Object>) errorTensor.getDataAsMap();
        assertNotNull(dataMap);
        
        // Should have an error message
        assertNotNull(dataMap.get(FIELD_ERROR));
        
        // Should have an empty highlights list
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> highlights = (List<Map<String, Object>>) dataMap.get(FIELD_HIGHLIGHTS);
        assertNotNull(highlights);
        assertEquals(0, highlights.size());
    }

    @Test
    public void testProcessOutput_WithNoSentences() {
        // Mock context with no sentences
        when(translatorContext.getAttachment(KEY_SENTENCES)).thenReturn(new ArrayList<>());
        
        // Mock model output
        NDArray mockOutput = mock(NDArray.class);
        when(mockOutput.getShape()).thenReturn(new Shape(0));
        
        NDList mockList = new NDList(mockOutput);
        
        // Process output
        Output output = translator.processOutput(translatorContext, mockList);
        
        // Verify results
        assertNotNull(output);
        byte[] bytes = output.getData().getAsBytes();
        ModelTensors tensorOutput = ModelTensors.fromBytes(bytes);
        List<ModelTensor> modelTensorsList = tensorOutput.getMlModelTensors();
        
        // Should have one tensor with the error
        assertEquals(1, modelTensorsList.size());
        ModelTensor errorTensor = modelTensorsList.get(0);
        
        // Get the error from the dataAsMap
        @SuppressWarnings("unchecked")
        Map<String, Object> dataMap = (Map<String, Object>) errorTensor.getDataAsMap();
        assertNotNull(dataMap);
        
        // Should have an error message
        assertNotNull(dataMap.get(FIELD_ERROR));
        
        // Should have an empty highlights list
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> highlights = (List<Map<String, Object>>) dataMap.get(FIELD_HIGHLIGHTS);
        assertNotNull(highlights);
        assertEquals(0, highlights.size());
    }

    @Test
    public void testIsRelevantPrediction() throws Exception {
        // Use reflection to access the protected method
        java.lang.reflect.Method isRelevantPredictionMethod = SentenceHighlightingQATranslator.class
            .getDeclaredMethod("isRelevantPrediction", long.class);
        isRelevantPredictionMethod.setAccessible(true);

        // Test with relevant value (1)
        boolean isRelevant = (boolean) isRelevantPredictionMethod.invoke(translator, 1L);
        assertTrue("Value 1 should be considered relevant", isRelevant);

        // Test with non-relevant value (0)
        boolean isNotRelevant = (boolean) isRelevantPredictionMethod.invoke(translator, 0L);
        assertFalse("Value 0 should not be considered relevant", isNotRelevant);

        // Test with other values
        boolean isOtherValueRelevant = (boolean) isRelevantPredictionMethod.invoke(translator, 2L);
        assertFalse("Value 2 should not be considered relevant", isOtherValueRelevant);
    }

    @Test
    public void testCreateDefault() {
        SentenceHighlightingQATranslator translator = SentenceHighlightingQATranslator.createDefault();
        assertNotNull(translator);
        assertNotNull(translator.getSegmenter());
        assertEquals(DefaultSentenceSegmenter.class, translator.getSegmenter().getClass());
        // Removed check for gson as we're now using StringUtils.toJson instead of a local Gson instance
    }
}
