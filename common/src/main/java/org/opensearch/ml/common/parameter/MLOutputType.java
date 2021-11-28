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

public enum MLOutputType {
    TRAINING("training"),
    PREDICTION("prediction"),
    SAMPLE_ALGO("sample_algo"),
    SAMPLE_CALCULATOR("sample_calculator");

    private final String name;

    MLOutputType(String name) {
        this.name = name;
    }

    public String toString() {
        return name;
    }

    public static MLOutputType fromString(String name){
        for(MLOutputType e : MLOutputType.values()){
            if(e.name.equals(name)) return e;
        }
        return null;
    }
}