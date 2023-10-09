/*
 * Copyright 2023 Aryn
 * Copyright OpenSearch Contributors
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
package org.opensearch.ml.memory.index;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Ignore;
import org.opensearch.OpenSearchSecurityException;
import org.opensearch.action.LatchedActionListener;
import org.opensearch.action.StepListener;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Client;
import org.opensearch.client.Requests;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.util.concurrent.ThreadContext.StoredContext;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.ml.common.conversation.ConversationMeta;
import org.opensearch.ml.common.conversation.ConversationalIndexConstants;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.OpenSearchIntegTestCase;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;

import lombok.extern.log4j.Log4j2;

@Log4j2
@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.TEST, numDataNodes = 2)
@Ignore
public class ConversationMetaIndexITTests extends OpenSearchIntegTestCase {

    private ClusterService clusterService;
    private Client client;
    private ConversationMetaIndex index;

    private void refreshIndex() {
        client.admin().indices().refresh(Requests.refreshRequest(ConversationalIndexConstants.META_INDEX_NAME));
    }

    private StoredContext setUser(String username) {
        StoredContext stored = client
            .threadPool()
            .getThreadContext()
            .newStoredContext(true, List.of(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT));
        ThreadContext context = client.threadPool().getThreadContext();
        context.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, username + "||");
        return stored;
    }

    @Before
    public void setup() {
        log.info("Setting up test");
        this.client = client();
        this.clusterService = clusterService();
        this.index = new ConversationMetaIndex(client, clusterService);
    }

    /**
     * Can the index be initialized?
     */
    public void testConversationMetaIndexCanBeInitialized() {
        CountDownLatch cdl = new CountDownLatch(1);
        index.initConversationMetaIndexIfAbsent(new LatchedActionListener<Boolean>(ActionListener.wrap(r -> { assert (r); }, e -> {
            log.error(e);
            assert (false);
        }), cdl));
        try {
            cdl.await();
        } catch (InterruptedException e) {
            log.error(e);
        }
    }

    /**
     * If the index tries to be initialized more than once does it break something
     * Also make sure that only one initialization happens
     */
    public void testConversationMetaIndexCanBeInitializedTwice() {
        CountDownLatch cdl = new CountDownLatch(2);
        index.initConversationMetaIndexIfAbsent(new LatchedActionListener<Boolean>(ActionListener.wrap(r -> { assert (r); }, e -> {
            log.error(e);
            assert (false);
        }), cdl));
        index.initConversationMetaIndexIfAbsent(new LatchedActionListener<Boolean>(ActionListener.wrap(r -> { assert (r); }, e -> {
            log.error(e);
            assert (false);
        }), cdl));
        try {
            cdl.await();
        } catch (InterruptedException e) {
            log.error(e);
        }
    }

    /**
     * If the index tries to be initialized by different objects does it break anything
     * Also make sure that only one initialization happens
     */
    public void testConversationMetaIndexCanBeInitializedByDifferentObjects() {
        CountDownLatch cdl = new CountDownLatch(2);
        index.initConversationMetaIndexIfAbsent(new LatchedActionListener<Boolean>(ActionListener.wrap(r -> { assert (r); }, e -> {
            log.error(e);
            assert (false);
        }), cdl));
        ConversationMetaIndex otherIndex = new ConversationMetaIndex(client, clusterService);
        otherIndex.initConversationMetaIndexIfAbsent(new LatchedActionListener<Boolean>(ActionListener.wrap(r -> { assert (r); }, e -> {
            log.error(e);
            assert (false);
        }), cdl));
        try {
            cdl.await();
        } catch (InterruptedException e) {
            log.error(e);
        }
    }

    /**
     * Can I add a new conversation to the index without crashong?
     */
    public void testCanAddNewConversation() {
        CountDownLatch cdl = new CountDownLatch(1);
        index
            .createConversation(new LatchedActionListener<String>(ActionListener.wrap(r -> { assert (r != null && r.length() > 0); }, e -> {
                log.error(e);
                assert (false);
            }), cdl));
        try {
            cdl.await();
        } catch (InterruptedException e) {
            log.error(e);
        }
    }

    /**
     * Are conversation ids unique?
     */
    public void testConversationIDsAreUnique() {
        int numTries = 100;
        CountDownLatch cdl = new CountDownLatch(numTries);
        Set<String> seenIds = Collections.synchronizedSet(new HashSet<String>(numTries));
        for (int i = 0; i < numTries; i++) {
            index.createConversation(new LatchedActionListener<String>(ActionListener.wrap(r -> {
                assert (!seenIds.contains(r));
                seenIds.add(r);
            }, e -> {
                log.error(e);
                assert (false);
            }), cdl));
        }
        try {
            cdl.await();
        } catch (InterruptedException e) {
            log.error(e);
        }
    }

    /**
     * If I add a conversation, that id shows up in the list of conversations
     */
    public void testConversationsCanBeListed() {
        CountDownLatch cdl = new CountDownLatch(1);
        StepListener<String> addConversationListener = new StepListener<>();
        index.createConversation(addConversationListener);

        StepListener<List<ConversationMeta>> listConversationListener = new StepListener<>();
        addConversationListener.whenComplete(cid -> {
            refreshIndex();
            refreshIndex();
            index.getConversations(10, listConversationListener);
        }, e -> {
            cdl.countDown();
            log.error(e);
        });

        LatchedActionListener<List<ConversationMeta>> finishAndAssert = new LatchedActionListener<>(ActionListener.wrap(conversations -> {
            boolean foundConversation = false;
            log.info("FINDME");
            log.info(addConversationListener.result());
            log.info(conversations);
            for (ConversationMeta c : conversations) {
                if (c.getId().equals(addConversationListener.result())) {
                    foundConversation = true;
                }
            }
            assert (foundConversation);
        }, e -> { log.error(e); }), cdl);
        listConversationListener.whenComplete(finishAndAssert::onResponse, finishAndAssert::onFailure);
        try {
            cdl.await();
        } catch (InterruptedException e) {
            log.error(e);
        }
    }

    public void testConversationsCanBeListedPaginated() {
        CountDownLatch cdl = new CountDownLatch(1);
        StepListener<String> addConversationListener1 = new StepListener<>();
        index.createConversation(addConversationListener1);

        StepListener<String> addConversationListener2 = new StepListener<>();
        addConversationListener1.whenComplete(cid -> { index.createConversation(addConversationListener2); }, e -> {
            cdl.countDown();
            assert (false);
        });

        StepListener<List<ConversationMeta>> listConversationListener1 = new StepListener<>();
        addConversationListener2.whenComplete(cid2 -> { index.getConversations(1, listConversationListener1); }, e -> {
            cdl.countDown();
            assert (false);
        });

        StepListener<List<ConversationMeta>> listConversationListener2 = new StepListener<>();
        listConversationListener1.whenComplete(conversations1 -> { index.getConversations(1, 1, listConversationListener2); }, e -> {
            cdl.countDown();
            assert (false);
        });

        LatchedActionListener<List<ConversationMeta>> finishAndAssert = new LatchedActionListener<>(ActionListener.wrap(conversations2 -> {
            List<ConversationMeta> conversations1 = listConversationListener1.result();
            String cid1 = addConversationListener1.result();
            String cid2 = addConversationListener2.result();
            if (!conversations1.get(0).getCreatedTime().equals(conversations2.get(0).getCreatedTime())) {
                assert (conversations1.get(0).getId().equals(cid2));
                assert (conversations2.get(0).getId().equals(cid1));
            }
        }, e -> { assert (false); }), cdl);
        listConversationListener2.whenComplete(finishAndAssert::onResponse, finishAndAssert::onFailure);
        try {
            cdl.await();
        } catch (InterruptedException e) {
            log.error(e);
        }

    }

    public void testConversationsCanBeDeleted() {
        CountDownLatch cdl = new CountDownLatch(1);
        StepListener<String> addConversationListener = new StepListener<>();
        index.createConversation(addConversationListener);

        StepListener<Boolean> deleteConversationListener = new StepListener<>();
        addConversationListener.whenComplete(cid -> { index.deleteConversation(cid, deleteConversationListener); }, e -> {
            cdl.countDown();
            assert (false);
        });

        LatchedActionListener<List<ConversationMeta>> finishAndAssert = new LatchedActionListener<>(ActionListener.wrap(conversations -> {
            assert (conversations.size() == 0);
        }, e -> {
            cdl.countDown();
            assert (false);
        }), cdl);
        deleteConversationListener.whenComplete(success -> {
            if (success) {
                index.getConversations(10, finishAndAssert);
            } else {
                cdl.countDown();
                assert (false);
            }
        }, e -> {
            cdl.countDown();
            assert (false);
        });

        try {
            cdl.await();
        } catch (InterruptedException e) {
            log.error(e);
        }
    }

    public void testConversationsForDifferentUsersAreDifferent() {
        try (ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
            CountDownLatch cdl = new CountDownLatch(1);
            Stack<StoredContext> contextStack = new Stack<>();
            Consumer<Exception> onFail = e -> {
                while (!contextStack.empty()) {
                    contextStack.pop().close();
                }
                cdl.countDown();
                log.error(e);
                threadContext.restore();
                assert (false);
            };

            final String user1 = "test-user1";
            final String user2 = "test-user2";

            StepListener<String> cid1 = new StepListener<>();
            contextStack.push(setUser(user1));
            index.createConversation(cid1);

            StepListener<String> cid2 = new StepListener<>();
            cid1.whenComplete(cid -> { index.createConversation(cid2); }, onFail);

            StepListener<String> cid3 = new StepListener<>();
            cid2.whenComplete(cid -> {
                contextStack.push(setUser(user2));
                index.createConversation(cid3);
            }, onFail);

            StepListener<List<ConversationMeta>> conversationsListener = new StepListener<>();
            cid3.whenComplete(cid -> { index.getConversations(10, conversationsListener); }, onFail);

            StepListener<List<ConversationMeta>> originalConversationsListener = new StepListener<>();
            conversationsListener.whenComplete(conversations -> {
                assert (conversations.size() == 1);
                assert (conversations.get(0).getId().equals(cid3.result()));
                assert (conversations.get(0).getUser().equals(user2));
                contextStack.pop().restore();
                index.getConversations(10, originalConversationsListener);
            }, onFail);

            originalConversationsListener.whenComplete(conversations -> {
                assert (conversations.size() == 2);
                if (!conversations.get(0).getCreatedTime().equals(conversations.get(1).getCreatedTime())) {
                    assert (conversations.get(0).getId().equals(cid2.result()));
                    assert (conversations.get(1).getId().equals(cid1.result()));
                }
                assert (conversations.get(0).getUser().equals(user1));
                assert (conversations.get(1).getUser().equals(user1));
                contextStack.pop().restore();
                cdl.countDown();
            }, onFail);

            try {
                cdl.await();
                threadContext.restore();
            } catch (InterruptedException e) {
                log.error(e);
            }
        } catch (Exception e) {
            log.error(e);
            throw e;
        }
    }

    public void testDifferentUsersCannotTouchOthersConversations() {
        try (ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
            CountDownLatch cdl = new CountDownLatch(1);
            Stack<StoredContext> contextStack = new Stack<>();
            Consumer<Exception> onFail = e -> {
                while (!contextStack.empty()) {
                    contextStack.pop().close();
                }
                cdl.countDown();
                log.error(e);
                threadContext.restore();
                assert (false);
            };

            final String user1 = "user-1";
            final String user2 = "user-2";
            contextStack.push(setUser(user1));

            StepListener<String> cid1 = new StepListener<>();
            index.createConversation(cid1);

            StepListener<Boolean> delListener = new StepListener<>();
            cid1.whenComplete(cid -> {
                contextStack.push(setUser(user2));
                index.deleteConversation(cid1.result(), delListener);
            }, onFail);

            delListener.whenComplete(success -> {
                Exception e = new OpenSearchSecurityException(
                    "Incorrect access was given to user [" + user2 + "] for conversation " + cid1.result()
                );
                while (!contextStack.empty()) {
                    contextStack.pop().close();
                }
                cdl.countDown();
                log.error(e);
                assert (false);
            }, e -> {
                if (e instanceof OpenSearchSecurityException
                    && e.getMessage().startsWith("User [" + user2 + "] does not have access to conversation ")) {
                    contextStack.pop().restore();
                    contextStack.pop().restore();
                    cdl.countDown();
                } else {
                    onFail.accept(e);
                }
            });

            try {
                cdl.await();
                threadContext.restore();
            } catch (InterruptedException e) {
                log.error(e);
            }
        } catch (Exception e) {
            log.error(e);
            throw e;
        }
    }

    public void testCanQueryOverConversations() {
        CountDownLatch cdl = new CountDownLatch(1);
        StepListener<String> convo1 = new StepListener<>();
        index.createConversation("Henry Conversation", convo1);

        StepListener<String> convo2 = new StepListener<>();
        convo1.whenComplete(cid -> { index.createConversation("Mehul Conversation", convo2); }, e -> {
            cdl.countDown();
            log.error(e);
            assert (false);
        });

        StepListener<SearchResponse> search = new StepListener<>();
        convo2.whenComplete(cid -> {
            SearchRequest request = new SearchRequest();
            request.source(new SearchSourceBuilder());
            request.source().query(new TermQueryBuilder(ConversationalIndexConstants.META_NAME_FIELD, "Henry Conversation"));
            index.searchConversations(request, search);
        }, e -> {
            cdl.countDown();
            log.error(e);
            assert (false);
        });

        search.whenComplete(response -> {
            log.info("SEARCH RESPONSE");
            log.info(response.toString());
            cdl.countDown();
            assert (response.getHits().getAt(0).getId().equals(convo1.result()));
        }, e -> {
            cdl.countDown();
            log.error(e);
            assert (false);
        });

        try {
            cdl.await();
        } catch (InterruptedException e) {
            log.error(e);
        }
    }

    public void testCanQueryOverConversationsSecurely() {
        try (ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
            CountDownLatch cdl = new CountDownLatch(1);
            Stack<StoredContext> contextStack = new Stack<>();
            Consumer<Exception> onFail = e -> {
                while (!contextStack.empty()) {
                    contextStack.pop().close();
                }
                cdl.countDown();
                log.error(e);
                threadContext.restore();
                assert (false);
            };

            final String user1 = "Dhrubo";
            final String user2 = "Jing";
            contextStack.push(setUser(user1));

            StepListener<String> convo1 = new StepListener<>();
            index.createConversation("Dhrubo Conversation", convo1);

            StepListener<String> convo2 = new StepListener<>();
            convo1.whenComplete(cid -> {
                contextStack.push(setUser(user2));
                index.createConversation("Jing Conversation", convo2);
            }, onFail);

            StepListener<SearchResponse> search1 = new StepListener<>();
            convo2.whenComplete(cid -> {
                SearchRequest request = new SearchRequest();
                request.source(new SearchSourceBuilder());
                request.source().query(new TermQueryBuilder(ConversationalIndexConstants.META_NAME_FIELD, "Dhrubo Conversation"));
                index.searchConversations(request, search1);
            }, onFail);

            StepListener<SearchResponse> search2 = new StepListener<>();
            search1.whenComplete(response -> {
                SearchRequest request = new SearchRequest();
                request.source(new SearchSourceBuilder());
                request.source().query(new TermQueryBuilder(ConversationalIndexConstants.META_NAME_FIELD, "Jing Conversation"));
                index.searchConversations(request, search2);
            }, onFail);

            search2.whenComplete(response -> {
                cdl.countDown();
                assert (response.getHits().getAt(0).getId().equals(convo2.result()));
                assert (search1.result().getHits().getHits().length == 0);
                while (!contextStack.isEmpty()) {
                    contextStack.pop().close();
                }
            }, onFail);

            try {
                cdl.await();
                threadContext.restore();
            } catch (InterruptedException e) {
                log.error(e);
                threadContext.restore();
            }

        } catch (Exception e) {
            log.error(e);
        }
    }

    public void testCanGetAConversationById() {
        CountDownLatch cdl = new CountDownLatch(1);
        StepListener<String> cid1 = new StepListener<>();
        index.createConversation("convo1", cid1);

        StepListener<String> cid2 = new StepListener<>();
        cid1.whenComplete(cid -> { index.createConversation("convo2", cid2); }, e -> {
            cdl.countDown();
            log.error(e);
            assert (false);
        });

        StepListener<ConversationMeta> get1 = new StepListener<>();
        cid2.whenComplete(cid -> { index.getConversation(cid1.result(), get1); }, e -> {
            cdl.countDown();
            log.error(e);
            assert (false);
        });

        StepListener<ConversationMeta> get2 = new StepListener<>();
        get1.whenComplete(convo1 -> { index.getConversation(cid2.result(), get2); }, e -> {
            cdl.countDown();
            log.error(e);
            assert (false);
        });

        get2.whenComplete(convo2 -> {
            assert (cid1.result().equals(get1.result().getId()));
            assert (cid2.result().equals(get2.result().getId()));
            assert (get1.result().getName().equals("convo1"));
            assert (get2.result().getName().equals("convo2"));
            cdl.countDown();
        }, e -> {
            cdl.countDown();
            log.error(e);
            assert (false);
        });

        try {
            cdl.await();
        } catch (InterruptedException e) {
            log.error(e);
        }
    }

    public void testCanGetAConversationByIdSecurely() {
        try (ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
            CountDownLatch cdl = new CountDownLatch(1);
            Stack<StoredContext> contextStack = new Stack<>();
            Consumer<Exception> onFail = e -> {
                while (!contextStack.empty()) {
                    contextStack.pop().close();
                }
                cdl.countDown();
                log.error(e);
                threadContext.restore();
                assert (false);
            };

            final String user1 = "Austin";
            final String user2 = "Yaliang";
            contextStack.push(setUser(user1));

            StepListener<String> cid1 = new StepListener<>();
            index.createConversation("Austin Convo", cid1);

            StepListener<String> cid2 = new StepListener<>();
            cid1.whenComplete(cid -> {
                contextStack.push(setUser(user2));
                index.createConversation("Yaliang Convo", cid2);
            }, onFail);

            StepListener<ConversationMeta> get2 = new StepListener<>();
            cid2.whenComplete(cid -> { index.getConversation(cid2.result(), get2); }, onFail);

            StepListener<ConversationMeta> get1 = new StepListener<>();
            get2.whenComplete(convo -> { index.getConversation(cid1.result(), get1); }, onFail);

            get1.whenComplete(convo -> {
                while (!contextStack.isEmpty()) {
                    contextStack.pop().close();
                }
                cdl.countDown();
                assert (false);
            }, e -> {
                cdl.countDown();
                assert (e.getMessage().startsWith("User [Yaliang] does not have access to conversation"));
                assert (get2.result().getName().equals("Yaliang Convo"));
                assert (get2.result().getId().equals(cid2.result()));
            });

            try {
                cdl.await();
                threadContext.restore();
            } catch (InterruptedException e) {
                log.error(e);
                threadContext.restore();
            }

        } catch (Exception e) {
            log.error(e);
        }
    }

}
