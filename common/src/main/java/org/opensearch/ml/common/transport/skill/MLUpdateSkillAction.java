/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.skill;

import org.opensearch.action.ActionType;
import org.opensearch.action.update.UpdateResponse;

public class MLUpdateSkillAction extends ActionType<UpdateResponse> {

    public static final MLUpdateSkillAction INSTANCE = new MLUpdateSkillAction();
    public static final String NAME = "cluster:admin/opensearch/ml/skills/update";

    private MLUpdateSkillAction() {
        super(NAME, UpdateResponse::new);
    }
}
