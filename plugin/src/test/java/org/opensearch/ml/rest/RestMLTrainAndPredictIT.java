package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import lombok.NonNull;

import org.apache.http.HttpEntity;
import org.opensearch.client.Response;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.SearchQueryInputDataset;
import org.opensearch.ml.common.parameter.FunctionName;
import org.opensearch.ml.common.parameter.KMeansParams;
import org.opensearch.ml.common.parameter.MLInput;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.search.builder.SearchSourceBuilder;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;

public class RestMLTrainAndPredictIT extends MLCommonsRestTestCase {
    private String irisIndex = "iris_data";
    private Gson gson = new Gson();

    public void testTrainAndPredictKmeans() throws IOException {
        ingestIrisData(irisIndex);
        KMeansParams params = KMeansParams.builder().centroids(3).build();
        @NonNull
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(new MatchAllQueryBuilder());
        sourceBuilder.size(1000);
        sourceBuilder.fetchSource(new String[] { "petal_length_in_cm", "petal_width_in_cm" }, null);
        MLInputDataset inputData = SearchQueryInputDataset
            .builder()
            .indices(ImmutableList.of(irisIndex))
            .searchSourceBuilder(sourceBuilder)
            .build();
        trainAndPredictKmeansWithIrisData(params, inputData, clusterCount -> {
            if (clusterCount.size() == 3) {
                for (Map.Entry<Double, Integer> entry : clusterCount.entrySet()) {
                    assertEquals(50, entry.getValue(), 5);
                }
            }
        });
    }

    public void testTrainAndPredictKmeansWithEmptyParam() throws IOException {
        ingestIrisData(irisIndex);
        KMeansParams params = KMeansParams.builder().build();
        @NonNull
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(new MatchAllQueryBuilder());
        sourceBuilder.size(1000);
        sourceBuilder.fetchSource(new String[] { "petal_length_in_cm", "petal_width_in_cm" }, null);
        MLInputDataset inputData = SearchQueryInputDataset
            .builder()
            .indices(ImmutableList.of(irisIndex))
            .searchSourceBuilder(sourceBuilder)
            .build();
        trainAndPredictKmeansWithIrisData(params, inputData, clusterCount -> { assertEquals(2, clusterCount.size()); });
    }

    private void trainAndPredictKmeansWithIrisData(KMeansParams params, MLInputDataset inputData, Consumer<Map<Double, Integer>> function)
        throws IOException {
        MLInput kmeansInput = MLInput.builder().algorithm(FunctionName.KMEANS).parameters(params).inputDataset(inputData).build();
        Response kmeansResponse = TestHelper
            .makeRequest(
                client(),
                "POST",
                "/_plugins/_ml/_train_predict/kmeans",
                ImmutableMap.of(),
                TestHelper.toHttpEntity(kmeansInput),
                null
            );
        HttpEntity entity = kmeansResponse.getEntity();
        assertNotNull(kmeansResponse);
        String entityString = TestHelper.httpEntityToString(entity);
        Map map = gson.fromJson(entityString, Map.class);
        Map predictionResult = (Map) map.get("prediction_result");
        ArrayList rows = (ArrayList) predictionResult.get("rows");
        Map<Double, Integer> clusterCount = new HashMap<>();
        for (Object obj : rows) {
            Double value = (Double) ((Map) ((ArrayList) ((Map) obj).get("values")).get(0)).get("value");
            if (!clusterCount.containsKey(value)) {
                clusterCount.put(value, 1);
            } else {
                Integer count = clusterCount.get(value);
                clusterCount.put(value, ++count);
            }
        }
        function.accept(clusterCount);
    }
}
