/*
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  The OpenSearch Contributors require contributions made to
 *  this file be licensed under the Apache-2.0 license or a
 *  compatible open source license.
 *
 *  Modifications Copyright OpenSearch Contributors. See
 *  GitHub history for details.
 */

package org.opensearch.ml.common.dataset;

import lombok.Getter;

public enum MLInputDataType {
    SEARCH_QUERY("search_query"),
    DATA_FRAME("data_frame");


    @Getter
    private final String name;

    MLInputDataType(String name) {
        this.name = name;
    }

    public String toString() {
        return name;
    }

    public static MLInputDataType fromString(String name){
        for(MLInputDataType e : MLInputDataType.values()){
            if(e.name.equals(name)) return e;
        }
        return null;
    }
}
