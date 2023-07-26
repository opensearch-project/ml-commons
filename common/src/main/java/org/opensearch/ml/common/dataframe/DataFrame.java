/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.dataframe;

import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;

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

    /**
     * Find the index of the target in columnMetas
     * @param target the string value of the target
     * @return column index of the target in the list of columnMetas
     */
    int getColumnIndex(String target);
}
