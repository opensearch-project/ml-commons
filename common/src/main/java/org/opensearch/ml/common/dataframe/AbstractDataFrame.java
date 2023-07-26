/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.dataframe;

import java.io.IOException;

import org.opensearch.core.common.io.stream.StreamOutput;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Getter
public abstract class AbstractDataFrame implements DataFrame {
    @NonNull
    DataFrameType dataFrameType;

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeEnum(dataFrameType);
    }
}
