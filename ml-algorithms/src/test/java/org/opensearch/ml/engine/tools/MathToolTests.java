package org.opensearch.ml.engine.tools;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.action.ActionListener;
import org.opensearch.script.Script;
import org.opensearch.script.ScriptService;
import org.opensearch.script.TemplateScript;

public class MathToolTests {

    @Mock
    private ScriptService scriptService;

    @Mock
    private TemplateScript templateScript;

    @Mock
    private TemplateScript.Factory templateScriptFactory;

    @Mock
    private ActionListener<String> listener;

    @Captor
    private ArgumentCaptor<Script> scriptArgumentCaptor;

    private MathTool mathTool;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        MathTool.Factory.getInstance().init(scriptService);
    }

    @Test
    public void test_HappyCase_EvaluatesExpression() {
        mathTool = MathTool.Factory.getInstance().create(null);
        Map<String, String> params = new HashMap<>();
        params.put("input", "2 + 2");
        Mockito.when(scriptService.compile(Mockito.any(), Mockito.any())).thenReturn(templateScriptFactory);
        Mockito.when(templateScriptFactory.newInstance(Mockito.anyMap())).thenReturn(templateScript);
        Mockito.when(templateScript.execute()).thenReturn("4");

        mathTool.run(params, listener);

        Mockito.verify(scriptService, Mockito.times(1)).compile(scriptArgumentCaptor.capture(), Mockito.eq(TemplateScript.CONTEXT));
        Mockito.verify(templateScript, Mockito.times(1)).execute();
        Assert.assertEquals("2 + 2+ \"\"", scriptArgumentCaptor.getValue().getIdOrCode());
        Mockito.verify(listener).onResponse("4");
    }

    @Test
    public void test_WithRegex_EvaluatesExpression() {
        mathTool = MathTool.Factory.getInstance().create(null);
        Map<String, String> params = new HashMap<>();
        params.put("input", "2/0.5");
        Mockito.when(scriptService.compile(Mockito.any(), Mockito.any())).thenReturn(templateScriptFactory);
        Mockito.when(templateScriptFactory.newInstance(Mockito.anyMap())).thenReturn(templateScript);

        mathTool.run(params, listener);

        Mockito.verify(scriptService, Mockito.times(1)).compile(scriptArgumentCaptor.capture(), Mockito.eq(TemplateScript.CONTEXT));
        Mockito.verify(templateScript, Mockito.times(1)).execute();
        Assert.assertEquals("2.0/0.5+ \"\"", scriptArgumentCaptor.getValue().getIdOrCode());
    }

    @Test
    public void test_Validate_ReturnsTrueWhenNoException() {
        mathTool = MathTool.Factory.getInstance().create(null);
        Assert.assertTrue(mathTool.validate(new HashMap<>()));
    }

}
