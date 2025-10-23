/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents image content with type, format, and data fields.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageContent {
    private SourceType type;
    private String format; // "jpeg", "png", "gif", "webp"
    private String data; // URL or base64 data
}
