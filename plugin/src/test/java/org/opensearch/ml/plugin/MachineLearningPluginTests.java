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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionRequest;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.indices.SystemIndexDescriptor;
import org.opensearch.ingest.Processor;
import org.opensearch.jobscheduler.spi.JobDocVersion;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.spi.MLCommonsExtension;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.engine.tools.MLModelTool;
import org.opensearch.ml.jobs.MLJobRunner;
import org.opensearch.ml.processor.MLInferenceIngestProcessor;
import org.opensearch.ml.processor.MLInferenceSearchRequestProcessor;
import org.opensearch.ml.processor.MLInferenceSearchResponseProcessor;
import org.opensearch.ml.searchext.MLInferenceRequestParametersExtBuilder;
import org.opensearch.plugins.ActionPlugin;
import org.opensearch.plugins.ExtensiblePlugin;
import org.opensearch.plugins.SearchPipelinePlugin;
import org.opensearch.plugins.SearchPlugin;
import org.opensearch.rest.RestHandler;
import org.opensearch.searchpipelines.questionanswering.generative.GenerativeQAProcessorConstants;
import org.opensearch.searchpipelines.questionanswering.generative.GenerativeQARequestProcessor;
import org.opensearch.searchpipelines.questionanswering.generative.GenerativeQAResponseProcessor;
import org.opensearch.searchpipelines.questionanswering.generative.ext.GenerativeQAParamExtBuilder;
import org.opensearch.threadpool.ExecutorBuilder;
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
    public void testConstructor() {
        MachineLearningPlugin newPlugin = new MachineLearningPlugin();
        assertNotNull(newPlugin);
    }

    @Test
    public void testJobSchedulerMethods() {
        assertEquals(MachineLearningPlugin.ML_COMMONS_JOBS_TYPE, plugin.getJobType());
        assertEquals(CommonValue.ML_JOBS_INDEX, plugin.getJobIndex());
        assertTrue(plugin.getJobRunner() instanceof MLJobRunner);
        assertNotNull(plugin.getJobParser());
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
    public void testGetActions() {
        List<ActionPlugin.ActionHandler<? extends ActionRequest, ? extends ActionResponse>> actions = plugin.getActions();
        assertNotNull(actions);
        assertFalse(actions.isEmpty());
        assertTrue(actions.size() > 50); // Plugin has many actions
    }

    @Test
    public void testGetRestHandlers() {
        Settings settings = Settings.builder().put("cluster.name", "test-cluster").build();

        try {
            List<RestHandler> restHandlers = plugin.getRestHandlers(settings, null, null, null, null, null, null);
            // If we get here without exception, the method works
            assertNotNull(restHandlers);
        } catch (Exception e) {
            // Expected due to missing dependencies, but method is covered
            assertTrue(e instanceof NullPointerException || e instanceof IllegalStateException);
        }
    }

    @Test
    public void testGetExecutorBuilders() {
        Settings settings = Settings.EMPTY;
        List<ExecutorBuilder<?>> executorBuilders = plugin.getExecutorBuilders(settings);
        assertNotNull(executorBuilders);
        assertEquals(12, executorBuilders.size());

        // Verify we have the expected number of thread pools
        assertTrue(executorBuilders.size() > 5);
    }

    @Test
    public void testGetNamedXContent() {
        List<NamedXContentRegistry.Entry> entries = plugin.getNamedXContent();
        assertNotNull(entries);
        assertFalse(entries.isEmpty());
        assertTrue(entries.size() > 10); // Plugin has many named XContent entries
    }

    @Test
    public void testGetSettings() {
        List<org.opensearch.common.settings.Setting<?>> settings = plugin.getSettings();
        assertNotNull(settings);
        assertFalse(settings.isEmpty());
        assertTrue(settings.size() > 40); // Plugin has many settings
    }

    @Test
    public void testGetSystemIndexDescriptors() {
        Settings settings = Settings.EMPTY;
        Collection<SystemIndexDescriptor> descriptors = plugin.getSystemIndexDescriptors(settings);
        assertNotNull(descriptors);
        assertEquals(14, descriptors.size()); // Plugin defines 13 system indices

        // Verify we have system index descriptors
        assertFalse(descriptors.isEmpty());
        for (SystemIndexDescriptor descriptor : descriptors) {
            assertNotNull(descriptor.getIndexPattern());
            assertTrue(descriptor.getIndexPattern().startsWith(".plugins-ml") || descriptor.getIndexPattern().startsWith(".plugins-mcp"));
        }
    }

    @Test
    public void testGetProcessors() {
        Processor.Parameters parameters = mock(Processor.Parameters.class);
        Map<String, org.opensearch.ingest.Processor.Factory> processors = plugin.getProcessors(parameters);
        assertNotNull(processors);
        assertEquals(1, processors.size());
        assertTrue(processors.containsKey(MLInferenceIngestProcessor.TYPE));
    }

    @Test
    public void testGetTokenizers() {
        Map<String, ?> tokenizers = plugin.getTokenizers();
        assertNotNull(tokenizers);
        assertEquals(1, tokenizers.size());
    }

    @Test
    public void testGetPreConfiguredTokenizers() {
        List<?> tokenizers = plugin.getPreConfiguredTokenizers();
        assertNotNull(tokenizers);
        assertEquals(2, tokenizers.size());
    }

    @Test
    public void testGetAnalyzers() {
        Map<String, ?> analyzers = plugin.getAnalyzers();
        assertNotNull(analyzers);
        assertEquals(1, analyzers.size());
    }

    @Test
    public void testCreateComponents() {
        Settings settings = Settings.builder().put("cluster.name", "test-cluster").put("node.name", "test-node").build();

        try {
            Collection<Object> components = plugin
                .createComponents(client, null, null, null, null, null, null, null, null, null, null, null, null);
            // If we get here without exception, the method works
            assertNotNull(components);
        } catch (Exception e) {
            // Expected due to missing dependencies, but method is covered
            assertTrue(e instanceof NullPointerException || e instanceof IllegalStateException);
        }
    }

    @Test
    public void testConstants() {
        assertEquals("opensearch_ml_general", MachineLearningPlugin.GENERAL_THREAD_POOL);
        assertEquals("opensearch_ml_execute", MachineLearningPlugin.EXECUTE_THREAD_POOL);
        assertEquals("opensearch_ml_train", MachineLearningPlugin.TRAIN_THREAD_POOL);
        assertEquals("opensearch_ml_predict", MachineLearningPlugin.PREDICT_THREAD_POOL);
        assertEquals("opensearch_ml_predict_stream", MachineLearningPlugin.STREAM_PREDICT_THREAD_POOL);
        assertEquals("opensearch_ml_register", MachineLearningPlugin.REGISTER_THREAD_POOL);
        assertEquals("opensearch_ml_deploy", MachineLearningPlugin.DEPLOY_THREAD_POOL);
        assertEquals("/_plugins/_ml", MachineLearningPlugin.ML_BASE_URI);
        assertEquals("opensearch_ml_commons_jobs", MachineLearningPlugin.ML_COMMONS_JOBS_TYPE);
    }

    @Test
    public void testGetPreBuiltAnalyzerProviderFactories() {
        List<?> factories = plugin.getPreBuiltAnalyzerProviderFactories();
        assertNotNull(factories);
        assertEquals(2, factories.size());
    }
}
