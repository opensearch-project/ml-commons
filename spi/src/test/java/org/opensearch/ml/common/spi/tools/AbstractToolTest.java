package org.opensearch.ml.common.spi.tools;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

public class AbstractToolTest {

    public static final String TOOL_TYPE = "tool_type";
    public static final String TOOL_NAME = "tool_name";
    public static final String TOOL_DESCRIPTION = "tool_description";
    private AbstractTool abstractTool;

    @Mock
    private Parser mockInputParser;

    @Mock
    private Parser mockOutputParser;

    @Before
    public void setup() {
        abstractTool = Mockito.mock(AbstractTool.class,
                Mockito.withSettings().useConstructor(TOOL_TYPE, TOOL_DESCRIPTION).defaultAnswer(Mockito.CALLS_REAL_METHODS));
    }

    @Test
    public void testConstructorValueIsPersisted() {
        Assert.assertEquals(TOOL_TYPE, abstractTool.getType());
        Assert.assertEquals(TOOL_TYPE, abstractTool.getName());
        Assert.assertEquals(TOOL_DESCRIPTION, abstractTool.getDescription());
    }

    @Test
    public void testGetterSetterName() {
        abstractTool.setName("test_name");
        Assert.assertEquals("test_name", abstractTool.getName());
    }

    @Test
    public void testGetterSetterDescription() {
        abstractTool.setDescription("test_description");
        Assert.assertEquals("test_description", abstractTool.getDescription());
    }


    @Test
    public void testGetterSetterVersion() {
        abstractTool.setVersion("test_version");
        Assert.assertEquals("test_version", abstractTool.getVersion());
    }

    @Test
    public void testGetterSetterInputParser() {
        abstractTool.setInputParser(mockInputParser);
        Assert.assertEquals(mockInputParser, abstractTool.getInputParser());
    }

    @Test
    public void testGetterSetterOutputParser() {
        abstractTool.setOutputParser(mockOutputParser);
        Assert.assertEquals(mockOutputParser, abstractTool.getOutputParser());
    }

    @Test
    public void testConstructor() {
        abstractTool = Mockito.mock(AbstractTool.class,
                Mockito.withSettings().useConstructor(TOOL_TYPE, TOOL_NAME, TOOL_DESCRIPTION).defaultAnswer(Mockito.CALLS_REAL_METHODS));
        Assert.assertEquals(TOOL_TYPE, abstractTool.getType());
        Assert.assertEquals(TOOL_NAME, abstractTool.getName());
        Assert.assertEquals(TOOL_DESCRIPTION, abstractTool.getDescription());
    }

}
