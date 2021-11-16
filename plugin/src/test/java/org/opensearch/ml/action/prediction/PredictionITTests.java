package org.opensearch.ml.action.prediction;

import static org.opensearch.ml.utils.IntegTestUtils.DATA_FRAME_INPUT_DATASET;
import static org.opensearch.ml.utils.IntegTestUtils.TESTING_DATA;
import static org.opensearch.ml.utils.IntegTestUtils.TESTING_INDEX_NAME;
import static org.opensearch.ml.utils.IntegTestUtils.generateEmptyDataset;
import static org.opensearch.ml.utils.IntegTestUtils.generateMLTestingData;
import static org.opensearch.ml.utils.IntegTestUtils.generateSearchSourceBuilder;
import static org.opensearch.ml.utils.IntegTestUtils.predictAndVerifyResult;
import static org.opensearch.ml.utils.IntegTestUtils.trainModel;
import static org.opensearch.ml.utils.IntegTestUtils.verifyGeneratedTestingData;
import static org.opensearch.ml.utils.IntegTestUtils.waitModelAvailable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

import org.junit.Before;
import org.junit.Ignore;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.ActionFuture;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.SearchQueryInputDataset;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskResponse;
import org.opensearch.ml.plugin.MachineLearningPlugin;
import org.opensearch.plugins.Plugin;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.test.OpenSearchIntegTestCase;

@OpenSearchIntegTestCase.ClusterScope(transportClientRatio = 0.9)
@Ignore("Test cases in this class are flaky, something is off with waitModelAvailable(taskId) method."
    + " This issue will be tracked in an issue and will be fixed later")
public class PredictionITTests extends OpenSearchIntegTestCase {
    private String taskId;

    @Before
    public void initTestingData() throws ExecutionException, InterruptedException {
        generateMLTestingData();

        SearchSourceBuilder searchSourceBuilder = generateSearchSourceBuilder();
        MLInputDataset inputDataset = new SearchQueryInputDataset(Collections.singletonList(TESTING_INDEX_NAME), searchSourceBuilder);
        taskId = trainModel(inputDataset);
        waitModelAvailable(taskId);
    }

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return Collections.singletonList(MachineLearningPlugin.class);
    }

    @Override
    protected Collection<Class<? extends Plugin>> transportClientPlugins() {
        return Collections.singletonList(MachineLearningPlugin.class);
    }

    public void testTestingData() throws ExecutionException, InterruptedException {
        verifyGeneratedTestingData(TESTING_DATA);
        waitModelAvailable(taskId);
    }

    public void testPredictionWithSearchInput() throws IOException {
        SearchSourceBuilder searchSourceBuilder = generateSearchSourceBuilder();
        MLInputDataset inputDataset = new SearchQueryInputDataset(Collections.singletonList(TESTING_INDEX_NAME), searchSourceBuilder);

        predictAndVerifyResult(taskId, inputDataset);
    }

    public void testPredictionWithDataInput() throws IOException {
        predictAndVerifyResult(taskId, DATA_FRAME_INPUT_DATASET);
    }

    public void testPredictionWithoutAlgorithm() throws IOException {
        MLPredictionTaskRequest predictionRequest = new MLPredictionTaskRequest("", new ArrayList<>(), taskId, DATA_FRAME_INPUT_DATASET);
        ActionFuture<MLPredictionTaskResponse> predictionFuture = client().execute(MLPredictionTaskAction.INSTANCE, predictionRequest);
        expectThrows(ActionRequestValidationException.class, () -> predictionFuture.actionGet());
    }

    public void testPredictionWithoutModelId() throws IOException {
        MLPredictionTaskRequest predictionRequest = new MLPredictionTaskRequest("kmeans", new ArrayList<>(), "", DATA_FRAME_INPUT_DATASET);
        ActionFuture<MLPredictionTaskResponse> predictionFuture = client().execute(MLPredictionTaskAction.INSTANCE, predictionRequest);
        expectThrows(ResourceNotFoundException.class, () -> predictionFuture.actionGet());
    }

    public void testPredictionWithoutDataset() throws IOException {
        MLPredictionTaskRequest predictionRequest = new MLPredictionTaskRequest("kmeans", new ArrayList<>(), taskId, null);
        ActionFuture<MLPredictionTaskResponse> predictionFuture = client().execute(MLPredictionTaskAction.INSTANCE, predictionRequest);
        expectThrows(ActionRequestValidationException.class, () -> predictionFuture.actionGet());
    }

    public void testPredictionWithEmptyDataset() throws IOException {
        MLInputDataset emptySearchInputDataset = generateEmptyDataset();
        MLPredictionTaskRequest predictionRequest = new MLPredictionTaskRequest(
            "kmeans",
            new ArrayList<>(),
            taskId,
            emptySearchInputDataset
        );
        ActionFuture<MLPredictionTaskResponse> predictionFuture = client().execute(MLPredictionTaskAction.INSTANCE, predictionRequest);
        expectThrows(IllegalArgumentException.class, () -> predictionFuture.actionGet());
    }
}
