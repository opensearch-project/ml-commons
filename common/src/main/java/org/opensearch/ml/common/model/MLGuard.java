/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import java.util.Map;

import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.transport.client.Client;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Getter
public class MLGuard {
    private NamedXContentRegistry xContentRegistry;
    private Client client;
    private Guardrails guardrails;

    public MLGuard(Guardrails guardrails, NamedXContentRegistry xContentRegistry, Client client) {
        this.xContentRegistry = xContentRegistry;
        this.client = client;
        this.guardrails = guardrails;
        if (this.guardrails != null && this.guardrails.getInputGuardrail() != null) {
            this.guardrails.getInputGuardrail().init(xContentRegistry, client);
        }
        if (this.guardrails != null && this.guardrails.getOutputGuardrail() != null) {
            this.guardrails.getOutputGuardrail().init(xContentRegistry, client);
        }
    }

    public Boolean validate(String input, Type type, Map<String, String> parameters) {
        switch (type) {
            case INPUT: // validate input
                return guardrails.getInputGuardrail() == null ? true : guardrails.getInputGuardrail().validate(input, parameters);
            case OUTPUT: // validate output
                return guardrails.getOutputGuardrail() == null ? true : guardrails.getOutputGuardrail().validate(input, parameters);
            default:
                throw new IllegalArgumentException("Unsupported type to validate for guardrails.");
        }
    }

    public enum Type {
        INPUT,
        OUTPUT
    }
}
