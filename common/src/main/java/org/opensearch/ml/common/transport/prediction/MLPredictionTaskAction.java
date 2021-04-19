/*
 *  Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License").
 *  You may not use this file except in compliance with the License.
 *  A copy of the License is located at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  or in the "license" file accompanying this file. This file is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package org.opensearch.ml.common.transport.prediction;

import org.opensearch.action.ActionType;

public class MLPredictionTaskAction extends ActionType<MLPredictionTaskResponse> {
    public static final MLPredictionTaskAction INSTANCE = new MLPredictionTaskAction();
    public static final String NAME = "cluster:admin/opensearch-ml/predict";

    private MLPredictionTaskAction() {
        super(NAME, MLPredictionTaskResponse::new);
    }
}
