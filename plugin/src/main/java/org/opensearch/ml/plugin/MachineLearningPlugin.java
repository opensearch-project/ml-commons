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


package org.opensearch.ml.plugin;

import org.opensearch.cluster.node.DiscoveryNodeRole;
import org.opensearch.common.settings.Setting;
import org.opensearch.ml.action.stats.MLStatsNodesAction;
import org.opensearch.ml.action.stats.MLStatsNodesTransportAction;
import org.opensearch.plugins.ActionPlugin;
import org.opensearch.plugins.Plugin;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionResponse;
import com.google.common.collect.ImmutableList;

import java.util.List;

public class MachineLearningPlugin extends Plugin implements ActionPlugin {
    public static final String ML_BASE_URI = "/_opendistro/_ml";

    @Override
    public List<ActionHandler<? extends ActionRequest, ? extends ActionResponse>> getActions() {
        return ImmutableList.of(
                new ActionHandler<>(MLStatsNodesAction.INSTANCE,
                        MLStatsNodesTransportAction.class)
        );
    }
}
