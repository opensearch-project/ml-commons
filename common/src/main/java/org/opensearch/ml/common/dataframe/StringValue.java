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

import org.opensearch.common.io.stream.StreamOutput;

import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@ToString
public class StringValue implements ColumnValue {
    @NonNull
    String value;

    @Override
    public ColumnType columnType() {
        return ColumnType.STRING;
    }

    @Override
    public Object getValue() {
        return value;
    }

    @Override
    public String stringValue() {
        return value;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeEnum(columnType());
        out.writeString(value);
    }
}
