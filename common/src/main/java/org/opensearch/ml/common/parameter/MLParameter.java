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

package org.opensearch.ml.common.parameter;

import java.io.IOException;
import java.util.Objects;

import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

/**
 * Parameter class for ML algorithms. This class doesn't check the the type of value. Instead, it will be only initialized
 * by MLParameterBuilder class which has all supported types of value.
 */
@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class MLParameter implements Writeable {
    String name;
    Object value;

    MLParameter(String name, Object value) {
        if(Objects.isNull(name) || Objects.isNull(value)) {
            throw new IllegalArgumentException("name or value can't be null");
        }

        this.name = name;
        this.value = value;
    }

    public MLParameter(StreamInput input) throws IOException {
        this.name = input.readString();
        this.value = input.readGenericValue();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(name);
        out.writeGenericValue(value);
    }
}
