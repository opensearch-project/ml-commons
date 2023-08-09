/*
 * Copyright Aryn, Inc 2023
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opensearch.ml.conversational.action.memory.interaction;

import java.io.IOException;
import java.util.List;

import org.opensearch.action.ActionResponse;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.ml.conversational.action.ActionConstants;
import org.opensearch.ml.conversational.index.Interaction;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

/**
 * Action Response for get interactions
 */
public class GetInteractionsResponse extends ActionResponse implements ToXContentObject {
    
    private List<Interaction> interactions;
    private int nextToken;
    private boolean hasMoreTokens;

    /**
     * Constructor
     * @param in stream input; assumes GetInteractionsResponse.writeTo was called
     * @throws IOException if theres not a G.I.R. in the stream
     */
    public GetInteractionsResponse(StreamInput in) throws IOException {
        super(in);
        interactions = in.readList(Interaction::fromStream);
        nextToken = in.readInt();
        hasMoreTokens = in.readBoolean();
    }

    /**
     * Constructor
     * @param interactions list of interactions returned by this response
     * @param nextToken token representing the next page of results
     * @param hasMoreTokens whether there are more results after this page
     */
    public GetInteractionsResponse(List<Interaction> interactions, int nextToken, boolean hasMoreTokens) {
        this.interactions = interactions;
        this.nextToken = nextToken;
        this.hasMoreTokens = hasMoreTokens;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeList(interactions);
        out.writeInt(nextToken);
        out.writeBoolean(hasMoreTokens);
    }

    /**
     * Get the list of interactions
     * @return the list of interactions returned by this response
     */
    public List<Interaction> getInteractions() {
        return interactions;
    }

    /**
     * Are there more pages in this search results
     * @return whether there are more pages in this search
     */
    public boolean hasMorePages() {
        return hasMoreTokens;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        builder.startArray(ActionConstants.RESPONSE_INTER_LIST_FIELD);
        for(Interaction inter : interactions ){
            inter.toXContent(builder, params);
        }
        builder.endArray();
        if(hasMoreTokens) {
            builder.field(ActionConstants.NEXT_TOKEN_FIELD, nextToken);
        }
        builder.endObject();
        return builder;
    }
}