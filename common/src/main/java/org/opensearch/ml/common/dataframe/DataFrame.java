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

import org.opensearch.common.io.stream.Writeable;
import org.opensearch.common.xcontent.ToXContentObject;

/**
 * This is data interface used for data exchange between client plugins and ml plugins. Currently, only row based interface are provided,
 * since OpenSearch is using row similar based document to manage data.
 */
public interface DataFrame extends Iterable<Row>, Writeable, ToXContentObject {
    /**
     * Add a new row given values array.
     * @param values input values
     */
    void appendRow(Object[] values);

    /**
     * Add a new row given a Row data
     * @param row input row data
     */
    void appendRow(Row row);

    /**
     * Get Row data given index value
     * @param index index value
     * @return row data
     */
    Row getRow(int index);

    /**
     * Get the size of the data frame. This is the row size actually.
     * @return the size
     */
    int size();

    /**
     * Get the array of column meta
     * @return array of ColumnMeta
     */
    ColumnMeta[] columnMetas();

    /**
     * Build a new DataFrame by removing one column based on index
     * @param columnIndex the index of column
     * @return new DataFrame
     */
    DataFrame remove(int columnIndex);

    /**
     * Build a new DataFrame given the input columns
     * @param columns the indices of column
     * @return new DataFrame
     */
    DataFrame select(int[] columns);

}
