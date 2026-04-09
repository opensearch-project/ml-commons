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
package org.opensearch.searchpipelines.questionanswering.generative;

import java.io.IOException;

import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchResponseSections;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.test.OpenSearchTestCase;

public class GenerativeSearchResponseTests extends OpenSearchTestCase {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    public void testToXContent() throws IOException {
        String answer = "answer";
        SearchResponseSections internal = new SearchResponseSections(
            new SearchHits(new SearchHit[0], null, 0),
            null,
            null,
            false,
            false,
            null,
            0
        );
        GenerativeSearchResponse searchResponse = new GenerativeSearchResponse(
            answer,
            null,
            internal,
            null,
            0,
            0,
            0,
            0,
            new ShardSearchFailure[0],
            SearchResponse.Clusters.EMPTY,
            "iid"
        );
        XContentBuilder builder = XContentFactory.jsonBuilder();
        XContentBuilder actual = searchResponse.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(actual);
    }

    public void testToXContentWithError() throws IOException {
        String error = "error";
        SearchResponseSections internal = new SearchResponseSections(
            new SearchHits(new SearchHit[0], null, 0),
            null,
            null,
            false,
            false,
            null,
            0
        );
        GenerativeSearchResponse searchResponse = new GenerativeSearchResponse(
            null,
            error,
            internal,
            null,
            0,
            0,
            0,
            0,
            new ShardSearchFailure[0],
            SearchResponse.Clusters.EMPTY,
            "iid"
        );
        XContentBuilder builder = XContentFactory.jsonBuilder();
        XContentBuilder actual = searchResponse.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(actual);
    }

    public void testInputValidation() {
        exceptionRule.expect(NullPointerException.class);
        exceptionRule.expectMessage("If answer is not given, errorMessage must be provided.");
        SearchResponseSections internal = new SearchResponseSections(
            new SearchHits(new SearchHit[0], null, 0),
            null,
            null,
            false,
            false,
            null,
            0
        );
        GenerativeSearchResponse searchResponse = new GenerativeSearchResponse(
            null,
            null,
            internal,
            null,
            0,
            0,
            0,
            0,
            new ShardSearchFailure[0],
            SearchResponse.Clusters.EMPTY,
            "iid"
        );
    }

}
