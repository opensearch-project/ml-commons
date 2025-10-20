/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.processor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class AbstractMLProcessorTest {

    @Test
    public void testConstructorStoresConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("key", "value");

        TestProcessor processor = new TestProcessor(config);

        assertNotNull(processor.getConfig());
        assertEquals("value", processor.getConfig().get("key"));
    }

    @Test
    public void testValidateConfigCalledInConstructor() {
        Map<String, Object> config = new HashMap<>();
        config.put("validate_marker", "initial");

        TestProcessor processor = new TestProcessor(config);

        // validateConfig should have been called and modified the config
        assertEquals("validated", processor.getConfig().get("validate_marker"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidationFailureThrowsException() {
        Map<String, Object> config = new HashMap<>();
        config.put("invalid", true);

        new TestProcessorWithValidation(config);
    }

    @Test
    public void testValidationSuccessWithValidConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("required_param", "value");

        TestProcessorWithValidation processor = new TestProcessorWithValidation(config);

        assertNotNull(processor);
        assertEquals("value", processor.getConfig().get("required_param"));
    }

    @Test
    public void testDefaultValidateConfigDoesNothing() {
        Map<String, Object> config = new HashMap<>();

        // Should not throw exception even with empty config
        TestProcessorNoValidation processor = new TestProcessorNoValidation(config);

        assertNotNull(processor);
    }

    @Test
    public void testConfigIsImmutableReference() {
        Map<String, Object> config = new HashMap<>();
        config.put("original", "value");

        TestProcessor processor = new TestProcessor(config);

        // Modifying original map should not affect processor's config reference
        config.put("new", "value");

        // Processor should still have reference to the same map object
        // (Note: The map itself is not immutable, but the reference is final)
        assertTrue(processor.getConfig().containsKey("new"));
    }

    @Test
    public void testProcessMethodMustBeImplemented() {
        Map<String, Object> config = new HashMap<>();
        TestProcessor processor = new TestProcessor(config);

        String input = "test";
        Object result = processor.process(input);

        // Our test implementation just returns the input
        assertEquals(input, result);
    }

    @Test
    public void testMultipleProcessorsWithDifferentConfigs() {
        Map<String, Object> config1 = new HashMap<>();
        config1.put("id", "processor1");

        Map<String, Object> config2 = new HashMap<>();
        config2.put("id", "processor2");

        TestProcessor processor1 = new TestProcessor(config1);
        TestProcessor processor2 = new TestProcessor(config2);

        assertEquals("processor1", processor1.getConfig().get("id"));
        assertEquals("processor2", processor2.getConfig().get("id"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidationWithNullRequiredParam() {
        Map<String, Object> config = new HashMap<>();
        config.put("required_param", null);

        new TestProcessorWithStrictValidation(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidationWithEmptyStringParam() {
        Map<String, Object> config = new HashMap<>();
        config.put("required_param", "");

        new TestProcessorWithStrictValidation(config);
    }

    @Test
    public void testValidationWithWhitespaceParam() {
        Map<String, Object> config = new HashMap<>();
        config.put("required_param", "   ");

        // Should throw because whitespace-only is invalid
        try {
            new TestProcessorWithStrictValidation(config);
            assertTrue("Should have thrown IllegalArgumentException", false);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("cannot be empty"));
        }
    }

    // Test processor implementations

    /**
     * Simple test processor that tracks if validateConfig was called
     */
    private static class TestProcessor extends AbstractMLProcessor {

        public TestProcessor(Map<String, Object> config) {
            super(config);
        }

        @Override
        protected void validateConfig() {
            // Modify the config to prove validateConfig was called
            if (config.containsKey("validate_marker")) {
                config.put("validate_marker", "validated");
            }
        }

        @Override
        public Object process(Object input) {
            return input;
        }

        public Map<String, Object> getConfig() {
            return config;
        }
    }

    /**
     * Test processor with validation logic
     */
    private static class TestProcessorWithValidation extends AbstractMLProcessor {

        public TestProcessorWithValidation(Map<String, Object> config) {
            super(config);
        }

        @Override
        protected void validateConfig() {
            if (config.containsKey("invalid") && (Boolean) config.get("invalid")) {
                throw new IllegalArgumentException("Invalid configuration");
            }
            if (!config.containsKey("required_param")) {
                throw new IllegalArgumentException("'required_param' is required");
            }
        }

        @Override
        public Object process(Object input) {
            return input;
        }

        public Map<String, Object> getConfig() {
            return config;
        }
    }

    /**
     * Test processor with no validation override
     */
    private static class TestProcessorNoValidation extends AbstractMLProcessor {

        public TestProcessorNoValidation(Map<String, Object> config) {
            super(config);
        }

        @Override
        public Object process(Object input) {
            return input;
        }
    }

    /**
     * Test processor with strict validation
     */
    private static class TestProcessorWithStrictValidation extends AbstractMLProcessor {

        public TestProcessorWithStrictValidation(Map<String, Object> config) {
            super(config);
        }

        @Override
        protected void validateConfig() {
            if (!config.containsKey("required_param")) {
                throw new IllegalArgumentException("'required_param' is required");
            }
            Object value = config.get("required_param");
            if (value == null) {
                throw new IllegalArgumentException("'required_param' cannot be null");
            }
            if (value instanceof String) {
                String strValue = (String) value;
                if (strValue.trim().isEmpty()) {
                    throw new IllegalArgumentException("'required_param' cannot be empty");
                }
            }
        }

        @Override
        public Object process(Object input) {
            return input;
        }
    }
}
