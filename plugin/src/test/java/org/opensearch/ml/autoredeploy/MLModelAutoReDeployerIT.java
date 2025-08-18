/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.autoredeploy;

import static org.opensearch.ml.common.MLTask.MODEL_ID_FIELD;

import java.nio.file.Files;
import java.nio.file.Path;

import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.rest.MLCommonsRestTestCase;

import lombok.SneakyThrows;

public class MLModelAutoReDeployerIT extends MLCommonsRestTestCase {

    public void testModelAutoRedeploy() {
        prepareModel();
    }

    @SneakyThrows
    private void prepareModel() {
        String requestBody = Files
            .readString(
                Path.of(this.getClass().getClassLoader().getResource("org/opensearch/ml/autoredeploy/TracedSmallModelRequest.json").toURI())
            );
        String registerFirstModelTaskId = registerModel(requestBody);
        String registerSecondModelTaskId = registerModel(requestBody);
        waitForTask(registerFirstModelTaskId, MLTaskState.COMPLETED);
        getTask(client(), registerFirstModelTaskId, response -> {
            String firstModelId = (String) response.get(MODEL_ID_FIELD);
            try {
                String deployFirstModelTaskId = deployModel(firstModelId);
                getTask(client(), registerSecondModelTaskId, response1 -> {
                    String secondModelId = (String) response1.get(MODEL_ID_FIELD);
                    try {
                        /**
                         * At this time point, the model auto redeployer should be querying the deploying/deploy failed/partially deployed models.
                         * The original deploy model task should be able to complete successfully, if not it means the
                         * org.opensearch.ml.action.forward.TransportForwardAction.triggerNextModelDeployAndCheckIfRestRetryTimes might throw exception
                         * which cause by org.opensearch.ml.autoredeploy.MLModelAutoReDeployer#redeployAModel. The auto redeploy constructs an arrangement
                         * with two models, the first model deploy done event will trigger the auto redeploy's next model deploy, and if during this
                         * any error occurs, the first model deploy task status won't be updated to complete. So if this IT can pass, then it means the
                         * next model auto redeploy trigger is correct.
                         */
                        String deploySecondModelTaskId = deployModel(secondModelId);
                        waitForTask(deploySecondModelTaskId, MLTaskState.COMPLETED);
                    } catch (Exception e) {
                        fail(e.getMessage());
                    }
                });
                waitForTask(deployFirstModelTaskId, MLTaskState.COMPLETED);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                fail(e.getMessage());
            }
        });
    }

}
