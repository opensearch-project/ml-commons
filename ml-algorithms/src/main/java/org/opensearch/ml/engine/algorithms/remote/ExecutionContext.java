/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.engine.algorithms.remote;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * This class encapsulates several parameters that are used in a split-batch request case.
 * A batch request is that in neural-search side multiple fields are send in one request to ml-commons,
 * but the remote model doesn't accept list of string inputs so in ml-commons the request needs split.
 * sequence is used to identify the index of the split request.
 */
@Data
@AllArgsConstructor
public class ExecutionContext {
    // Should never be null
    private int sequence;
}
