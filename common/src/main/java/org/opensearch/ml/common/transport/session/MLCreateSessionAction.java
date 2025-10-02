/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.session;

import org.opensearch.action.ActionType;

public class MLCreateSessionAction extends ActionType<MLCreateSessionResponse> {
    public static final MLCreateSessionAction INSTANCE = new MLCreateSessionAction();
    public static final String NAME = "cluster:admin/opensearch/ml/sessions/create";

    private MLCreateSessionAction() {
        super(NAME, MLCreateSessionResponse::new);
    }
}
