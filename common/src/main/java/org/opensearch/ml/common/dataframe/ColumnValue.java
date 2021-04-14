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

package org.opensearch.ml.common.dataframe;

import org.opensearch.common.io.stream.Writeable;

public interface ColumnValue extends Writeable {
    ColumnType columnType();

    Object getValue();

    default int intValue() {
        throw new RuntimeException("the value isn't Integer type");
    }

    default String stringValue() {
        throw new RuntimeException("the value isn't String type");
    }

    default double doubleValue() {
        throw new RuntimeException("the value isn't Double type");
    }

    default boolean booleanValue() {
        throw new RuntimeException("the value isn't Boolean type");
    }

}
