/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.spi.tools.Tool;

public class WriteToScratchPadToolTests {

    private WriteToScratchPadTool tool;

    @Mock
    private ActionListener<String> listener;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        tool = new WriteToScratchPadTool();
    }

    @Test
    public void testGetType() {
        assertEquals(WriteToScratchPadTool.TYPE, tool.getType());
    }

    @Test
    public void testGetVersion() {
        assertEquals("1", tool.getVersion());
    }

    @Test
    public void testGetName() {
        assertEquals(WriteToScratchPadTool.TYPE, tool.getName());
    }

    @Test
    public void testGetDescription() {
        assertNotNull(tool.getDescription());
    }

    @Test
    public void testGetAttributes() {
        assertNotNull(tool.getAttributes());
        assertTrue((Boolean) tool.getAttributes().get(WriteToScratchPadTool.STRICT_FIELD));
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
        Map<String, String> params = new HashMap<>();
        params.put(WriteToScratchPadTool.NOTES_KEY, "some notes");
        assertTrue(tool.validate(params));

        Map<String, String> invalidParams = new HashMap<>();
        assertFalse(tool.validate(invalidParams));
        assertFalse(tool.validate(null));
    }

    @Test
    public void testRun_Success_NoExistingNotes() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(WriteToScratchPadTool.NOTES_KEY, "new note");
        tool.run((Map) parameters, listener);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(listener).onResponse(captor.capture());
        assertEquals("Wrote to scratchpad: new note", captor.getValue());
        assertEquals(Arrays.asList("new note"), parameters.get(WriteToScratchPadTool.SCRATCHPAD_NOTES_KEY));
    }

    @Test
    public void testRun_Success_WithExistingNotes() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(WriteToScratchPadTool.NOTES_KEY, "new note");
        parameters.put(WriteToScratchPadTool.SCRATCHPAD_NOTES_KEY, new ArrayList<>(Arrays.asList("existing note")));
        tool.run((Map) parameters, listener);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(listener).onResponse(captor.capture());
        assertEquals("Wrote to scratchpad: new note", captor.getValue());
        assertEquals(Arrays.asList("existing note", "new note"), parameters.get(WriteToScratchPadTool.SCRATCHPAD_NOTES_KEY));
    }

    @Test
    public void testRun_Failure_NoNotes() {
        Map<String, Object> parameters = new HashMap<>();
        tool.run((Map) parameters, listener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof IllegalArgumentException);
        assertEquals("Parameter 'notes' is required for WriteToScratchPadTool.", captor.getValue().getMessage());
    }

    @Test
    public void testRun_Failure_EmptyNotes() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(WriteToScratchPadTool.NOTES_KEY, "");
        tool.run((Map) parameters, listener);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(captor.capture());
        assertTrue(captor.getValue() instanceof IllegalArgumentException);
        assertEquals("Parameter 'notes' is required for WriteToScratchPadTool.", captor.getValue().getMessage());
    }

    @Test
    public void testRun_Success_ReturnHistory_WithExistingNotes() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(WriteToScratchPadTool.NOTES_KEY, "new note");
        parameters.put(WriteToScratchPadTool.SCRATCHPAD_NOTES_KEY, new ArrayList<>(Arrays.asList("existing note")));
        parameters.put(WriteToScratchPadTool.RETURN_HISTORY_KEY, "true");
        tool.run((Map) parameters, listener);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(listener).onResponse(captor.capture());
        assertEquals("Scratchpad updated. Full content:\n- existing note\n- new note", captor.getValue());
        assertEquals(Arrays.asList("existing note", "new note"), parameters.get(WriteToScratchPadTool.SCRATCHPAD_NOTES_KEY));
    }

    @Test
    public void testRun_Success_ReturnHistory_NoExistingNotes() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(WriteToScratchPadTool.NOTES_KEY, "new note");
        parameters.put(WriteToScratchPadTool.RETURN_HISTORY_KEY, "true");
        tool.run((Map) parameters, listener);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(listener).onResponse(captor.capture());
        assertEquals("Scratchpad updated. Full content:\n- new note", captor.getValue());
        assertEquals(Arrays.asList("new note"), parameters.get(WriteToScratchPadTool.SCRATCHPAD_NOTES_KEY));
    }

    @Test
    public void testRun_Success_NonListScratchpadNotes() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(WriteToScratchPadTool.NOTES_KEY, "new note");
        parameters.put(WriteToScratchPadTool.SCRATCHPAD_NOTES_KEY, "not a list");
        tool.run((Map) parameters, listener);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(listener).onResponse(captor.capture());
        assertEquals("Wrote to scratchpad: new note", captor.getValue());
        assertEquals(Arrays.asList("new note"), parameters.get(WriteToScratchPadTool.SCRATCHPAD_NOTES_KEY));
    }

    @Test
    public void testRun_Success_WithJsonStringNotes() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(WriteToScratchPadTool.NOTES_KEY, "new note");
        parameters.put(WriteToScratchPadTool.SCRATCHPAD_NOTES_KEY, "[\"existing note\"]");
        tool.run((Map) parameters, listener);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(listener).onResponse(captor.capture());
        assertEquals("Wrote to scratchpad: new note", captor.getValue());
        assertEquals(Arrays.asList("existing note", "new note"), parameters.get(WriteToScratchPadTool.SCRATCHPAD_NOTES_KEY));
    }

    @Test
    public void testRun_Success_ReturnHistory_False() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(WriteToScratchPadTool.NOTES_KEY, "new note");
        parameters.put(WriteToScratchPadTool.RETURN_HISTORY_KEY, "false");
        tool.run((Map) parameters, listener);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(listener).onResponse(captor.capture());
        assertEquals("Wrote to scratchpad: new note", captor.getValue());
    }

    @Test
    public void testFactory() {
        WriteToScratchPadTool.Factory factory = WriteToScratchPadTool.Factory.getInstance();
        WriteToScratchPadTool.Factory factory2 = WriteToScratchPadTool.Factory.getInstance();
        assertEquals(factory, factory2); // Test singleton

        factory.init();
        Tool tool = factory.create(new HashMap<>());
        assertNotNull(tool);
        assertEquals(WriteToScratchPadTool.TYPE, tool.getType());
        assertEquals(factory.getDefaultDescription(), tool.getDescription());
        assertEquals(factory.getDefaultType(), tool.getType());
        assertEquals(factory.getDefaultVersion(), tool.getVersion());
        assertNotNull(factory.getDefaultAttributes());
        assertTrue((Boolean) factory.getDefaultAttributes().get(WriteToScratchPadTool.STRICT_FIELD));
    }
}
