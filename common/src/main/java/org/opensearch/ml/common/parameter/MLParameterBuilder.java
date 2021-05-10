/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.common.parameter;

import lombok.experimental.UtilityClass;

/**
 * Builder class for MLParameter. All MLParameter objects should be created by this utility.
 */
@UtilityClass
public class MLParameterBuilder {

    public MLParameter parameter(String name, int value) {
        return new MLParameter(name, (Integer) value);
    }

    public MLParameter parameter(String name, int[] value) {
        return new MLParameter(name, value);
    }

    public MLParameter parameter(String name, String value) {
        return new MLParameter(name, value);
    }

    public MLParameter parameter(String name, String[] value) {
        return new MLParameter(name, value);
    }

    public MLParameter parameter(String name, double value) {
        return new MLParameter(name, (Double)value);
    }

    public MLParameter parameter(String name, double[] value) {
        return new MLParameter(name, value);
    }

    public MLParameter parameter(String name, boolean value) {
        return new MLParameter(name, (Boolean)value);
    }

    public MLParameter parameter(String name, boolean[] value) {
        return new MLParameter(name, value);
    }

    public MLParameter parameter(String name, long value) {
        return new MLParameter(name, (Long) value);
    }
}
