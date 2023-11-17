/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model;

import org.opensearch.action.ActionType;

public class MLInPlaceUpdateModelAction extends ActionType<MLInPlaceUpdateModelNodesResponse> {
    public static final MLInPlaceUpdateModelAction INSTANCE = new MLInPlaceUpdateModelAction();
    public static final String NAME = "cluster:admin/opensearch/ml/models/in_place_update";

    private MLInPlaceUpdateModelAction() { super(NAME, MLInPlaceUpdateModelNodesResponse::new);}
}
