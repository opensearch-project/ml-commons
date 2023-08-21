package org.opensearch.ml.engine.utils;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.ingest.TestTemplateService;
import org.opensearch.ml.common.connector.MLPostProcessFunction;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.script.ScriptService;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class ScriptUtilsTest {

    @Mock
    ScriptService scriptService;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(scriptService.compile(any(), any())).then(invocation -> new TestTemplateService.MockTemplateScript.Factory("test result"));
    }

    @Test
    public void test_executePreprocessFunction() {
        Optional<String> resultOpt = ScriptUtils.executePreprocessFunction(scriptService, "any function", Collections.singletonList("any input"));
        assertEquals("test result", resultOpt.get());
    }

    @Test
    public void test_executeBuildInPostProcessFunction() {
        List<List<Float>> input = Arrays.asList(Arrays.asList(1.0f, 2.0f), Arrays.asList(3.0f, 4.0f));
        List<ModelTensor> modelTensors = ScriptUtils.executeBuildInPostProcessFunction(input, MLPostProcessFunction.get(MLPostProcessFunction.NEURAL_SEARCH_EMBEDDING));
        assertNotNull(modelTensors);
        assertEquals(2, modelTensors.size());
    }

    @Test
    public void test_executePostProcessFunction() {
        when(scriptService.compile(any(), any())).then(invocation -> new TestTemplateService.MockTemplateScript.Factory("{\"result\": \"test result\"}"));
        Optional<String> resultOpt = ScriptUtils.executePostProcessFunction(scriptService, "any function", "{\"result\": \"test result\"}");
        assertEquals("{\"result\": \"test result\"}", resultOpt.get());
    }

    @Test
    public void test_executeScript() {
        String result = ScriptUtils.executeScript(scriptService, "any function", Collections.singletonMap("key", "value"));
        assertEquals("test result", result);
    }
}
