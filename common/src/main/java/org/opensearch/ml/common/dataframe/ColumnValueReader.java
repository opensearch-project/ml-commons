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

import java.io.IOException;

import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.Writeable;

public class ColumnValueReader implements Writeable.Reader<ColumnValue> {
    @Override
    public ColumnValue read(StreamInput in) throws IOException {
        ColumnType columnType = in.readEnum(ColumnType.class);
        switch (columnType){
            case INTEGER:
                return new IntValue(in.readInt());
            case DOUBLE:
                return new DoubleValue(in.readDouble());
            case STRING:
                return new StringValue(in.readString());
            case BOOLEAN:
                return new BooleanValue(in.readBoolean());
            case NULL:
                return new NullValue();
            default:
                throw new IllegalArgumentException("unknown type:" + columnType);

        }
    }
}
