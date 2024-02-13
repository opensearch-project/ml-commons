/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.dataset;

// Please strictly add new MLInputDataType to the last line
// and avoid altering the order of the existing MLInputDataType,
// or it will break the backward compatibility!
public enum MLInputDataType {
    SEARCH_QUERY,
    DATA_FRAME,
    TEXT_DOCS,
    REMOTE,
    TEXT_SIMILARITY
}
