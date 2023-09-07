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
package org.opensearch.ml.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.opensearch.common.settings.Settings;
import org.opensearch.plugins.SearchPipelinePlugin;
import org.opensearch.plugins.SearchPlugin;
import org.opensearch.searchpipelines.questionanswering.generative.GenerativeQAProcessorConstants;
import org.opensearch.searchpipelines.questionanswering.generative.GenerativeQARequestProcessor;
import org.opensearch.searchpipelines.questionanswering.generative.GenerativeQAResponseProcessor;
import org.opensearch.searchpipelines.questionanswering.generative.ext.GenerativeQAParamExtBuilder;

public class MachineLearningPluginTests {

    MachineLearningPlugin plugin = new MachineLearningPlugin();

    @Test
    public void testGetSearchExts() {
        List<SearchPlugin.SearchExtSpec<?>> searchExts = plugin.getSearchExts();
        assertEquals(1, searchExts.size());
        SearchPlugin.SearchExtSpec<?> spec = searchExts.get(0);
        assertEquals(GenerativeQAParamExtBuilder.PARAMETER_NAME, spec.getName().getPreferredName());
    }

    @Test
    public void testGetRequestProcessors() {
        SearchPipelinePlugin.Parameters parameters = mock(SearchPipelinePlugin.Parameters.class);
        Map<String, ?> requestProcessors = plugin.getRequestProcessors(parameters);
        assertEquals(1, requestProcessors.size());
        assertTrue(
            requestProcessors.get(GenerativeQAProcessorConstants.REQUEST_PROCESSOR_TYPE) instanceof GenerativeQARequestProcessor.Factory
        );
    }

    @Test
    public void testGetResponseProcessors() {
        SearchPipelinePlugin.Parameters parameters = mock(SearchPipelinePlugin.Parameters.class);
        Map<String, ?> responseProcessors = plugin.getResponseProcessors(parameters);
        assertEquals(1, responseProcessors.size());
        assertTrue(
            responseProcessors.get(GenerativeQAProcessorConstants.RESPONSE_PROCESSOR_TYPE) instanceof GenerativeQAResponseProcessor.Factory
        );
    }
}
