/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.execute.agent;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a content block that can contain different types of content (text, image, video, document).
 */
@Data
@NoArgsConstructor
public class ContentBlock {
    private ContentType type;
    private String text; // for text content
    private ImageContent image; // for image content
    private VideoContent video; // for video content
    private DocumentContent document; // for document content
}
