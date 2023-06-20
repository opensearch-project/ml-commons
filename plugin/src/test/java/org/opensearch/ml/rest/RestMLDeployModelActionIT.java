package org.opensearch.ml.rest;

import org.apache.hc.core5.http.HttpEntity;
import org.junit.Before;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.utils.TestHelper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.opensearch.ml.common.MLTask.MODEL_ID_FIELD;
import static org.opensearch.ml.common.MLTask.STATE_FIELD;

public class RestMLDeployModelActionIT extends MLCommonsRestTestCase {
    private MLRegisterModelInput registerModelInput;

    @Before
    public void setup() {
        registerModelInput = createRegisterModelInput();
    }
    public void testReDeployModel() throws InterruptedException, IOException {
        // Create and register Model
        String taskId = registerModel(TestHelper.toJsonString(registerModelInput));
        waitForTask(taskId, MLTaskState.COMPLETED);
        getTask(client(), taskId, response -> {
                String model_id = (String) response.get(MODEL_ID_FIELD);
                logger.info("Model ID: {}", model_id);
                try {
                    // Deploy Model
                    String taskId1 = deployModel(model_id);
                    getTask(client(), taskId1, innerResponse -> {
                        assertEquals(model_id, innerResponse.get(MODEL_ID_FIELD));
                    });
                    Thread.sleep(300);

                    // Undeploy Model
                    Map<String, Object> undeployresponse = undeployModel(model_id);
                    for (Map.Entry<String, Object> entry : undeployresponse.entrySet()) {
                        Map stats = (Map) ((Map) entry.getValue()).get("stats");
                        assertEquals("undeployed", stats.get(undeployresponse));
                    }

                    // Deploy Model again
                    taskId1 = deployModel(model_id);
                    getTask(client(), taskId1, innerResponse -> {
                        logger.info("Re-Deploy model {}", innerResponse);
                    });
                    waitForTask(taskId1, MLTaskState.FAILED);

                    getModel(client(), model_id, model ->{
                        logger.info("Get Model after re-deploy {}", model);
                        // Re-Deploy fails due to https://github.com/opensearch-project/ml-commons/issues/844
                        assertEquals("DEPLOY_FAILED",model.get("model_state"));
                    });

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
        });
    }
}
