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
package org.opensearch.ml.conversational;

import java.util.List;
import java.util.Stack;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import org.junit.Before;
import org.opensearch.OpenSearchSecurityException;
import org.opensearch.action.ActionListener;
import org.opensearch.action.LatchedActionListener;
import org.opensearch.action.StepListener;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.CheckedConsumer;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.util.concurrent.ThreadContext.StoredContext;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.ml.common.conversational.ConversationMeta;
import org.opensearch.ml.common.conversational.Interaction;
import org.opensearch.ml.conversational.index.OpenSearchConversationalMemoryHandler;
import org.opensearch.test.OpenSearchIntegTestCase;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;

import lombok.extern.log4j.Log4j2;

@Log4j2
@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.TEST, numDataNodes = 2)
public class ConversationalMemoryHandlerTests extends OpenSearchIntegTestCase {

    private Client client;
    private ClusterService clusterService;
    private ConversationalMemoryHandler cmHandler;

    @Before
    private void setup() {
        log.warn("started a test");
        client = client();
        clusterService = clusterService();
        cmHandler = new OpenSearchConversationalMemoryHandler(client, clusterService);
    }

    private StoredContext setUser(String username) {
        StoredContext stored = client.threadPool().getThreadContext().newStoredContext(true, List.of(
            ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT
        ));
        ThreadContext context = client.threadPool().getThreadContext();
        context.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, username + "||");
        return stored;
    }

    public void testCanStartConversations() {
        log.warn("test can start conversations");
        CountDownLatch cdl = new CountDownLatch(3);
        cmHandler.createConversation("test-1", new LatchedActionListener<String>(ActionListener.wrap(cid0 -> {
            cmHandler.createConversation("test-2", new LatchedActionListener<String>(ActionListener.wrap(cid1 -> {
                cmHandler.createConversation(new LatchedActionListener<String>(ActionListener.wrap(cid2 -> {
                    assert(!cid0.equals(cid1) && !cid0.equals(cid2) && !cid1.equals(cid2));
                }, e -> {
                    cdl.countDown(); cdl.countDown(); log.error(e); assert(false);
                }), cdl));
            }, e -> {
                cdl.countDown(); cdl.countDown(); log.error(e); assert(false);
            }), cdl));
        }, e -> {
            cdl.countDown(); cdl.countDown(); log.error(e); assert(false);
        }), cdl));
        try {
            cdl.await();
        } catch (InterruptedException e) {
            log.error(e);
        }
        log.warn("test can start conversations finished");
    }

    public void testCanAddNewInteractionsToConversation() {
        log.warn("test can add new interactions");
        CountDownLatch cdl = new CountDownLatch(1);
        StepListener<String> cidListener = new StepListener<>();
        cmHandler.createConversation("test", cidListener);

        StepListener<String> iid1Listener = new StepListener<>();
        cidListener.whenComplete(cid -> {
            cmHandler.createInteraction(cid, "test input1", "test prompt", "test response", 
                "test agent", "{\"test\":\"metadata\"}", iid1Listener);
        }, e -> {
            cdl.countDown(); assert(false);
        });

        StepListener<String> iid2Listener = new StepListener<>();
        iid1Listener.whenComplete(iid -> {
            cmHandler.createInteraction(cidListener.result(), "test input1", "test prompt", "test response", 
                "test agent", "{\"test\":\"metadata\"}", iid2Listener);
        }, e -> {
            cdl.countDown(); assert(false);
        });

        LatchedActionListener<String> finishAndAssert = new LatchedActionListener<>(ActionListener.wrap(
            iid2 -> {assert(!iid2.equals(iid1Listener.result()));}, e -> {assert(false);}
        ), cdl);
        iid2Listener.whenComplete(finishAndAssert::onResponse, finishAndAssert::onFailure);
        try {
            cdl.await();
        } catch (InterruptedException e) {
            log.error(e);
        }
        log.warn("test can add new interactions finished");
    }


    public void testCanGetInteractionsBackOut() {
        log.warn("test can get interactions");
        CountDownLatch cdl = new CountDownLatch(1);
        StepListener<String> cidListener = new StepListener<>();
        cmHandler.createConversation("test", cidListener);

        StepListener<String> iid1Listener = new StepListener<>();
        cidListener.whenComplete(cid -> {
            cmHandler.createInteraction(cid, "test input1", "test prompt", "test response", 
                "test agent", "{\"test\":\"metadata\"}", iid1Listener);
        }, e -> {
            cdl.countDown(); assert(false);
        });

        StepListener<String> iid2Listener = new StepListener<>();
        iid1Listener.whenComplete(iid -> {
            cmHandler.createInteraction(cidListener.result(), "test input1", "test prompt", "test response", 
                "test agent", "{\"test\":\"metadata\"}", iid2Listener);
        }, e -> {
            cdl.countDown(); assert(false);
        });

        StepListener<List<Interaction>> interactionsListener = new StepListener<>();
        iid2Listener.whenComplete(
            iid2 -> {cmHandler.getInteractions(cidListener.result(), 0, 2, interactionsListener);}, 
            e -> {cdl.countDown(); assert(false);}
        );

        LatchedActionListener<List<ConversationMeta>> finishAndAssert = new LatchedActionListener<>(ActionListener.wrap(
            conversations -> {
                List<Interaction> interactions = interactionsListener.result();
                String id1 = iid1Listener.result();
                String id2 = iid2Listener.result();
                String cid = cidListener.result();
                assert(interactions.size() == 2);
                assert(interactions.get(0).getId().equals(id2));
                assert(interactions.get(1).getId().equals(id1));
                assert(conversations.size() == 1);
                assert(conversations.get(0).getId().equals(cid));
            }, e -> {
                assert(false);
            }
        ), cdl);
        interactionsListener.whenComplete(r -> {
            cmHandler.getConversations(10, finishAndAssert);
        }, e -> {cdl.countDown(); assert(false);});

        try { 
            cdl.await(); 
        } catch (InterruptedException e) { 
            log.error(e); 
        }
        log.warn("test can get interactions finished");
    }

    public void testConversationMetaIsUpToDateWithHits() {
        log.warn("test conversation meta is up to date");
        CountDownLatch cdl = new CountDownLatch(1);
        StepListener<String> cidListener = new StepListener<>();
        cmHandler.createConversation("test", cidListener);

        StepListener<String> iid1Listener = new StepListener<>();
        cidListener.whenComplete(cid -> {
            cmHandler.createInteraction(cid, "test input1", "test prompt", "test response", 
                "test agent", "{\"test\":\"metadata\"}", iid1Listener);
        }, e -> {
            cdl.countDown(); assert(false);
        });

        StepListener<String> iid2Listener = new StepListener<>();
        iid1Listener.whenComplete(iid -> {
            cmHandler.createInteraction(cidListener.result(), "test input1", "test prompt", "test response", 
                "test agent", "{\"test\":\"metadata\"}", iid2Listener);
        }, e -> {
            cdl.countDown(); assert(false);
        });

        StepListener<List<Interaction>> interactionsListener = new StepListener<>();
        iid2Listener.whenComplete(
            iid2 -> {cmHandler.getInteractions(cidListener.result(), 0, 10, interactionsListener);}, 
            e -> {cdl.countDown(); assert(false);}
        );

        LatchedActionListener<List<ConversationMeta>> finishAndAssert = new LatchedActionListener<>(ActionListener.wrap(
            conversations -> {
                List<Interaction> interactions = interactionsListener.result();
                assert(conversations.size() == 1);
                ConversationMeta conversation = conversations.get(0);
                assert(conversation.getLastHit().equals(interactions.get(0).getTimestamp()));
                assert(conversation.getLength() == 2);
            }, e -> {
                assert(false);
            }
        ), cdl);
        interactionsListener.whenComplete(r -> {
            cmHandler.getConversations(10, finishAndAssert);
        }, e -> {cdl.countDown(); assert(false);});

        try { 
            cdl.await(); 
        } catch (InterruptedException e) { 
            log.error(e); 
        }
        log.warn("test conversation meta is up to date finished");
    }

    public void testCanDeleteConversations() {
        log.warn("test can delete conversations");
        CountDownLatch cdl = new CountDownLatch(1);
        StepListener<String> cid1 = new StepListener<>();
        cmHandler.createConversation("test", cid1);

        StepListener<String> iid1 = new StepListener<>();
        cid1.whenComplete(cid -> {
            cmHandler.createInteraction(cid, "test input1", "test prompt", "test response", 
                "test agent", "{\"test\":\"metadata\"}", iid1);
        }, e -> {cdl.countDown(); assert(false); });

        StepListener<String> iid2 = new StepListener<>();
        iid1.whenComplete(iid -> {
            cmHandler.createInteraction(cid1.result(), "test input1", "test prompt", "test response", 
                "test agent", "{\"test\":\"metadata\"}", iid2);
        }, e -> {cdl.countDown(); assert(false); });

        StepListener<String> cid2 = new StepListener<>();
        iid2.whenComplete(iid -> {
            cmHandler.createConversation(cid2);
        }, e -> { cdl.countDown(); assert(false); });

        StepListener<String> iid3 = new StepListener<>();
        cid2.whenComplete(cid -> {
            cmHandler.createInteraction(cid, "test input1", "test prompt", "test response", 
                "test agent", "{\"test\":\"metadata\"}", iid3);
        }, e -> {cdl.countDown(); assert(false); });

        StepListener<Boolean> del = new StepListener<>();
        iid3.whenComplete(iid -> {
            cmHandler.deleteConversation(cid1.result(), del);
        }, e -> {cdl.countDown(); assert(false);});

        StepListener<List<ConversationMeta>> conversations = new StepListener<>();
        del.whenComplete(success -> {
            cmHandler.getConversations(10, conversations);
        }, e -> {cdl.countDown(); assert(false); });

        StepListener<List<Interaction>> inters1 = new StepListener<>();
        conversations.whenComplete(cons -> {
            cmHandler.getInteractions(cid1.result(), 0, 10, inters1);
        }, e -> {cdl.countDown(); assert(false); });

        StepListener<List<Interaction>> inters2 = new StepListener<>();
        inters1.whenComplete(ints -> {
            cmHandler.getInteractions(cid2.result(), 0, 10, inters2);
        }, e -> {cdl.countDown(); assert(false);});

        LatchedActionListener<List<Interaction>> finishAndAssert = new LatchedActionListener<>(ActionListener.wrap(
            r -> {
                assert(del.result());
                assert(conversations.result().size() == 1);
                assert(conversations.result().get(0).getId().equals(cid2.result()));
                assert(inters1.result().size() == 0);
                assert(inters2.result().size() == 1);
                assert(inters2.result().get(0).getId().equals(iid3.result()));
            }, e -> {
                assert(false);
            }
        ), cdl);
        inters2.whenComplete(finishAndAssert::onResponse, finishAndAssert::onFailure);

        try { 
            cdl.await(); 
        } catch (InterruptedException e) { 
            log.error(e); 
        }
        log.warn("test can delete conversations finished");
    }

    public void testDifferentUsers_DifferentConversations() {
        try(ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
            CountDownLatch cdl = new CountDownLatch(1);
            Stack<StoredContext> contextStack = new Stack<>();

            Consumer<Exception> onFail = e -> {
                while(!contextStack.empty()) {
                    contextStack.pop().close();
                }
                cdl.countDown(); 
                log.error(e); 
                threadContext.restore(); 
                assert(false);
            };
            CheckedConsumer<Object, Exception> shouldHaveFailed = r -> {
                while(!contextStack.empty()) {
                    contextStack.pop().close();
                }
                OpenSearchSecurityException e = new OpenSearchSecurityException("User was given inappropriate access controls");
                log.error(e);
                throw e;
            };
            CheckedConsumer<String, Exception> shouldHaveFailedAsString = r -> {shouldHaveFailed.accept(r);};
            CheckedConsumer<List<Interaction>, Exception> shouldHaveFailedAsInterList = r -> {shouldHaveFailed.accept(r);};

            final String user1 = "test-user1";
            final String user2 = "test-user2";

            contextStack.push(setUser(user1));

            StepListener<String> cid1 = new StepListener<>();
            StepListener<String> cid2 = new StepListener<>();
            StepListener<String> cid3 = new StepListener<>();
            StepListener<String> iid1 = new StepListener<>();
            StepListener<String> iid2 = new StepListener<>();
            StepListener<String> iid3 = new StepListener<>();
            StepListener<String> iid4 = new StepListener<>();
            StepListener<String> iid5 = new StepListener<>();
            StepListener<List<ConversationMeta>> conversations1 = new StepListener<>();
            StepListener<List<ConversationMeta>> conversations2 = new StepListener<>();
            StepListener<List<Interaction>> inter1 = new StepListener<>();
            StepListener<List<Interaction>> inter2 = new StepListener<>();
            StepListener<List<Interaction>> inter3 = new StepListener<>();
            StepListener<List<Interaction>> failInter1 = new StepListener<>();
            StepListener<List<Interaction>> failInter2 = new StepListener<>();
            StepListener<List<Interaction>> failInter3 = new StepListener<>();
            StepListener<String> failiid1 = new StepListener<>();
            StepListener<String> failiid2 = new StepListener<>();
            StepListener<String> failiid3 = new StepListener<>();

            cmHandler.createConversation("conversation1", cid1);

            cid1.whenComplete(cid -> {
                cmHandler.createConversation("conversation2", cid2);
            }, onFail);

            cid2.whenComplete(cid -> {
                cmHandler.createInteraction(cid1.result(), "test input1", "test prompt", "test response", 
                "test agent", "{\"test\":\"metadata\"}", iid1);
            }, onFail);

            iid1.whenComplete(iid -> {
                cmHandler.createInteraction(cid1.result(), "test input2", "test prompt", "test response", 
                "test agent", "{\"test\":\"metadata\"}", iid2);
            }, onFail);

            iid2.whenComplete(iid -> {
                cmHandler.createInteraction(cid2.result(), "test input3", "test prompt", "test response", 
                "test agent", "{\"test\":\"metadata\"}", iid3);
            }, onFail);

            iid3.whenComplete(iid -> {
                contextStack.push(setUser(user2));
                cmHandler.createConversation("conversation3", cid3);
            }, onFail);

            cid3.whenComplete(cid -> {
                cmHandler.createInteraction(cid3.result(), "test input4", "test prompt", "test response", 
                "test agent", "{\"test\":\"metadata\"}", iid4);
            }, onFail);

            iid4.whenComplete(iid -> {
                cmHandler.createInteraction(cid3.result(), "test input5", "test prompt", "test response", 
                "test agent", "{\"test\":\"metadata\"}", iid5);
            }, onFail);

            iid5.whenComplete(iid -> {
                cmHandler.createInteraction(cid1.result(), "test inputf1", "test prompt", "test response", 
                "test agent", "{\"test\":\"metadata\"}", failiid1);
            }, onFail);

            failiid1.whenComplete(shouldHaveFailedAsString, e -> {
                if(e instanceof OpenSearchSecurityException && e.getMessage().startsWith("User [" + user2 + "] does not have access to conversation ")) {
                    cmHandler.createInteraction(cid1.result(), "test inputf2", "test prompt", "test response", 
                    "test agent", "{\"test\":\"metadata\"}", failiid2);
                } else {
                    onFail.accept(e);
                }
            });

            failiid2.whenComplete(shouldHaveFailedAsString, e -> {
                if(e instanceof OpenSearchSecurityException && e.getMessage().startsWith("User [" + user2 + "] does not have access to conversation ")) {
                    cmHandler.getConversations(10, conversations2);
                } else {
                    onFail.accept(e);
                }
            });

            conversations2.whenComplete(conversations -> {
                assert(conversations.size() == 1);
                assert(conversations.get(0).getId().equals(cid3.result()));
                cmHandler.getInteractions(cid3.result(), 0, 10, inter3);
            }, onFail);

            inter3.whenComplete(inters -> {
                assert(inters.size() == 2);
                assert(inters.get(0).getId().equals(iid5.result()));
                assert(inters.get(1).getId().equals(iid4.result()));
                cmHandler.getInteractions(cid2.result(), 0, 10, failInter2);
            }, onFail);

            failInter2.whenComplete(shouldHaveFailedAsInterList, e -> {
                if(e instanceof OpenSearchSecurityException && e.getMessage().startsWith("User [" + user2 + "] does not have access to conversation ")) {
                    cmHandler.getInteractions(cid1.result(), 0, 10, failInter1);
                } else {
                    onFail.accept(e);
                }
            });

            failInter1.whenComplete(shouldHaveFailedAsInterList, e -> {
                if(e instanceof OpenSearchSecurityException && e.getMessage().startsWith("User [" + user2 + "] does not have access to conversation ")) {
                    contextStack.pop().restore();
                    cmHandler.getConversations(0, 10, conversations1);
                } else {
                    onFail.accept(e);
                }
            });

            conversations1.whenComplete(conversations -> {
                assert(conversations.size() == 2);
                assert(conversations.get(0).getId().equals(cid2.result()));
                assert(conversations.get(1).getId().equals(cid1.result()));
                cmHandler.getInteractions(cid1.result(), 0, 10, inter1);
            }, onFail);

            inter1.whenComplete(inters -> {
                assert(inters.size() == 2);
                assert(inters.get(0).getId().equals(iid2.result()));
                assert(inters.get(1).getId().equals(iid1.result()));
                cmHandler.getInteractions(cid2.result(), 0, 10, inter2);
            }, onFail);

            inter2.whenComplete(inters -> {
                assert(inters.size() == 1);
                assert(inters.get(0).getId().equals(iid3.result()));
                cmHandler.getInteractions(cid3.result(), 0, 10, failInter3);
            }, onFail);

            failInter3.whenComplete(shouldHaveFailedAsInterList, e -> {
                if(e instanceof OpenSearchSecurityException && e.getMessage().startsWith("User [" + user1 + "] does not have access to conversation ")) {
                    cmHandler.createInteraction(cid3.result(), "test inputf3", "test prompt", "test response", 
                    "test agent", "{\"test\":\"metadata\"}", failiid3);
                } else {
                    onFail.accept(e);
                }
            });

            failiid3.whenComplete(shouldHaveFailedAsString, e -> {
                if(e instanceof OpenSearchSecurityException && e.getMessage().startsWith("User [" + user1 + "] does not have access to conversation ")) {
                    contextStack.pop().restore();
                    cdl.countDown();
                } else {
                    onFail.accept(e);
                }
            });

            try { 
                cdl.await(); 
            } catch (InterruptedException e) { 
                log.error(e); 
            }
            threadContext.restore();
        } catch (Exception e) {
            log.error(e);
            throw e;
        }
    }


}