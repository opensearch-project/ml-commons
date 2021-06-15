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

package org.opensearch.ml.engine;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MLEngineMetaData {
    private List<MLAlgoMetaData> algoMetaDataList = new ArrayList<>();

    public void addAlgoMetaData(MLAlgoMetaData metaData) {
        algoMetaDataList.add(metaData);
    }
}
