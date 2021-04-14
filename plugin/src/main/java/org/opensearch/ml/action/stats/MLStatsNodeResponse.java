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

import org.opensearch.action.support.nodes.BaseNodeResponse;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.ToXContentFragment;
import org.opensearch.common.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.Map;

public class MLStatsNodeResponse extends BaseNodeResponse implements ToXContentFragment {
    private Map<String, Object> statsMap;

    /**
     * Constructor
     *
     * @param in StreamInput
     * @throws IOException throws an IO exception if the StreamInput cannot be reML from
     */
    public MLStatsNodeResponse(StreamInput in) throws IOException {
        super(in);
        this.statsMap = in.readMap(StreamInput::readString, StreamInput::readGenericValue);
    }

    /**
     * Constructor
     *
     * @param node node
     * @param statsToValues Mapping of stat name to value
     */
    public MLStatsNodeResponse(DiscoveryNode node, Map<String, Object> statsToValues) {
        super(node);
        this.statsMap = statsToValues;
    }

    /**
     * Creates a new MLStatsNodeResponse object and reMLs in the stats from an input stream
     *
     * @param in StreamInput to reML from
     * @return MLStatsNodeResponse object corresponding to the input stream
     * @throws IOException throws an IO exception if the StreamInput cannot be reML from
     */
    public static MLStatsNodeResponse readStats(StreamInput in) throws IOException {

        return new MLStatsNodeResponse(in);
    }

    /**
     * getStatsMap
     *
     * @return map of stats
     */
    public Map<String, Object> getStatsMap() {
        return statsMap;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeMap(statsMap, StreamOutput::writeString, StreamOutput::writeGenericValue);
    }

    /**
     * Converts statsMap to xContent
     *
     * @param builder XContentBuilder
     * @param params Params
     * @return XContentBuilder
     * @throws IOException thrown by builder for invalid field
     */
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        for (String stat : statsMap.keySet()) {
            builder.field(stat, statsMap.get(stat));
        }

        return builder;
    }
}
