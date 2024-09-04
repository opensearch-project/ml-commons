/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.batch;

import org.opensearch.action.ActionType;

public class MLBatchIngestionAction extends ActionType<MLBatchIngestionResponse> {
    public static MLBatchIngestionAction INSTANCE = new MLBatchIngestionAction();
    public static final String NAME = "cluster:admin/opensearch/ml/batch_ingestion";

    private MLBatchIngestionAction() {
        super(NAME, MLBatchIngestionResponse::new);
    }

}
