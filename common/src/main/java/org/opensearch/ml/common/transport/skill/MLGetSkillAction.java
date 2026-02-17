/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.skill;

import org.opensearch.action.ActionType;

public class MLGetSkillAction extends ActionType<MLGetSkillResponse> {
    public static final MLGetSkillAction INSTANCE = new MLGetSkillAction();
    public static final String NAME = "cluster:admin/opensearch/ml/skills/get";

    private MLGetSkillAction() {
        super(NAME, MLGetSkillResponse::new);
    }
}
