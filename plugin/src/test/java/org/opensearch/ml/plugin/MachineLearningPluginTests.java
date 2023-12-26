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
import static org.mockito.Mockito.when;

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
import org.opensearch.client.Client;
import org.opensearch.ml.common.spi.MLCommonsExtension;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.engine.tools.MLModelTool;
import org.opensearch.plugins.ExtensiblePlugin;
import org.opensearch.plugins.SearchPipelinePlugin;
import org.opensearch.plugins.SearchPlugin;
import org.opensearch.searchpipelines.questionanswering.generative.GenerativeQAProcessorConstants;
import org.opensearch.searchpipelines.questionanswering.generative.GenerativeQARequestProcessor;
import org.opensearch.searchpipelines.questionanswering.generative.GenerativeQAResponseProcessor;
import org.opensearch.searchpipelines.questionanswering.generative.ext.GenerativeQAParamExtBuilder;

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
}
