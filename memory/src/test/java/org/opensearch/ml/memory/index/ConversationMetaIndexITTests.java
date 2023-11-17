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
import org.opensearch.client.Client;
import org.opensearch.client.Requests;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.util.concurrent.ThreadContext.StoredContext;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.conversation.ConversationMeta;
import org.opensearch.ml.common.conversation.ConversationalIndexConstants;
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

}
