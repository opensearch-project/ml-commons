/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.skill;

import org.opensearch.action.ActionType;

public class MLCreateSkillAction extends ActionType<MLCreateSkillResponse> {
    public static final MLCreateSkillAction INSTANCE = new MLCreateSkillAction();
    public static final String NAME = "cluster:admin/opensearch/ml/skills/create";

    private MLCreateSkillAction() {
        super(NAME, MLCreateSkillResponse::new);
    }
}
