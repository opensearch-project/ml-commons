/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.profile;

import org.opensearch.action.ActionType;
import org.opensearch.ml.constant.CommonValue;

public class MLProfileAction extends ActionType<MLProfileResponse> {
    // Internal Action which is not used for public facing RestAPIs.
    public static final String NAME = CommonValue.ACTION_PREFIX + "profile/nodes";
    public static final MLProfileAction INSTANCE = new MLProfileAction();

    /**
     * Constructor
     */
    private MLProfileAction() {
        super(NAME, MLProfileResponse::new);
    }
}
