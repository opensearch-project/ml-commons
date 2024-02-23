/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.utils;

import lombok.extern.log4j.Log4j2;
import java.util.Map;

@Log4j2
public class IndexUtils {

    // This setting is for index creation.
    public static final Map<String, Object> INDEX_SETTINGS = Map.of("index.number_of_shards", "1", "index.auto_expand_replicas", "0-1");

    // This setting is for index update only, so only dynamic settings should be contained!
    public static final Map<String, Object> UPDATED_INDEX_SETTINGS = Map.of("index.auto_expand_replicas", "0-1");
}
