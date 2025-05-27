/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils;

public class FieldDescriptor {
    private final String value;
    private final boolean required;

    public FieldDescriptor(String value, boolean required) {
        this.value = value;
        this.required = required;
    }

    public String getValue() {
        return value;
    }

    public boolean isRequired() {
        return required;
    }
}
