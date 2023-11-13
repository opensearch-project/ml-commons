/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.memory;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.opensearch.action.get.GetRequest;
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
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.SortOrder;
import software.amazon.awssdk.utils.ImmutableMap;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import static org.opensearch.ml.common.CommonValue.ML_MEMORY_MESSAGE_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MEMORY_META_INDEX;
import static org.opensearch.ml.engine.algorithms.agent.MLReActAgentRunner.SESSION_ID;

@Log4j2
@Getter
public class ConversationIndexMemory implements Memory {
    public static final String TYPE = "conversation_index";
    protected String memoryMetaIndexName;
    protected String memoryMessageIndexName;
    protected String conversationId;
    protected boolean retrieveFinalAnswer = true;
    protected final Client client;
    private final MLIndicesHandler mlIndicesHandler;

    public ConversationIndexMemory(Client client, MLIndicesHandler mlIndicesHandler, String memoryMetaIndexName, String memoryMessageIndexName, String conversationId) {
        this.client = client;
        this.mlIndicesHandler = mlIndicesHandler;
        this.memoryMetaIndexName = memoryMetaIndexName;
        this.memoryMessageIndexName = memoryMessageIndexName;
        this.conversationId = conversationId;
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
        mlIndicesHandler.initMemoryMessageIndex(ActionListener.wrap(created -> {
            if (created) {
                IndexRequest indexRequest = new IndexRequest(memoryMessageIndexName);
                ConversationIndexMessage conversationIndexMessage = (ConversationIndexMessage) message;
                XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
                conversationIndexMessage.toXContent(builder, ToXContent.EMPTY_PARAMS);
                indexRequest.source(builder);
                client.index(indexRequest, listener);
            } else {
                listener.onFailure(new RuntimeException("Failed to create memory message index"));
            }
        }, e -> {
            listener.onFailure(new RuntimeException("Failed to create memory message index"));
        }));
    }

    @Override
    public void getMessages(String id, ActionListener listener) {
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.indices(memoryMessageIndexName);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.size(10000);
        QueryBuilder sessionIdQueryBuilder = new TermQueryBuilder(SESSION_ID, id);

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

    public static class Factory implements Memory.Factory<ConversationIndexMemory> {
        private Client client;
        private MLIndicesHandler mlIndicesHandler;
        private String memoryMetaIndexName = ML_MEMORY_META_INDEX;
        private String memoryMessageIndexName = ML_MEMORY_MESSAGE_INDEX;

        public void init(Client client, MLIndicesHandler mlIndicesHandler) {
            this.client = client;
            this.mlIndicesHandler = mlIndicesHandler;
        }

        @Override
        public void create(Map<String, Object> map, ActionListener<ConversationIndexMemory> listener) {
            if (map != null) {
                if (map.containsKey("memory_index_name")) {
                    memoryMetaIndexName = (String) map.get("memory_index_name");
                }
                if (map.containsKey("memory_message_index_name")) {
                    memoryMessageIndexName = (String) map.get("memory_message_index_name");
                }
                if (map.containsKey(SESSION_ID)) {
                    String conversationId = (String) map.get(SESSION_ID);
                    GetRequest getRequest = new GetRequest(memoryMetaIndexName).id(conversationId);
                    client.get(getRequest, ActionListener.wrap(r -> {
                        listener.onResponse(new ConversationIndexMemory(client, mlIndicesHandler, memoryMetaIndexName, memoryMessageIndexName, r.getId()));
                    }, e-> {
                        listener.onFailure(new IllegalArgumentException("Can't find conversation " + conversationId));
                    }));
                } else if (map.containsKey("question")) {
                    String question = (String) map.get("question");
                    mlIndicesHandler.initMemoryMetaIndex(ActionListener.wrap(created -> {
                        if (created) {
                            IndexRequest indexRequest = new IndexRequest(memoryMetaIndexName);
                            indexRequest.source(ImmutableMap.of("name", question, "created_time", Instant.now().toEpochMilli()));
                            client.index(indexRequest, ActionListener.wrap(r-> {
                                listener.onResponse(new ConversationIndexMemory(client, mlIndicesHandler, memoryMetaIndexName, memoryMessageIndexName, r.getId()));
                            }, e-> {
                                listener.onFailure(e);
                            }));
                        } else {
                            listener.onFailure(new RuntimeException("Failed to create memory meta index"));
                        }
                    }, e -> {
                        listener.onFailure(new RuntimeException("Failed to create memory meta index"));
                    }));
                } else {
                    listener.onFailure(new IllegalArgumentException("Invalid input parameter. Must set conversation id or question"));
                }
            } else {
                listener.onFailure(new IllegalArgumentException("Invalid input parameter for creating ConversationIndexMemory"));
            }
        }
    }
}
