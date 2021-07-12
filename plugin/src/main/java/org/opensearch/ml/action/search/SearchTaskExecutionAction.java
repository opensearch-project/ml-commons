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

package org.opensearch.ml.action.search;

import org.opensearch.action.ActionType;
import org.opensearch.ml.common.transport.search.SearchTaskResponse;

public class SearchTaskExecutionAction extends ActionType<SearchTaskResponse> {
    public static SearchTaskExecutionAction INSTANCE = new SearchTaskExecutionAction();
    public static final String NAME = "cluster:admin/opensearch-ml/search/execution";

    public SearchTaskExecutionAction() {
        super(NAME, SearchTaskResponse::new);
    }
}
