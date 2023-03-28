/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.parameter;

import org.opensearch.common.io.stream.NamedWriteable;
import org.opensearch.core.xcontent.ToXContentObject;

/**
 * Machine learning algorithms parameter interface.
 * Implement this interface when add a new algorith.
 */
public interface MLAlgoParams extends ToXContentObject, NamedWriteable {

    int getVersion();

}
