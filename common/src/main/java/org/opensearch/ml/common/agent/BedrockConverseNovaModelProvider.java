/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

/**
 * Model provider for Amazon Nova models via Bedrock Converse API.
 *
 * Nova models use the same Bedrock Converse API format as Claude for connector
 * creation, request body templates, content block mapping, and message parsing.
 * This class extends {@link BedrockConverseModelProvider} and overrides only
 * {@link #getLLMInterface()} to return the Nova-specific interface identifier.
 *
 * Supported Nova models include:
 * - amazon.nova-pro-v1:0
 * - amazon.nova-lite-v1:0
 * - amazon.nova-micro-v1:0
 * - amazon.nova-premier-v1:0
 */
public class BedrockConverseNovaModelProvider extends BedrockConverseModelProvider {

    @Override
    public String getLLMInterface() {
        return "bedrock/converse/nova";
    }
}
