/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.dataset;

import java.io.IOException;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.annotation.InputDataSet;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DataFrameType;
import org.opensearch.ml.common.dataframe.DefaultDataFrame;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

/**
 * DataFrame based input data. Client directly passes the data frame to ML plugin with this.
 */
@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@InputDataSet(MLInputDataType.DATA_FRAME)
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
