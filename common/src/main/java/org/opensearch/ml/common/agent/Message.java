/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a message with role and content fields for conversation-style input.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    private String role; // flexible - any role allowed (user, assistant, system, etc.)
    private List<ContentBlock> content;
}