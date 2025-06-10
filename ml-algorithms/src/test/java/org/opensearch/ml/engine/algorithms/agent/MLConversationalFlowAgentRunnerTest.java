/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.MLTask.TASK_ID_FIELD;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.StepListener;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.spi.memory.Memory;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.utils.MLTaskUtils;
import org.opensearch.ml.engine.MLStaticMockBase;
import org.opensearch.transport.client.Client;

import software.amazon.awssdk.utils.ImmutableMap;

public class MLConversationalFlowAgentRunnerTest extends MLStaticMockBase {
    public static final String FIRST_TOOL = "firstTool";
    public static final String SECOND_TOOL = "secondTool";

    @Mock
    private Client client;

    private Settings settings;

    @Mock
    private ClusterService clusterService;

    @Mock
    private NamedXContentRegistry xContentRegistry;

    private Map<String, Tool.Factory> toolFactories;

    @Mock
    private Map<String, Memory.Factory> memoryMap;

    private MLConversationalFlowAgentRunner mlConversationalFlowAgentRunner;

    @Mock
    private Tool.Factory firstToolFactory;

    @Mock
    private Tool.Factory secondToolFactory;

    @Mock
    private Tool firstTool;

    @Mock
    private Tool secondTool;

    @Mock
    private ActionListener<Object> agentActionListener;

    @Captor
    private ArgumentCaptor<Object> objectCaptor;

    @Captor
    private ArgumentCaptor<StepListener<Object>> nextStepListenerCaptor;

    @Before
    @SuppressWarnings("unchecked")
    public void setup() {
        MockitoAnnotations.openMocks(this);
        settings = Settings.builder().build();
        toolFactories = ImmutableMap.of(FIRST_TOOL, firstToolFactory, SECOND_TOOL, secondToolFactory);
        mlConversationalFlowAgentRunner = new MLConversationalFlowAgentRunner(
            client,
            settings,
            clusterService,
            xContentRegistry,
            toolFactories,
            memoryMap,
            null,
            null
        );
        when(firstToolFactory.create(anyMap())).thenReturn(firstTool);
        when(secondToolFactory.create(anyMap())).thenReturn(secondTool);
    }

    @Test
    public void testTaskCancellation() {
        MLToolSpec firstToolSpec = MLToolSpec.builder().name(FIRST_TOOL).type(FIRST_TOOL).build();
        MLToolSpec secondToolSpec = MLToolSpec.builder().name(SECOND_TOOL).type(SECOND_TOOL).build();
        final MLAgent mlAgent = MLAgent
            .builder()
            .name("TestAgent")
            .type(MLAgentType.CONVERSATIONAL_FLOW.name())
            .tools(Arrays.asList(firstToolSpec, secondToolSpec))
            .build();

        final Map<String, String> params = new HashMap<>();
        String taskId = "test-task-id";
        params.put(TASK_ID_FIELD, taskId);

        try (MockedStatic<MLTaskUtils> mlTaskUtilsMockedStatic = mockStatic(MLTaskUtils.class)) {
            mlTaskUtilsMockedStatic.when(() -> MLTaskUtils.isTaskMarkedForCancel(taskId, client)).thenReturn(true);

            mlConversationalFlowAgentRunner.run(mlAgent, params, agentActionListener);

            verify(agentActionListener)
                .onFailure(
                    argThat(
                        exception -> exception instanceof CancellationException
                            && exception.getMessage().equals(String.format("Agent execution cancelled for task: %s", taskId))
                    )
                );
        }
    }
}
