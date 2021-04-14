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

import java.util.Objects;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ColumnValueBuilder {

    /**
     * build column value based on input object
     * @param object input object
     * @return ColumnValue
     */
    public ColumnValue build(Object object) {
        if(Objects.isNull(object)) {
            return new NullValue();
        }

        if(object instanceof Integer) {
            return new IntValue((Integer)object);
        }

        if(object instanceof String) {
            return new StringValue((String)object);
        }

        if(object instanceof Double) {
            return new DoubleValue((Double)object);
        }

        if(object instanceof Boolean) {
            return new BooleanValue((Boolean)object);
        }

        throw new IllegalArgumentException("unsupported type:" + object.getClass().getName());
    }
}
