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
