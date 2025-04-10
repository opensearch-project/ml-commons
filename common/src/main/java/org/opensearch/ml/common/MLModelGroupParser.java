/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import java.io.IOException;

import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.security.spi.resources.ShareableResourceParser;

/**
 * MLModelGroup parser from xcontent
 */
public class MLModelGroupParser implements ShareableResourceParser<MLModelGroup> {

    @Override
    public MLModelGroup parseXContent(XContentParser xContentParser) throws IOException {
        return MLModelGroup.parse(xContentParser);
    }
}
