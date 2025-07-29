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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.xcontent.DeprecationHandler;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.jobscheduler.spi.JobDocVersion;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.spi.MLCommonsExtension;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.engine.tools.MLModelTool;
import org.opensearch.ml.jobs.MLJobParameter;
import org.opensearch.ml.jobs.MLJobRunner;
import org.opensearch.ml.processor.MLInferenceSearchRequestProcessor;
import org.opensearch.ml.processor.MLInferenceSearchResponseProcessor;
import org.opensearch.ml.searchext.MLInferenceRequestParametersExtBuilder;
import org.opensearch.plugins.ExtensiblePlugin;
import org.opensearch.plugins.SearchPipelinePlugin;
import org.opensearch.plugins.SearchPlugin;
import org.opensearch.searchpipelines.questionanswering.generative.GenerativeQAProcessorConstants;
import org.opensearch.searchpipelines.questionanswering.generative.GenerativeQARequestProcessor;
import org.opensearch.searchpipelines.questionanswering.generative.GenerativeQAResponseProcessor;
import org.opensearch.searchpipelines.questionanswering.generative.ext.GenerativeQAParamExtBuilder;
import org.opensearch.transport.client.Client;

public class MachineLearningPluginTests {

    MachineLearningPlugin plugin = new MachineLearningPlugin();

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Mock
    Client client;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testGetSearchExts() {
        List<SearchPlugin.SearchExtSpec<?>> searchExts = plugin.getSearchExts();
        assertEquals(2, searchExts.size());
        SearchPlugin.SearchExtSpec<?> spec1 = searchExts.get(0);
        assertEquals(GenerativeQAParamExtBuilder.PARAMETER_NAME, spec1.getName().getPreferredName());
        SearchPlugin.SearchExtSpec<?> spec2 = searchExts.get(1);
        assertEquals(MLInferenceRequestParametersExtBuilder.NAME, spec2.getName().getPreferredName());
    }

    @Test
    public void testGetRequestProcessors() {
        SearchPipelinePlugin.Parameters parameters = mock(SearchPipelinePlugin.Parameters.class);
        Map<String, ?> requestProcessors = plugin.getRequestProcessors(parameters);
        assertEquals(2, requestProcessors.size());
        assertTrue(
            requestProcessors.get(GenerativeQAProcessorConstants.REQUEST_PROCESSOR_TYPE) instanceof GenerativeQARequestProcessor.Factory
        );
        assertTrue(requestProcessors.get(MLInferenceSearchRequestProcessor.TYPE) instanceof MLInferenceSearchRequestProcessor.Factory);
    }

    @Test
    public void testGetResponseProcessors() {
        SearchPipelinePlugin.Parameters parameters = mock(SearchPipelinePlugin.Parameters.class);
        Map<String, ?> responseProcessors = plugin.getResponseProcessors(parameters);
        assertEquals(2, responseProcessors.size());
        assertTrue(
            responseProcessors.get(GenerativeQAProcessorConstants.RESPONSE_PROCESSOR_TYPE) instanceof GenerativeQAResponseProcessor.Factory
        );
        assertTrue(responseProcessors.get(MLInferenceSearchResponseProcessor.TYPE) instanceof MLInferenceSearchResponseProcessor.Factory);
    }

    @Test
    public void testLoadExtensionsWithNoExtensiblePlugin() {
        ExtensiblePlugin.ExtensionLoader loader = mock(ExtensiblePlugin.ExtensionLoader.class);
        when(loader.loadExtensions(MLCommonsExtension.class)).thenReturn(new ArrayList<>());
        plugin.loadExtensions(loader);
        assertEquals(0, plugin.externalToolFactories.size());
    }

    @Test
    public void testLoadExtensionsWithExtensiblePluginNoToolFactory() {
        ExtensiblePlugin.ExtensionLoader loader = mock(ExtensiblePlugin.ExtensionLoader.class);
        MLCommonsExtension extension = mock(MLCommonsExtension.class);
        when(extension.getToolFactories()).thenReturn(new ArrayList<>());
        when(loader.loadExtensions(MLCommonsExtension.class)).thenReturn(Arrays.asList(extension));
        plugin.loadExtensions(loader);
        assertEquals(0, plugin.externalToolFactories.size());
    }

    @Test
    public void testLoadExtensionsWithExtensiblePluginAndWrongToolFactory() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Missing ToolAnnotation for Tool DummyWrongTool");
        ExtensiblePlugin.ExtensionLoader loader = mock(ExtensiblePlugin.ExtensionLoader.class);
        MLCommonsExtension extension = mock(MLCommonsExtension.class);
        DummyWrongTool.Factory.getInstance().init();
        Tool.Factory<? extends Tool> factory = DummyWrongTool.Factory.getInstance();
        List<Tool.Factory<? extends Tool>> toolFactories = Arrays.asList(factory);
        when(extension.getToolFactories()).thenReturn(toolFactories);
        when(loader.loadExtensions(MLCommonsExtension.class)).thenReturn(Arrays.asList(extension));
        plugin.loadExtensions(loader);
        assertEquals(0, plugin.externalToolFactories.size());
    }

    @Test
    public void testLoadExtensionsWithExtensiblePluginAndCorrectToolFactory() {
        ExtensiblePlugin.ExtensionLoader loader = mock(ExtensiblePlugin.ExtensionLoader.class);
        MLCommonsExtension extension = mock(MLCommonsExtension.class);
        MLModelTool.Factory.getInstance().init(client);
        Tool.Factory<? extends Tool> factory = MLModelTool.Factory.getInstance();
        List<Tool.Factory<? extends Tool>> toolFactories = Arrays.asList(factory);
        when(extension.getToolFactories()).thenReturn(toolFactories);
        when(loader.loadExtensions(MLCommonsExtension.class)).thenReturn(Arrays.asList(extension));
        plugin.loadExtensions(loader);
        assertEquals(1, plugin.externalToolFactories.size());
        assertEquals(
            MLModelTool.Factory.getInstance().getDefaultDescription(),
            plugin.externalToolFactories.get("MLModelTool").getDefaultDescription()
        );
    }

    @Test
    public void testGetJobType() {
        assertEquals(MachineLearningPlugin.ML_COMMONS_JOBS_TYPE, plugin.getJobType());
    }

    @Test
    public void testGetJobIndex() {
        assertEquals(CommonValue.ML_JOBS_INDEX, plugin.getJobIndex());
    }

    @Test
    public void testGetJobRunner() {
        assertTrue(plugin.getJobRunner() instanceof MLJobRunner);
    }

    @Test
    public void testGetJobParser() {
        assertNotNull(plugin.getJobParser());
    }

    @Test
    public void testGetJobParserWithInvalidJson() throws IOException {
        String invalidJson = "{ invalid json }";
        XContentParser parser = JsonXContent.jsonXContent.createParser(null, null, invalidJson);
        exceptionRule.expect(IOException.class);
        plugin.getJobParser().parse(parser, "test_id", new JobDocVersion(1, 0, 0));
    }

    @Test
    public void testGetJobParserWithValidJson() throws IOException {
        String json = "{"
            + "\"name\": \"testJob\","
            + "\"enabled\": true,"
            + "\"enabled_time\": 1672531200000,"
            + "\"last_update_time\": 1672534800000,"
            + "\"lock_duration_seconds\": 300,"
            + "\"jitter\": 0.1"
            + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, DeprecationHandler.THROW_UNSUPPORTED_OPERATION, json);

        MLJobParameter parsedJobParameter = (MLJobParameter) plugin.getJobParser().parse(parser, "test_id", new JobDocVersion(1, 0, 0));

        assertEquals("testJob", parsedJobParameter.getName());
        assertTrue(parsedJobParameter.isEnabled());
        assertEquals(Long.valueOf(1672531200000L), Long.valueOf(parsedJobParameter.getEnabledTime().toEpochMilli()));
        assertEquals(Long.valueOf(1672534800000L), Long.valueOf(parsedJobParameter.getLastUpdateTime().toEpochMilli()));
        assertEquals(Long.valueOf(300L), Long.valueOf(parsedJobParameter.getLockDurationSeconds()));
        assertEquals(Double.valueOf(0.1), Double.valueOf(parsedJobParameter.getJitter()), 0.0001);
    }
}
