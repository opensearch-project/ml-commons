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

package org.opensearch.ml.common.parameter;

import org.opensearch.common.io.stream.NamedWriteable;
import org.opensearch.common.xcontent.ToXContentObject;

/**
 * Machine learning algorithms parameter interface.
 * Implement this interface when add a new algorith.
 */
public interface MLAlgoParams extends ToXContentObject, NamedWriteable {

    int getVersion();

}
