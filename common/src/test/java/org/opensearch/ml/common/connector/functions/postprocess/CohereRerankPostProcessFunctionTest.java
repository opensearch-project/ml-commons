package org.opensearch.ml.common.connector.functions.postprocess;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.ml.common.output.model.ModelTensor;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class CohereRerankPostProcessFunctionTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    CohereRerankPostProcessFunction function;

    @Before
    public void setUp() {
        function = new CohereRerankPostProcessFunction();
    }

    @Test
    public void process_WrongInput_NotList() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Post process function input is not a List.");
        function.apply("abc");
    }

    @Test
    public void process_WrongInput_NotCorrectList() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Post process function input is not a List of Map.");
        function.apply(Arrays.asList("abc"));
    }

    @Test
    public void process_WrongInput_NotCorrectMap() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("The rerank result should contain index and relevance_score.");
        function.apply(Arrays.asList(Map.of("test1", "value1")));
    }

    @Test
    public void process_CorrectInput() {
        List<Map<String, Object>> rerankResults = List.of(
                Map.of("index", 2, "relevance_score", 0.5),
                Map.of("index", 1, "relevance_score", 0.4),
                Map.of("index", 0, "relevance_score", 0.3));
        List<ModelTensor> result = function.apply(rerankResults);
        assertEquals(3, result.size());
        assertEquals(1, result.get(0).getData().length);
        assertEquals(0.3, result.get(0).getData()[0]);
        assertEquals(0.4, result.get(1).getData()[0]);
        assertEquals(0.5, result.get(2).getData()[0]);
    }
}
