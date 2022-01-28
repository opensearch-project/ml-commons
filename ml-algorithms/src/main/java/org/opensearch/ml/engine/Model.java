/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine;

import lombok.Data;

//TODO: remove this class, use MLModel
@Data
public class Model {
    String name;
    int version;
    byte[] content;
}
