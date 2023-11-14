package org.opensearch.ml.engine.tools;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.script.ScriptService;
import software.amazon.awssdk.utils.ImmutableMap;

import java.util.List;
import java.util.Map;

public class ToolsFactoryTest {
    @Mock
    private Client client;
    @Mock
    private ScriptService scriptService;
    @Mock
    private ClusterService clusterService;
    @Mock
    private NamedXContentRegistry xContentRegistry;
    @Mock
    private Tool firstTool;
    @Mock
    private Tool secondTool;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private ToolsFactory toolsFactory;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        Map<String, Tool> externalTools = ImmutableMap.of("firstTool", firstTool, "secondTool", secondTool);
        toolsFactory = new ToolsFactory(client, scriptService, clusterService, xContentRegistry, externalTools);
    }

    @Test
    public void testGetToolHappyCase() {
        Assert.assertNotNull(toolsFactory.getTool(MLModelTool.TYPE));
    }

    @Test
    public void testGetToolFromExternalTools() {
        Assert.assertNotNull(toolsFactory.getTool("firstTool"));
    }

    @Test
    public void testGetToolInvalidTool() {
        exceptionRule.expect(IllegalArgumentException.class);
        Assert.assertNotNull(toolsFactory.getTool("invalid"));
    }

    @Test
    public void testGetAllTools() {
        List<Tool> tools = toolsFactory.getAllTools();
        Assert.assertEquals(9, tools.size());
        Assert.assertTrue(tools.contains(firstTool));
        Assert.assertTrue(tools.contains(secondTool));
        Assert.assertTrue(tools.stream().anyMatch(tool -> tool instanceof MLModelTool));
    }


}
