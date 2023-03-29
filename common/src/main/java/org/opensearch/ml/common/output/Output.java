/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.output;

import org.opensearch.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;

public interface Output extends ToXContentObject, Writeable {

}
