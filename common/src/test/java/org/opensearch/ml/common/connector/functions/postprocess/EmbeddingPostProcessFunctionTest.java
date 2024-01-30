package org.opensearch.ml.common.connector.functions.postprocess;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.ml.common.output.model.ModelTensor;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class EmbeddingPostProcessFunctionTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    EmbeddingPostProcessFunction function;

    @Before
    public void setUp() {
        function = new EmbeddingPostProcessFunction();
    }

    @Test
    public void process_WrongInput_NotList() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Post process function input is not a List.");
        function.apply("abc");
    }

    @Test
    public void process_WrongInput_NotListOfList() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("The embedding should be a non-empty List containing List of Float values.");
        function.apply(Arrays.asList("abc"));
    }

    @Test
    public void process_WrongInput_NotListOfNumber() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("The embedding should be a non-empty List containing Float values.");
        function.apply(List.of(Arrays.asList("abc")));
    }

    @Test
    public void process_CorrectInput() {
        List<ModelTensor> result = function.apply(List.of(List.of(1.1, 1.2, 1.3), List.of(2.1, 2.2, 2.3)));
        assertEquals(2, result.size());
        assertEquals(3, result.get(0).getData().length);
        assertEquals(1.1, result.get(0).getData()[0]);
        assertEquals(1.2, result.get(0).getData()[1]);
        assertEquals(1.3, result.get(0).getData()[2]);
        assertEquals(3, result.get(1).getData().length);
        assertEquals(2.1, result.get(1).getData()[0]);
        assertEquals(2.2, result.get(1).getData()[1]);
        assertEquals(2.3, result.get(1).getData()[2]);
    }
}
