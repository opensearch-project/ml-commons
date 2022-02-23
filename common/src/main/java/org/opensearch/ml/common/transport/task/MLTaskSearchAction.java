package org.opensearch.ml.common.transport.task;

import org.opensearch.action.ActionType;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.ml.common.transport.model.MLModelSearchAction;

public class MLTaskSearchAction extends ActionType<SearchResponse> {
    // External Action which used for public facing RestAPIs.
    public static final String NAME = "cluster:admin/opensearch/ml/tasks/search";
    public static final MLTaskSearchAction INSTANCE = new MLTaskSearchAction();

    private MLTaskSearchAction() {
        super(NAME, SearchResponse::new);
    }
}
