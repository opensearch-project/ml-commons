/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import static org.junit.Assert.*;

import org.junit.Test;
import org.opensearch.ml.common.agent.BedrockConverseModelProvider;

public class ModelProviderFactoryTest {

    @Test
    public void testGetProvider_BedrockConverse() {
        // Arrange
        String providerType = "bedrock/converse";

        // Act
        ModelProvider provider = ModelProviderFactory.getProvider(providerType);

        // Assert
        assertNotNull(provider);
        assertTrue(provider instanceof BedrockConverseModelProvider);
    }

    @Test
    public void testGetProvider_BedrockConverse_CaseInsensitive() {
        // Arrange
        String providerType = "BEDROCK/CONVERSE";

        // Act
        ModelProvider provider = ModelProviderFactory.getProvider(providerType);

        // Assert
        assertNotNull(provider);
        assertTrue(provider instanceof BedrockConverseModelProvider);
    }

    @Test
    public void testGetProvider_BedrockConverse_MixedCase() {
        // Arrange
        String providerType = "Bedrock/Converse";

        // Act
        ModelProvider provider = ModelProviderFactory.getProvider(providerType);

        // Assert
        assertNotNull(provider);
        assertTrue(provider instanceof BedrockConverseModelProvider);
    }

    @Test
    public void testGetProvider_UnsupportedProvider() {
        // Arrange
        String providerType = "unsupported/provider";

        // Act & Assert
        try {
            ModelProviderFactory.getProvider(providerType);
            fail("Should throw IllegalArgumentException for unsupported provider");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Unknown model provider type"));
        }
    }

    @Test
    public void testGetProvider_NullProviderType() {
        // Act & Assert
        try {
            ModelProviderFactory.getProvider(null);
            fail("Should throw IllegalArgumentException for null provider type");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Model provider type cannot be null"));
        }
    }

    @Test
    public void testGetProvider_ReturnsNewInstance() {
        // Arrange
        String providerType = "bedrock/converse";

        // Act
        ModelProvider provider1 = ModelProviderFactory.getProvider(providerType);
        ModelProvider provider2 = ModelProviderFactory.getProvider(providerType);

        // Assert
        assertNotNull(provider1);
        assertNotNull(provider2);
        assertNotSame("Factory should return new instances", provider1, provider2);
    }

    @Test
    public void testGetProvider_VerifyProviderInterface() {
        // Arrange
        String providerType = "bedrock/converse";

        // Act
        ModelProvider provider = ModelProviderFactory.getProvider(providerType);

        // Assert
        assertNotNull(provider);
        assertEquals("bedrock/converse/claude", provider.getLLMInterface());
    }
}
