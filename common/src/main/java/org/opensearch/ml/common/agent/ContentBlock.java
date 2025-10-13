/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import java.io.IOException;

import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a content block that can contain different types of content (text, image, video, document).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContentBlock implements Writeable {
    private ContentType type;
    private String text; // for text content
    private ImageContent image; // for image content
    private VideoContent video; // for video content
    private DocumentContent document; // for document content
    
    /**
     * Constructor for text content block.
     */
    public ContentBlock(String text) {
        this.type = ContentType.TEXT;
        this.text = text;
    }
    
    /**
     * Constructor for image content block.
     */
    public ContentBlock(ImageContent image) {
        this.type = ContentType.IMAGE;
        this.image = image;
    }
    
    /**
     * Constructor for video content block.
     */
    public ContentBlock(VideoContent video) {
        this.type = ContentType.VIDEO;
        this.video = video;
    }
    
    /**
     * Constructor for document content block.
     */
    public ContentBlock(DocumentContent document) {
        this.type = ContentType.DOCUMENT;
        this.document = document;
    }

    // TODO: Add stream and XContent constructors when content classes support them
    // For now, we'll implement a basic version for the POC
    
    @Override
    public void writeTo(StreamOutput out) throws IOException {
        // Basic implementation for POC - only support text content for now
        out.writeString(type != null ? type.name() : ContentType.TEXT.name());
        out.writeOptionalString(text);
        // TODO: Add support for other content types when their classes support serialization
    }
}