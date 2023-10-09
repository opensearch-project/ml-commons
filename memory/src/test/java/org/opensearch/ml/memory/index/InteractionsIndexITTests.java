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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.junit.Before;
import org.junit.Ignore;
import org.opensearch.action.LatchedActionListener;
import org.opensearch.action.StepListener;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.query.MatchQueryBuilder;
import org.opensearch.ml.common.conversation.ConversationalIndexConstants;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.OpenSearchIntegTestCase;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;

import lombok.extern.log4j.Log4j2;

@Log4j2
@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
@OpenSearchIntegTestCase.ClusterScope(scope = OpenSearchIntegTestCase.Scope.TEST, numDataNodes = 2)
@Ignore
public class InteractionsIndexITTests extends OpenSearchIntegTestCase {

    private Client client;
    private ClusterService clusterService;
    private InteractionsIndex index;

    @Before
    public void setup() {
        client = client();
        clusterService = clusterService();
        index = new InteractionsIndex(client, clusterService, new ConversationMetaIndex(client, clusterService));
    }

    /**
     * Test the index intialization logic - can I create the index exactly once (while trying 3 times)
     */
    public void testInteractionsIndexCanBeInitialized() {
        log.info("testing index creation logic of the index object");
        CountDownLatch cdl = new CountDownLatch(3);
        index.initInteractionsIndexIfAbsent(new LatchedActionListener<>(ActionListener.wrap(r -> { assert (r); }, e -> {
            cdl.countDown();
            cdl.countDown();
            log.error(e);
            assert (false);
        }), cdl));
        index.initInteractionsIndexIfAbsent(new LatchedActionListener<>(ActionListener.wrap(r -> { assert (r); }, e -> {
            cdl.countDown();
            cdl.countDown();
            log.error(e);
            assert (false);
        }), cdl));
        InteractionsIndex otherIndex = new InteractionsIndex(client, clusterService, new ConversationMetaIndex(client, clusterService));
        otherIndex.initInteractionsIndexIfAbsent(new LatchedActionListener<>(ActionListener.wrap(r -> { assert (r); }, e -> {
            cdl.countDown();
            cdl.countDown();
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
     * Make sure nothing breaks when I add an interaction, with and without timestamp,
     * and that the ids are different
     */
    public void testCanAddNewInteraction() {
        CountDownLatch cdl = new CountDownLatch(2);
        String[] ids = new String[2];
        index
            .createInteraction(
                "test",
                "test input",
                "pt",
                "test response",
                "test origin",
                Collections.singletonMap("metadata", "some meta"),
                new LatchedActionListener<>(ActionListener.wrap(id -> {
                    ids[0] = id;
                }, e -> {
                    cdl.countDown();
                    log.error(e);
                    assert (false);
                }), cdl)
            );

        index
            .createInteraction(
                "test",
                "test input",
                "pt",
                "test response",
                "test origin",
                Collections.singletonMap("metadata", "some meta"),
                new LatchedActionListener<>(ActionListener.wrap(id -> {
                    ids[1] = id;
                }, e -> {
                    cdl.countDown();
                    log.error(e);
                    assert (false);
                }), cdl)
            );
        try {
            cdl.await();
        } catch (InterruptedException e) {
            log.error(e);
        }
        assert (!ids[0].equals(ids[1]));
    }

    /**
     * Make sure I can get interactions out related to a conversation
     */
    public void testGetInteractions() {
        final String conversation = "test-conversation";
        CountDownLatch cdl = new CountDownLatch(1);
        StepListener<String> id1Listener = new StepListener<>();
        index
            .createInteraction(
                conversation,
                "test input",
                "pt",
                "test response",
                "test origin",
                Collections.singletonMap("metadata", "some meta"),
                id1Listener
            );

        StepListener<String> id2Listener = new StepListener<>();
        id1Listener.whenComplete(id -> {
            index
                .createInteraction(
                    conversation,
                    "test input",
                    "pt",
                    "test response",
                    "test origin",
                    Collections.singletonMap("metadata", "some meta"),
                    Instant.now().plus(3, ChronoUnit.MINUTES),
                    id2Listener
                );
        }, e -> {
            cdl.countDown();
            log.error(e);
            assert (false);
        });

        StepListener<List<Interaction>> getListener = new StepListener<>();
        id2Listener.whenComplete(r -> { index.getInteractions(conversation, 0, 2, getListener); }, e -> {
            cdl.countDown();
            log.error(e);
            assert (false);
        });

        LatchedActionListener<List<Interaction>> finishAndAssert = new LatchedActionListener<>(ActionListener.wrap(interactions -> {
            assert (interactions.size() == 2);
            assert (interactions.get(0).getId().equals(id2Listener.result()));
            assert (interactions.get(1).getId().equals(id1Listener.result()));
        }, e -> {
            log.error(e);
            assert (false);
        }), cdl);
        getListener.whenComplete(finishAndAssert::onResponse, finishAndAssert::onFailure);

        try {
            cdl.await();
        } catch (InterruptedException e) {
            log.error(e);
        }
    }

    public void testGetInteractionPages() {
        final String conversation = "test-conversation";
        CountDownLatch cdl = new CountDownLatch(1);
        StepListener<String> id1Listener = new StepListener<>();
        index
            .createInteraction(
                conversation,
                "test input",
                "pt",
                "test response",
                "test origin",
                Collections.singletonMap("metadata", "some meta"),
                id1Listener
            );

        StepListener<String> id2Listener = new StepListener<>();
        id1Listener.whenComplete(id -> {
            index
                .createInteraction(
                    conversation,
                    "test input1",
                    "pt",
                    "test response",
                    "test origin",
                    Collections.singletonMap("metadata", "some meta"),
                    Instant.now().plus(3, ChronoUnit.MINUTES),
                    id2Listener
                );
        }, e -> {
            cdl.countDown();
            log.error(e);
            assert (false);
        });

        StepListener<String> id3Listener = new StepListener<>();
        id2Listener.whenComplete(id -> {
            index
                .createInteraction(
                    conversation,
                    "test input2",
                    "pt",
                    "test response",
                    "test origin",
                    Collections.singletonMap("metadata", "some meta"),
                    Instant.now().plus(4, ChronoUnit.MINUTES),
                    id3Listener
                );
        }, e -> {
            cdl.countDown();
            log.error(e);
            assert (false);
        });

        StepListener<List<Interaction>> getListener1 = new StepListener<>();
        id3Listener.whenComplete(r -> { index.getInteractions(conversation, 0, 2, getListener1); }, e -> {
            cdl.countDown();
            log.error(e);
            assert (false);
        });

        StepListener<List<Interaction>> getListener2 = new StepListener<>();
        getListener1.whenComplete(r -> { index.getInteractions(conversation, 2, 2, getListener2); }, e -> {
            cdl.countDown();
            log.error(e);
            assert (false);
        });

        LatchedActionListener<List<Interaction>> finishAndAssert = new LatchedActionListener<>(ActionListener.wrap(interactions2 -> {
            List<Interaction> interactions1 = getListener1.result();
            String id1 = id1Listener.result();
            String id2 = id2Listener.result();
            String id3 = id3Listener.result();
            assert (interactions2.size() == 1);
            assert (interactions1.size() == 2);
            assert (interactions1.get(0).getId().equals(id3));
            assert (interactions1.get(1).getId().equals(id2));
            assert (interactions2.get(0).getId().equals(id1));
        }, e -> {
            log.error(e);
            assert (false);
        }), cdl);
        getListener2.whenComplete(finishAndAssert::onResponse, finishAndAssert::onFailure);

        try {
            cdl.await();
        } catch (InterruptedException e) {
            log.error(e);
        }
    }

    public void testDeleteConversation() {
        final String conversation1 = "conversation1";
        final String conversation2 = "conversation2";
        CountDownLatch cdl = new CountDownLatch(1);
        StepListener<String> iid1 = new StepListener<>();
        index
            .createInteraction(
                conversation1,
                "test input",
                "pt",
                "test response",
                "test origin",
                Collections.singletonMap("metadata", "some meta"),
                iid1
            );

        StepListener<String> iid2 = new StepListener<>();
        iid1.whenComplete(r -> {
            index
                .createInteraction(
                    conversation1,
                    "test input",
                    "pt",
                    "test response",
                    "test origin",
                    Collections.singletonMap("metadata", "some meta"),
                    iid2
                );
        }, e -> {
            cdl.countDown();
            log.error(e);
            assert (false);
        });

        StepListener<String> iid3 = new StepListener<>();
        iid2.whenComplete(r -> {
            index
                .createInteraction(
                    conversation2,
                    "test input",
                    "pt",
                    "test response",
                    "test origin",
                    Collections.singletonMap("metadata", "some meta"),
                    iid3
                );
        }, e -> {
            cdl.countDown();
            log.error(e);
            assert (false);
        });

        StepListener<String> iid4 = new StepListener<>();
        iid3.whenComplete(r -> {
            index
                .createInteraction(
                    conversation1,
                    "test input",
                    "pt",
                    "test response",
                    "test origin",
                    Collections.singletonMap("metadata", "some meta"),
                    iid4
                );
        }, e -> {
            cdl.countDown();
            log.error(e);
            assert (false);
        });

        StepListener<Boolean> deleteListener = new StepListener<>();
        iid4.whenComplete(r -> { index.deleteConversation(conversation1, deleteListener); }, e -> {
            cdl.countDown();
            log.error(e);
            assert (false);
        });

        StepListener<List<Interaction>> interactions1 = new StepListener<>();
        deleteListener.whenComplete(success -> {
            if (success) {
                index.getInteractions(conversation1, 0, 10, interactions1);
            } else {
                cdl.countDown();
                assert (false);
            }
        }, e -> {
            cdl.countDown();
            log.error(e);
            assert (false);
        });

        StepListener<List<Interaction>> interactions2 = new StepListener<>();
        interactions1.whenComplete(interactions -> { index.getInteractions(conversation2, 0, 10, interactions2); }, e -> {
            cdl.countDown();
            log.error(e);
            assert (false);
        });

        LatchedActionListener<List<Interaction>> finishAndAssert = new LatchedActionListener<>(ActionListener.wrap(interactions -> {
            assert (interactions.size() == 1);
            assert (interactions.get(0).getId().equals(iid3.result()));
            assert (interactions1.result().size() == 0);
        }, e -> {
            log.error(e);
            assert (false);
        }), cdl);
        interactions2.whenComplete(finishAndAssert::onResponse, finishAndAssert::onFailure);

        try {
            cdl.await();
        } catch (InterruptedException e) {
            log.error(e);
        }
    }

    public void testSearchInteractions() {
        final String conversation1 = "conversation1";
        final String conversation2 = "conversation2";
        CountDownLatch cdl = new CountDownLatch(1);
        StepListener<String> iid1 = new StepListener<>();
        index
            .createInteraction(
                conversation1,
                "input about fish",
                "pt",
                "response about fish",
                "origin1",
                Collections.singletonMap("metadata", "some meta"),
                iid1
            );

        StepListener<String> iid2 = new StepListener<>();
        iid1.whenComplete(r -> {
            index
                .createInteraction(
                    conversation1,
                    "input about squash",
                    "pt",
                    "response about squash",
                    "origin1",
                    Collections.singletonMap("metadata", "some meta"),
                    iid2
                );
        }, e -> {
            cdl.countDown();
            log.error(e);
            assert (false);
        });

        StepListener<String> iid3 = new StepListener<>();
        iid2.whenComplete(r -> {
            index
                .createInteraction(
                    conversation2,
                    "input about fish",
                    "pt2",
                    "response about fish",
                    "origin1",
                    Collections.singletonMap("metadata", "some meta"),
                    iid3
                );
        }, e -> {
            cdl.countDown();
            log.error(e);
            assert (false);
        });

        StepListener<String> iid4 = new StepListener<>();
        iid3.whenComplete(r -> {
            index
                .createInteraction(
                    conversation1,
                    "input about france",
                    "pt",
                    "response about france",
                    "origin1",
                    Collections.singletonMap("metadata", "some meta"),
                    iid4
                );
        }, e -> {
            cdl.countDown();
            log.error(e);
            assert (false);
        });

        StepListener<SearchResponse> searchListener = new StepListener<>();
        iid4.whenComplete(r -> {
            SearchRequest request = new SearchRequest();
            request.source(new SearchSourceBuilder());
            request.source().query(new MatchQueryBuilder(ConversationalIndexConstants.INTERACTIONS_INPUT_FIELD, "fish input"));
            index.searchInteractions(conversation1, request, searchListener);
        }, e -> {
            cdl.countDown();
            log.error(e);
            assert (false);
        });

        searchListener.whenComplete(response -> {
            cdl.countDown();
            assert (response.getHits().getHits().length == 3);
            // BM25 was being a little unpredictable here so I don't assert ordering
            List<String> ids = new ArrayList<>(3);
            for (SearchHit hit : response.getHits()) {
                ids.add(hit.getId());
            }
            assert (ids.contains(iid1.result()));
            assert (ids.contains(iid2.result()));
            assert (ids.contains(iid4.result()));
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

    public void testGetInteractionById() {
        final String conversation = "test-conversation";
        CountDownLatch cdl = new CountDownLatch(1);
        StepListener<String> iid1 = new StepListener<>();
        index
            .createInteraction(
                conversation,
                "test input",
                "pt",
                "test response",
                "test origin",
                Collections.singletonMap("metadata", "some meta"),
                iid1
            );

        StepListener<String> iid2 = new StepListener<>();
        iid1.whenComplete(iid -> {
            index
                .createInteraction(
                    conversation,
                    "test input2",
                    "pt",
                    "test response",
                    "test origin",
                    Collections.singletonMap("metadata", "some meta"),
                    iid2
                );
        }, e -> {
            cdl.countDown();
            log.error(e);
            assert false;
        });

        StepListener<Interaction> get1 = new StepListener<>();
        iid2.whenComplete(iid -> { index.getInteraction(conversation, iid1.result(), get1); }, e -> {
            cdl.countDown();
            log.error(e);
        });

        StepListener<Interaction> get2 = new StepListener<>();
        get1.whenComplete(interaction1 -> { index.getInteraction(conversation, iid2.result(), get2); }, e -> {
            cdl.countDown();
            log.error(e);
        });

        get2.whenComplete(interaction2 -> {
            assert (get1.result().getId().equals(iid1.result()));
            assert (get1.result().getInput().equals("test input"));
            assert (get2.result().getId().equals(iid2.result()));
            assert (get2.result().getInput().equals("test input2"));
            cdl.countDown();
        }, e -> {
            cdl.countDown();
            log.error(e);
        });

        try {
            cdl.await();
        } catch (InterruptedException e) {
            log.error(e);
        }
    }
}
