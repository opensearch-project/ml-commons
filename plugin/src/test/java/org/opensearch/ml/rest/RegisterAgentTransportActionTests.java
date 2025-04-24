/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.agent.LLMSpec.MODEL_ID_FIELD;
import static org.opensearch.ml.common.agent.MLAgent.AGENT_NAME_FIELD;
import static org.opensearch.ml.common.agent.MLAgent.AGENT_TYPE_FIELD;
import static org.opensearch.ml.common.agent.MLAgent.CREATED_TIME_FIELD;
import static org.opensearch.ml.common.agent.MLAgent.DESCRIPTION_FIELD;
import static org.opensearch.ml.common.agent.MLAgent.LAST_UPDATED_TIME_FIELD;
import static org.opensearch.ml.common.agent.MLAgent.LLM_FIELD;
import static org.opensearch.ml.common.agent.MLAgent.MEMORY_FIELD;
import static org.opensearch.ml.common.agent.MLAgent.MEMORY_ID_FIELD;
import static org.opensearch.ml.common.agent.MLAgent.PARAMETERS_FIELD;
import static org.opensearch.ml.common.agent.MLAgent.TOOLS_FIELD;
import static org.opensearch.ml.common.agent.MLMemorySpec.MEMORY_TYPE_FIELD;
import static org.opensearch.ml.common.agent.MLMemorySpec.SESSION_ID_FIELD;
import static org.opensearch.ml.common.agent.MLMemorySpec.WINDOW_SIZE_FIELD;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.client.node.NodeClient;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.MLAgentType;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentAction;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentRequest;
import org.opensearch.rest.RestChannel;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;

import com.google.gson.Gson;

public class RegisterAgentTransportActionTests extends OpenSearchTestCase {

    Gson gson;
    Instant time;

    @Mock
    MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        gson = new Gson();
        time = Instant.ofEpochMilli(123);
        when(mlFeatureEnabledSetting.isAgentFrameworkEnabled()).thenReturn(true);
    }

    public void test_GetName_Routes() {
        RestMLRegisterAgentAction action = new RestMLRegisterAgentAction(mlFeatureEnabledSetting);
        assert (action.getName().equals("ml_register_agent_action"));
        List<RestHandler.Route> routes = action.routes();
        assert (routes.size() == 1);
        assert (routes.get(0).equals(new RestHandler.Route(RestRequest.Method.POST, "/_plugins/_ml/agents/_register")));
    }

    public void testPrepareRequest() throws Exception {
        RestMLRegisterAgentAction action = new RestMLRegisterAgentAction(mlFeatureEnabledSetting);
        final Map<String, Object> llmSpec = Map.of(MODEL_ID_FIELD, "id", PARAMETERS_FIELD, new HashMap<>());
        final Map<String, Object> memorySpec = Map.of(MEMORY_TYPE_FIELD, "conversation", SESSION_ID_FIELD, "sid", WINDOW_SIZE_FIELD, 2);
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withContent(
                new BytesArray(
                    gson
                        .toJson(
                            Map
                                .of(
                                    AGENT_NAME_FIELD,
                                    "agent-name",
                                    AGENT_TYPE_FIELD,
                                    MLAgentType.CONVERSATIONAL.name(),
                                    DESCRIPTION_FIELD,
                                    "description",
                                    LLM_FIELD,
                                    llmSpec,
                                    TOOLS_FIELD,
                                    new ArrayList<>(),
                                    PARAMETERS_FIELD,
                                    new HashMap<>(),
                                    MEMORY_FIELD,
                                    memorySpec,
                                    MEMORY_ID_FIELD,
                                    "memory_id",
                                    CREATED_TIME_FIELD,
                                    time.getEpochSecond(),
                                    LAST_UPDATED_TIME_FIELD,
                                    time.getEpochSecond()
                                )
                        )
                ),
                MediaTypeRegistry.JSON
            )
            .build();

        NodeClient client = mock(NodeClient.class);
        RestChannel channel = mock(RestChannel.class);
        action.handleRequest(request, channel, client);

        ArgumentCaptor<MLRegisterAgentRequest> argumentCaptor = ArgumentCaptor.forClass(MLRegisterAgentRequest.class);
        verify(client, times(1)).execute(eq(MLRegisterAgentAction.INSTANCE), argumentCaptor.capture(), any());
        assert (argumentCaptor.getValue().getMlAgent().getName().equals("agent-name"));
        assert (argumentCaptor.getValue().getMlAgent().getType().equals(MLAgentType.CONVERSATIONAL.name()));
        assert (argumentCaptor.getValue().getMlAgent().getDescription().equals("description"));
        assert (argumentCaptor.getValue().getMlAgent().getTools().equals(new ArrayList<>()));
        assert (argumentCaptor.getValue().getMlAgent().getLlm().getModelId().equals("id"));
        assert (argumentCaptor.getValue().getMlAgent().getParameters().equals(new HashMap<>()));
        assert (argumentCaptor.getValue().getMlAgent().getMemory().getType().equals("conversation"));
    }

    public void testPrepareRequest_disabled() {
        when(mlFeatureEnabledSetting.isAgentFrameworkEnabled()).thenReturn(false);
        RestMLRegisterAgentAction action = new RestMLRegisterAgentAction(mlFeatureEnabledSetting);
        final Map<String, Object> llmSpec = Map.of(MODEL_ID_FIELD, "id", PARAMETERS_FIELD, new HashMap<>());
        final Map<String, Object> memorySpec = Map.of(MEMORY_TYPE_FIELD, "conversation", SESSION_ID_FIELD, "sid", WINDOW_SIZE_FIELD, 2);
        RestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
                .withContent(
                        new BytesArray(
                                gson
                                        .toJson(
                                                Map
                                                        .of(
                                                                AGENT_NAME_FIELD,
                                                                "agent-name",
                                                                AGENT_TYPE_FIELD,
                                                                "agent-type",
                                                                DESCRIPTION_FIELD,
                                                                "description",
                                                                LLM_FIELD,
                                                                llmSpec,
                                                                TOOLS_FIELD,
                                                                new ArrayList<>(),
                                                                PARAMETERS_FIELD,
                                                                new HashMap<>(),
                                                                MEMORY_FIELD,
                                                                memorySpec,
                                                                MEMORY_ID_FIELD,
                                                                "memory_id",
                                                                CREATED_TIME_FIELD,
                                                                time.getEpochSecond(),
                                                                LAST_UPDATED_TIME_FIELD,
                                                                time.getEpochSecond()
                                                        )
                                        )
                        ),
                        MediaTypeRegistry.JSON
                )
                .build();

        NodeClient client = mock(NodeClient.class);
        RestChannel channel = mock(RestChannel.class);

        assertThrows(IllegalStateException.class, () -> action.handleRequest(request, channel, client));
    }
}
