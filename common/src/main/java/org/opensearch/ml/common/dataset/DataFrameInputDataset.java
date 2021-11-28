/*
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  The OpenSearch Contributors require contributions made to
 *  this file be licensed under the Apache-2.0 license or a
 *  compatible open source license.
 *
 *  Modifications Copyright OpenSearch Contributors. See
 *  GitHub history for details.
 */

package org.opensearch.ml.common.dataset;

import java.io.IOException;

import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.ml.common.dataframe.DataFrame;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import org.opensearch.ml.common.dataframe.DataFrameType;
import org.opensearch.ml.common.dataframe.DefaultDataFrame;

/**
 * DataFrame based input data. Client directly passes the data frame to ML plugin with this.
 */
@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class DataFrameInputDataset extends MLInputDataset {
    DataFrame dataFrame;

    @Builder
    public DataFrameInputDataset(@NonNull DataFrame dataFrame) {
        super(MLInputDataType.DATA_FRAME);
        this.dataFrame = dataFrame;
    }

    public DataFrameInputDataset(StreamInput in) throws IOException {
        super(MLInputDataType.DATA_FRAME);
        DataFrameType dataFrameType = in.readEnum(DataFrameType.class);
        switch (dataFrameType) {
            case DEFAULT:
                this.dataFrame = new DefaultDataFrame(in);
                break;
            default:
                this.dataFrame = null;
                break;
        }
    }

    @Override
    public void writeTo(StreamOutput streamOutput) throws IOException {
        super.writeTo(streamOutput);
        dataFrame.writeTo(streamOutput);
    }
}
