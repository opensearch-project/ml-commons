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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.search.pipeline.Processor;
import org.opensearch.search.pipeline.SearchRequestProcessor;
import org.opensearch.test.OpenSearchTestCase;

public class GenerativeQARequestProcessorTests extends OpenSearchTestCase {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(mlFeatureEnabledSetting.isRagSearchPipelineEnabled()).thenReturn(true);
    }

    public void testProcessorFactory() throws Exception {

        Map<String, Object> config = new HashMap<>();
        config.put("model_id", "foo");
        SearchRequestProcessor processor = new GenerativeQARequestProcessor.Factory(mlFeatureEnabledSetting)
            .create(null, "tag", "desc", true, config, null);
        assertTrue(processor instanceof GenerativeQARequestProcessor);
    }

    public void testProcessRequest() throws Exception {
        GenerativeQARequestProcessor processor = new GenerativeQARequestProcessor("tag", "desc", false, "foo", mlFeatureEnabledSetting);
        SearchRequest request = new SearchRequest();
        SearchRequest processed = processor.processRequest(request);
        assertEquals(request, processed);
    }

    public void testGetType() {
        GenerativeQARequestProcessor processor = new GenerativeQARequestProcessor("tag", "desc", false, "foo", mlFeatureEnabledSetting);
        assertEquals(GenerativeQAProcessorConstants.REQUEST_PROCESSOR_TYPE, processor.getType());
    }

    public void testProcessorFeatureFlagOffOnOff() throws Exception {
        Map<String, Object> config = new HashMap<>();
        config.put("model_id", "foo");
        when(mlFeatureEnabledSetting.isRagSearchPipelineEnabled()).thenReturn(false);
        Processor.Factory factory = new GenerativeQARequestProcessor.Factory(mlFeatureEnabledSetting);
        boolean firstExceptionThrown = false;
        try {
            factory.create(null, "tag", "desc", true, config, null);
        } catch (MLException e) {
            assertEquals(GenerativeQAProcessorConstants.FEATURE_NOT_ENABLED_ERROR_MSG, e.getMessage());
            firstExceptionThrown = true;
        }
        assertTrue(firstExceptionThrown);

        when(mlFeatureEnabledSetting.isRagSearchPipelineEnabled()).thenReturn(true);
        GenerativeQARequestProcessor processor = (GenerativeQARequestProcessor) factory.create(null, "tag", "desc", true, config, null);

        when(mlFeatureEnabledSetting.isRagSearchPipelineEnabled()).thenReturn(false);
        boolean secondExceptionThrown = false;
        try {
            processor.processRequest(mock(SearchRequest.class));
        } catch (MLException e) {
            assertEquals(GenerativeQAProcessorConstants.FEATURE_NOT_ENABLED_ERROR_MSG, e.getMessage());
            secondExceptionThrown = true;
        }
        assertTrue(secondExceptionThrown);
    }
}
