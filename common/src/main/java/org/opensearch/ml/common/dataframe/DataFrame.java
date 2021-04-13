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

/**
 * This is data interface used for data exchange between client plugins and ml plugins. Currently, only row based interface are provided,
 * since OpenSearch is using row similar based document to manage data.
 */
public interface DataFrame extends Iterable<Row>, Writeable {
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
}
