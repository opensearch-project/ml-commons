/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.junit.Assert.assertEquals;
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
        assertTrue((Boolean) tool.getAttributes().get(ReadFromScratchPadTool.STRICT_FIELD));
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
        Map<String, Object> parameters = new HashMap<>();
        tool.run((Map) parameters, listener);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(listener).onResponse(captor.capture());
        assertEquals("Scratchpad is empty.", captor.getValue());
        assertEquals(new ArrayList<>(), parameters.get(ReadFromScratchPadTool.SCRATCHPAD_NOTES_KEY));
    }

    @Test
    public void testRun_WithScratchpadNotes() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(ReadFromScratchPadTool.SCRATCHPAD_NOTES_KEY, new ArrayList<>(Arrays.asList("existing note")));
        tool.run((Map) parameters, listener);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(listener).onResponse(captor.capture());
        assertEquals("Notes from scratchpad:\n- existing note", captor.getValue());
        assertEquals(Arrays.asList("existing note"), parameters.get(ReadFromScratchPadTool.SCRATCHPAD_NOTES_KEY));
    }

    @Test
    public void testRun_WithPersistentNotes() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(ReadFromScratchPadTool.SCRATCHPAD_NOTES_KEY, new ArrayList<>()); // Initialize with empty list
        parameters.put(ReadFromScratchPadTool.PERSISTENT_NOTES_KEY, "persistent note");
        tool.run((Map) parameters, listener);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(listener).onResponse(captor.capture());
        assertEquals("Notes from scratchpad:\n- persistent note", captor.getValue());
        assertEquals(Arrays.asList("persistent note"), parameters.get(ReadFromScratchPadTool.SCRATCHPAD_NOTES_KEY));
    }

    @Test
    public void testRun_WithBothNotes() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(ReadFromScratchPadTool.SCRATCHPAD_NOTES_KEY, new ArrayList<>(Arrays.asList("existing note")));
        parameters.put(ReadFromScratchPadTool.PERSISTENT_NOTES_KEY, "persistent note");
        tool.run((Map) parameters, listener);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(listener).onResponse(captor.capture());
        assertEquals("Notes from scratchpad:\n- existing note\n- persistent note", captor.getValue());
        assertEquals(Arrays.asList("existing note", "persistent note"), parameters.get(ReadFromScratchPadTool.SCRATCHPAD_NOTES_KEY));
    }

    @Test
    public void testRun_WithDuplicatePersistentNotes() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(ReadFromScratchPadTool.SCRATCHPAD_NOTES_KEY, new ArrayList<>(Arrays.asList("existing note", "persistent note")));
        parameters.put(ReadFromScratchPadTool.PERSISTENT_NOTES_KEY, "persistent note");
        tool.run((Map) parameters, listener);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(listener).onResponse(captor.capture());
        assertEquals("Notes from scratchpad:\n- existing note\n- persistent note", captor.getValue());
        assertEquals(Arrays.asList("existing note", "persistent note"), parameters.get(ReadFromScratchPadTool.SCRATCHPAD_NOTES_KEY));
    }

    @Test
    public void testRun_WithNonListScratchpadNotes() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(ReadFromScratchPadTool.SCRATCHPAD_NOTES_KEY, "not a list");
        tool.run((Map) parameters, listener);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(listener).onResponse(captor.capture());
        assertEquals("Scratchpad is empty.", captor.getValue());
        assertEquals(new ArrayList<>(), parameters.get(ReadFromScratchPadTool.SCRATCHPAD_NOTES_KEY));
    }

    @Test
    public void testRun_WithJsonStringNotes() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(ReadFromScratchPadTool.SCRATCHPAD_NOTES_KEY, "[\"json note\"]");
        tool.run((Map) parameters, listener);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(listener).onResponse(captor.capture());
        assertEquals("Notes from scratchpad:\n- json note", captor.getValue());
        assertEquals(Arrays.asList("json note"), parameters.get(ReadFromScratchPadTool.SCRATCHPAD_NOTES_KEY));
    }

    @Test
    public void testRun_WithEmptyPersistentNotes() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(ReadFromScratchPadTool.SCRATCHPAD_NOTES_KEY, new ArrayList<>(Arrays.asList("existing note")));
        parameters.put(ReadFromScratchPadTool.PERSISTENT_NOTES_KEY, "");
        tool.run((Map) parameters, listener);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(listener).onResponse(captor.capture());
        assertEquals("Notes from scratchpad:\n- existing note", captor.getValue());
    }

    @Test
    public void testRun_WithNullPersistentNotes() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(ReadFromScratchPadTool.SCRATCHPAD_NOTES_KEY, new ArrayList<>(Arrays.asList("existing note")));
        parameters.put(ReadFromScratchPadTool.PERSISTENT_NOTES_KEY, null);
        tool.run((Map) parameters, listener);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(listener).onResponse(captor.capture());
        assertEquals("Notes from scratchpad:\n- existing note", captor.getValue());
    }

    @Test
    public void testFactory() {
        ReadFromScratchPadTool.Factory factory = ReadFromScratchPadTool.Factory.getInstance();
        ReadFromScratchPadTool.Factory factory2 = ReadFromScratchPadTool.Factory.getInstance();
        assertEquals(factory, factory2); // Test singleton

        factory.init();
        Tool tool = factory.create(new HashMap<>());
        assertNotNull(tool);
        assertEquals(ReadFromScratchPadTool.TYPE, tool.getType());
        assertEquals(factory.getDefaultDescription(), tool.getDescription());
        assertEquals(factory.getDefaultType(), tool.getType());
        assertEquals(factory.getDefaultVersion(), tool.getVersion());
        assertNotNull(factory.getDefaultAttributes());
        assertTrue((Boolean) factory.getDefaultAttributes().get(ReadFromScratchPadTool.STRICT_FIELD));
    }
}
