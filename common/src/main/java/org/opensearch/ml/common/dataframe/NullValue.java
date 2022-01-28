/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.dataframe;

import java.io.IOException;

import org.opensearch.common.io.stream.StreamOutput;

public class NullValue implements ColumnValue {

    @Override
    public ColumnType columnType() {
        return ColumnType.NULL;
    }

    @Override
    public Object getValue() {
        return null;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeEnum(columnType());
    }
}
