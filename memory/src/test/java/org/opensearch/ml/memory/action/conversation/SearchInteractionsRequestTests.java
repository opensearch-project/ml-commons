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
package org.opensearch.ml.memory.action.conversation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.common.io.stream.NamedWriteableRegistry;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.indices.IndicesModule;
import org.opensearch.search.SearchModule;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.OpenSearchTestCase;

public class SearchInteractionsRequestTests extends OpenSearchTestCase {

    protected NamedWriteableRegistry namedWriteableRegistry;

    public void setUp() throws Exception {
        super.setUp();
        IndicesModule indicesModule = new IndicesModule(Collections.emptyList());
        SearchModule searchModule = new SearchModule(Settings.EMPTY, List.of());
        List<NamedWriteableRegistry.Entry> entries = new ArrayList<>();
        entries.addAll(indicesModule.getNamedWriteables());
        entries.addAll(searchModule.getNamedWriteables());
        namedWriteableRegistry = new NamedWriteableRegistry(entries);
    }

    public void testConstructorsAndStreaming() throws IOException {
        SearchRequest original = new SearchRequest();
        original.source(new SearchSourceBuilder());
        original.source().query(new MatchAllQueryBuilder());

        SearchInteractionsRequest request = new SearchInteractionsRequest("test_cid", original);
        assert (request instanceof SearchRequest);
        assert (request.getConversationId().equals("test_cid"));
        assert (request.validate() == null);

        SearchInteractionsRequest newRequest = copyWriteable(request, namedWriteableRegistry, SearchInteractionsRequest::new);
        assert (newRequest.getConversationId().equals("test_cid"));
        assert (newRequest.validate() == null);
        assert (newRequest.equals(request));
    }

}
