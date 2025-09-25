package org.opensearch.ml.engine.tools;

import static org.junit.Assert.*;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.spi.tools.Tool;

public class ReadFromScratchPadToolTests {

    private ReadFromScratchPadTool tool;

    @Mock
    private ActionListener<String> listener;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        tool = new ReadFromScratchPadTool();
    }

    @Test
    public void testGetType() {
        assertEquals(ReadFromScratchPadTool.TYPE, tool.getType());
    }

    @Test
    public void testGetVersion() {
        assertEquals("1", tool.getVersion());
    }

    @Test
    public void testGetName() {
        assertEquals(ReadFromScratchPadTool.TYPE, tool.getName());
    }

    @Test
    public void testGetDescription() {
        assertNotNull(tool.getDescription());
    }

    @Test
    public void testGetAttributes() {
        assertNotNull(tool.getAttributes());
        assertFalse((Boolean) tool.getAttributes().get(ReadFromScratchPadTool.STRICT_FIELD));
    }

    @Test
    public void testSetters() {
        tool.setName("test name");
        assertEquals("test name", tool.getName());

        tool.setDescription("test description");
        assertEquals("test description", tool.getDescription());

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("test", "value");
        tool.setAttributes(attributes);
        assertEquals(attributes, tool.getAttributes());
    }

    @Test
    public void testValidate() {
        assertTrue(tool.validate(new HashMap<>()));
    }

    @Test
    public void testRun_NoNotes() {
        Map<String, String> parameters = new HashMap<>();
        tool.run(parameters, listener);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(listener).onResponse(captor.capture());
        // Assuming StringUtils.toJson("") returns an empty string
        assertEquals("Notes from scratchpad: ", captor.getValue());
        assertEquals("", parameters.get(ReadFromScratchPadTool.SCRATCHPAD_NOTES_KEY));
    }

    @Test
    public void testRun_WithScratchpadNotes() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(ReadFromScratchPadTool.SCRATCHPAD_NOTES_KEY, "existing notes");
        tool.run(parameters, listener);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(listener).onResponse(captor.capture());
        // Assuming StringUtils.toJson("existing notes") returns "existing notes"
        assertEquals("Notes from scratchpad: existing notes", captor.getValue());
        assertEquals("existing notes", parameters.get(ReadFromScratchPadTool.SCRATCHPAD_NOTES_KEY));
    }

    @Test
    public void testRun_WithPersistentNotes() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(ReadFromScratchPadTool.PERSISTENT_NOTES_KEY, "persistent notes");
        tool.run(parameters, listener);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(listener).onResponse(captor.capture());
        assertEquals("Notes from scratchpad: \npersistent notes", captor.getValue());
        assertEquals("\npersistent notes", parameters.get(ReadFromScratchPadTool.SCRATCHPAD_NOTES_KEY));
    }

    @Test
    public void testRun_WithBothNotes() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(ReadFromScratchPadTool.SCRATCHPAD_NOTES_KEY, "existing notes");
        parameters.put(ReadFromScratchPadTool.PERSISTENT_NOTES_KEY, "persistent notes");
        tool.run(parameters, listener);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(listener).onResponse(captor.capture());
        assertEquals("Notes from scratchpad: existing notes\npersistent notes", captor.getValue());
        assertEquals("existing notes\npersistent notes", parameters.get(ReadFromScratchPadTool.SCRATCHPAD_NOTES_KEY));
    }

    @Test
    public void testRun_WithDuplicatePersistentNotes() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(ReadFromScratchPadTool.SCRATCHPAD_NOTES_KEY, "existing notes with persistent notes");
        parameters.put(ReadFromScratchPadTool.PERSISTENT_NOTES_KEY, "persistent notes");
        tool.run(parameters, listener);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(listener).onResponse(captor.capture());
        assertEquals("Notes from scratchpad: existing notes with persistent notes", captor.getValue());
        assertEquals("existing notes with persistent notes", parameters.get(ReadFromScratchPadTool.SCRATCHPAD_NOTES_KEY));
    }

    @Test
    public void testFactory() {
        ReadFromScratchPadTool.Factory factory = ReadFromScratchPadTool.Factory.getInstance();
        factory.init();
        Tool tool = factory.create(new HashMap<>());
        assertNotNull(tool);
        assertEquals(ReadFromScratchPadTool.TYPE, tool.getType());
        assertEquals(factory.getDefaultDescription(), tool.getDescription());
        assertEquals(factory.getDefaultType(), tool.getType());
        assertEquals(factory.getDefaultVersion(), tool.getVersion());
        assertNotNull(factory.getDefaultAttributes());
    }
}
