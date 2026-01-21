/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.execute.agent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents video content with type, format, and data fields.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoContent {
    private SourceType type;
    private String format; // "mp4", "mov", "avi", etc.
    private String data; // URL or base64 data
}
