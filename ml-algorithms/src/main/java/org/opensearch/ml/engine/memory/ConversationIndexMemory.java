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
import org.opensearch.core.common.Strings;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.ml.common.spi.memory.Memory;
import org.opensearch.ml.common.spi.memory.Message;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.memory.action.conversation.CreateConversationResponse;
import org.opensearch.ml.memory.action.conversation.CreateInteractionResponse;
import org.opensearch.ml.repackage.com.google.common.collect.ImmutableMap;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.SortOrder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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
    private MLMemoryManager memoryManager;

    public ConversationIndexMemory(Client client, MLIndicesHandler mlIndicesHandler, String memoryMetaIndexName, String memoryMessageIndexName, String conversationId, MLMemoryManager memoryManager) {
        this.client = client;
        this.mlIndicesHandler = mlIndicesHandler;
        this.memoryMetaIndexName = memoryMetaIndexName;
        this.memoryMessageIndexName = memoryMessageIndexName;
        this.conversationId = conversationId;
        this.memoryManager = memoryManager;
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

    public void save(Message message, String parentId, Integer traceNum) {
        this.save(message, parentId, traceNum, ActionListener.<CreateInteractionResponse>wrap(r -> {
            log.info("saved message into memory {}, parent id: {}, trace number: {}, interaction id: {}", conversationId, parentId, traceNum, r.getId());
        }, e-> {
            log.error("Failed to save interaction", e);
        }));
    }

    public void save(Message message, String parentId, Integer traceNum, ActionListener listener) {
        ConversationIndexMessage msg = (ConversationIndexMessage) message;
        memoryManager.createInteraction(conversationId, msg.getQuestion(), null, msg.getResponse(), null, null, parentId, traceNum, listener);
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

    public void getMessages(ActionListener listener) {
        memoryManager.getFinalInteractions(conversationId, 10, listener);
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
        private MLMemoryManager memoryManager;

        public void init(Client client, MLIndicesHandler mlIndicesHandler, MLMemoryManager memoryManager) {
            this.client = client;
            this.mlIndicesHandler = mlIndicesHandler;
            this.memoryManager = memoryManager;
        }

        @Override
        public void create(Map<String, Object> map, ActionListener<ConversationIndexMemory> listener) {
            if (map == null || map.isEmpty()) {
                listener.onFailure(new IllegalArgumentException("Invalid input parameter for creating ConversationIndexMemory"));
                return;
            }

//            if (map.containsKey("memory_id")) {
//                create((String) map.get("memory_id"), listener);
//            } else {
//                memoryManager.createConversation((String) map.get("conversation_name"), "OLLY", ActionListener.<CreateConversationResponse>wrap(r -> {
//                    create(r.getId(), listener);
//                    log.info("Created conversation on memory layer, conversation id: {}", r.getId());
//                }, e-> {
//                    log.error("Failed to save interaction", e);
//                    listener.onFailure(e);
//                }));
//            }

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
                    listener.onResponse(new ConversationIndexMemory(client, mlIndicesHandler, memoryMetaIndexName, memoryMessageIndexName, r.getId(), null));
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
                            listener.onResponse(new ConversationIndexMemory(client, mlIndicesHandler, memoryMetaIndexName, memoryMessageIndexName, r.getId(), null));
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
        }

        public void create(String name, String memoryId, String appType, ActionListener<ConversationIndexMemory> listener) {
            if (Strings.isEmpty(memoryId)) {
                memoryManager.createConversation(name, appType, ActionListener.<CreateConversationResponse>wrap(r -> {
                    create(r.getId(), listener);
                    log.info("Created conversation on memory layer, conversation id: {}", r.getId());
                }, e -> {
                    log.error("Failed to save interaction", e);
                    listener.onFailure(e);
                }));
            } else {
                create(memoryId, listener);
            }
        }

        public void create(String memoryId, ActionListener<ConversationIndexMemory> listener) {
            listener.onResponse(new ConversationIndexMemory(client, mlIndicesHandler, memoryMetaIndexName, memoryMessageIndexName, memoryId, memoryManager));
        }
    }
}
