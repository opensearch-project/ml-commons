/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.config;

import org.opensearch.action.ActionType;

public class MLConfigGetAction extends ActionType<MLConfigGetResponse> {
    public static final MLConfigGetAction INSTANCE = new MLConfigGetAction();
    public static final String NAME = "cluster:admin/opensearch/ml/config/get";

    private MLConfigGetAction() { super(NAME, MLConfigGetResponse::new);}

}
