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
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.ml.common.dataframe.DataFrameBuilder;

public class MLInputDatasetReader implements Writeable.Reader<MLInputDataset> {
    @Override
    public MLInputDataset read(StreamInput streamInput) throws IOException {
        MLInputDataType inputDataType = streamInput.readEnum(MLInputDataType.class);
        switch (inputDataType) {
            case DATA_FRAME:
                return new DataFrameInputDataset(DataFrameBuilder.load(streamInput));
            case SEARCH_QUERY:
                return new SearchQueryInputDataset(streamInput);
            default:
                throw new IllegalArgumentException("unknown input data type:" + inputDataType);
        }

    }
}
