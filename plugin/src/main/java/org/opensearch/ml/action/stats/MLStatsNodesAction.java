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


package org.opensearch.ml.action.stats;

import org.opensearch.ml.constant.CommonValue;
import org.opensearch.action.ActionType;

public class MLStatsNodesAction extends ActionType<MLStatsNodesResponse> {
    // Internal Action which is not used for public facing RestAPIs.
    public static final String NAME = CommonValue.ACTION_PREFIX + "stats/nodes";
    public static final MLStatsNodesAction INSTANCE = new MLStatsNodesAction();

    /**
     * Constructor
     */
    private MLStatsNodesAction() {
        super(NAME, MLStatsNodesResponse::new);
    }
}
