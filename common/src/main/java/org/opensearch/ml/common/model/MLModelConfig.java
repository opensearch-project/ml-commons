/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import org.opensearch.common.io.stream.NamedWriteable;
import org.opensearch.common.xcontent.ToXContentObject;

public interface MLModelConfig extends ToXContentObject, NamedWriteable {

    String getModelType();

}
