/*
 *  Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License").
 *  You may not use this file except in compliance with the License.
 *  A copy of the License is located at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  or in the "license" file accompanying this file. This file is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package org.opensearch.ml.stats;

import lombok.Getter;

/**
 * Enum containing names of all internal stats which will not be returned
 * in ML stats REST API.
 */
public enum InternalStatNames {
    JVM_HEAP_USAGE("jvm_heap_usage");

    @Getter
    private String name;

    InternalStatNames(String name) {
        this.name = name;
    }
}
