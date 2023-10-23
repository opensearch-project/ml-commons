/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.memory;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.client.Client;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.ml.common.spi.memory.Memory;
import org.opensearch.ml.common.spi.memory.Message;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.SortOrder;

import java.io.IOException;

@Log4j2
public class ConversationIndexMemory implements Memory {
    public static final String TYPE = "conversation_index";
    @Getter
    protected String indexName;
    protected boolean retrieveFinalAnswer = true;
    protected final Client client;

    public ConversationIndexMemory(Client client) {
        this.client = client;
        this.indexName = "my_sessions";
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public void save(String id, Message message) {
        this.save(id, message, ActionListener.wrap(r -> {
            log.info("saved message into {} memory, session id: {}", TYPE, id);
        }, e-> {
            log.error("Failed to save message to memory", e);
        }));
    }

    @Override
    public void save(String id, Message message, ActionListener listener) {
        IndexRequest indexRequest = new IndexRequest(indexName);
        try {
            ConversationIndexMessage conversationIndexMessage = (ConversationIndexMessage)message;
            XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
            conversationIndexMessage.toXContent(builder, ToXContent.EMPTY_PARAMS);
            indexRequest.source(builder);
            client.index(indexRequest, listener);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void getMessages(String id, ActionListener listener) {
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.indices(indexName);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.size(10000);
        QueryBuilder sessionIdQueryBuilder = new TermQueryBuilder("session_id", id);

        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        boolQueryBuilder.must(sessionIdQueryBuilder);

        if (retrieveFinalAnswer) {
            QueryBuilder finalAnswerQueryBuilder = new TermQueryBuilder("final_answer", true);
            boolQueryBuilder.must(finalAnswerQueryBuilder);
        }

        sourceBuilder.query(boolQueryBuilder);
        sourceBuilder.sort("created_time", SortOrder.ASC);
        searchRequest.source(sourceBuilder);
        client.search(searchRequest, listener);
    }

    @Override
    public void clear() {
    }

    @Override
    public void remove(String id) {
    }

}
