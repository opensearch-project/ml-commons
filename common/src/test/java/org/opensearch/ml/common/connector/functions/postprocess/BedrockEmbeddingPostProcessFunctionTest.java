package org.opensearch.ml.common.connector.functions.postprocess;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.ml.common.output.model.ModelTensor;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class BedrockEmbeddingPostProcessFunctionTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    BedrockEmbeddingPostProcessFunction function;

    @Before
    public void setUp() {
        function = new BedrockEmbeddingPostProcessFunction();
    }

    @Test
    public void process_WrongInput_NotList() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Post process function input is not a List.");
        function.apply("abc");
    }

    @Test
    public void process_WrongInput_NotNumberList() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("The embedding should be a non-empty List containing Float values.");
        function.apply(Arrays.asList("abc"));
    }

    @Test
    public void process_CorrectInput() {
        List<ModelTensor> result = function.apply(Arrays.asList(1.1, 1.2, 1.3));
        assertEquals(1, result.size());
        assertEquals(3, result.get(0).getData().length);
        assertEquals(1.1, result.get(0).getData()[0]);
        assertEquals(1.2, result.get(0).getData()[1]);
        assertEquals(1.3, result.get(0).getData()[2]);
    }
}
