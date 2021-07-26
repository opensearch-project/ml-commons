/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.common.transport.search;

import org.opensearch.action.ActionType;

public class SearchTaskAction extends ActionType<SearchTaskResponse> {
    public static final SearchTaskAction INSTANCE = new SearchTaskAction();
    public static final String NAME = "cluster:admin/opensearch-ml/search";

    private SearchTaskAction() {
        super(NAME, SearchTaskResponse::new);
    }
}
