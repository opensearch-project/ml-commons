/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.action.ActionListener;

public class AGUIFrontendToolTest {

    @Mock
    private ActionListener<String> listener;

    private AGUIFrontendTool frontendTool;
    private Map<String, Object> toolAttributes;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

        toolAttributes = new HashMap<>();
        toolAttributes.put("key1", "value1");
        toolAttributes.put("key2", 123);

        frontendTool = new AGUIFrontendTool("testFrontendTool", "A test frontend tool", toolAttributes);
    }

    @Test
    public void testGetName() {
        assertEquals("testFrontendTool", frontendTool.getName());
    }

    @Test
    public void testGetDescription() {
        assertEquals("A test frontend tool", frontendTool.getDescription());
    }

    @Test
    public void testGetAttributes() {
        Map<String, Object> attributes = frontendTool.getAttributes();
        assertNotNull(attributes);
        assertEquals("value1", attributes.get("key1"));
        assertEquals(123, attributes.get("key2"));
    }

    @Test
    public void testGetType() {
        assertEquals("AGUIFrontendTool", frontendTool.getType());
    }

    @Test
    public void testGetVersion() {
        assertEquals("1.0.0", frontendTool.getVersion());
    }

    @Test
    public void testValidate_AlwaysReturnsTrue() {
        Map<String, String> params = new HashMap<>();
        params.put("param1", "value1");

        assertTrue(frontendTool.validate(params));

        // Empty params should also be valid
        assertTrue(frontendTool.validate(new HashMap<>()));

        // Null params should also be valid
        assertTrue(frontendTool.validate(null));
    }

    @Test
    public void testRun_ReturnsErrorMessage() {
        Map<String, String> params = new HashMap<>();
        params.put("param1", "value1");

        frontendTool.run(params, listener);

        ArgumentCaptor<String> resultCaptor = ArgumentCaptor.forClass(String.class);
        verify(listener).onResponse(resultCaptor.capture());

        String result = resultCaptor.getValue();
        assertEquals("Error: Tool 'testFrontendTool' is a frontend tool and should be run in the frontend.", result);
    }

    @Test
    public void testRun_WithEmptyParameters() {
        frontendTool.run(new HashMap<>(), listener);

        ArgumentCaptor<String> resultCaptor = ArgumentCaptor.forClass(String.class);
        verify(listener).onResponse(resultCaptor.capture());

        String result = resultCaptor.getValue();
        assertEquals("Error: Tool 'testFrontendTool' is a frontend tool and should be run in the frontend.", result);
    }

    @Test
    public void testSetName_NoOp() {
        // setName is a no-op, just verify it doesn't throw
        frontendTool.setName("newName");
        // Name should remain unchanged
        assertEquals("testFrontendTool", frontendTool.getName());
    }

    @Test
    public void testSetDescription_NoOp() {
        // setDescription is a no-op, just verify it doesn't throw
        frontendTool.setDescription("newDescription");
        // Description should remain unchanged
        assertEquals("A test frontend tool", frontendTool.getDescription());
    }

    @Test
    public void testSetAttributes_NoOp() {
        Map<String, Object> newAttributes = new HashMap<>();
        newAttributes.put("newKey", "newValue");

        // setAttributes is a no-op, just verify it doesn't throw
        frontendTool.setAttributes(newAttributes);

        // Attributes should remain unchanged
        Map<String, Object> attributes = frontendTool.getAttributes();
        assertEquals("value1", attributes.get("key1"));
        assertTrue(attributes.containsKey("key2"));
    }

    @Test
    public void testConstructor_WithNullAttributes() {
        AGUIFrontendTool tool = new AGUIFrontendTool("tool", "description", null);

        assertEquals("tool", tool.getName());
        assertEquals("description", tool.getDescription());
        assertEquals(null, tool.getAttributes());
    }

    @Test
    public void testConstructor_WithEmptyAttributes() {
        AGUIFrontendTool tool = new AGUIFrontendTool("tool", "description", new HashMap<>());

        Map<String, Object> attributes = tool.getAttributes();
        assertNotNull(attributes);
        assertTrue(attributes.isEmpty());
    }
}
