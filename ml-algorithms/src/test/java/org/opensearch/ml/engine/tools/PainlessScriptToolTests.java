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
import org.opensearch.client.Client;
import org.opensearch.core.action.ActionListener;
import org.opensearch.script.Script;
import org.opensearch.script.ScriptService;
import org.opensearch.script.TemplateScript;

public class PainlessScriptToolTests {

    @Mock
    private Client client;
    @Mock
    private ScriptService scriptService;

    @Mock
    private TemplateScript templateScript;

    @Mock
    private TemplateScript.Factory templateScriptFactory;

    @Mock
    private ActionListener<String> listener;

    private PainlessScriptTool painlessScriptTool;

    @Captor
    private ArgumentCaptor<Script> scriptArgumentCaptor;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        PainlessScriptTool.Factory.getInstance().init(client, scriptService);
        Mockito.when(scriptService.compile(Mockito.any(), Mockito.any())).thenReturn(templateScriptFactory);
        Mockito.when(templateScriptFactory.newInstance(Mockito.anyMap())).thenReturn(templateScript);
        Mockito.when(templateScript.execute()).thenReturn("4");

        painlessScriptTool = PainlessScriptTool.Factory.getInstance().create(new HashMap<>());
    }

    @Test
    public void test_HappyCase_ExecutesScript() {
        Map<String, String> params = new HashMap<>();
        params.put("script", "2 + 2");
        params.put("script_params", "{}");

        painlessScriptTool.run(params, listener);

        Mockito.verify(scriptService, Mockito.times(1)).compile(scriptArgumentCaptor.capture(), Mockito.eq(TemplateScript.CONTEXT));
        Mockito.verify(templateScript, Mockito.times(1)).execute();
        Assert.assertEquals("2 + 2", scriptArgumentCaptor.getValue().getIdOrCode());
        Mockito.verify(listener).onResponse("4");
    }

    @Test
    public void test_Validate_ReturnsTrue() {
        Map<String, String> params = new HashMap<>();
        params.put("script", "2 + 2");

        Assert.assertTrue(painlessScriptTool.validate(params));
    }

    @Test
    public void test_Validate_ReturnsFalseWhenParamsNull() {
        Assert.assertFalse(painlessScriptTool.validate(null));
    }

    @Test
    public void test_Validate_ReturnsFalseWhenParamsEmpty() {
        Assert.assertFalse(painlessScriptTool.validate(new HashMap<>()));
    }
}
