/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.sentence_transformer;

import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import lombok.extern.log4j.Log4j2;
import org.opensearch.action.ActionListener;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.input.execute.sentence_transformer.SentenceTransformerInput;
import org.opensearch.ml.common.output.Output;
import org.opensearch.ml.common.output.execute.sentence_transformer.SentenceTransformerOutput;
import org.opensearch.ml.engine.Executable;
import org.opensearch.ml.engine.annotation.Function;
import org.opensearch.ml.engine.grpc.DJLModelClient;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.opensearch.ml.engine.MLEngine.DJL_BUILT_IN_MODELS_PATH;
import static org.opensearch.ml.engine.MLEngine.DJL_CACHE_PATH;

@Log4j2
@Function(FunctionName.SENTENCE_TRANSFORMER)
public class SentenceTransformer implements Executable {
    private final Settings settings;
    private final Client client;
    private Predictor<STInput, STOutput> predictor;
    private final int version = 1;
    SentenceTransformerTranslator translator;

    public SentenceTransformer(Client client, Settings settings) {
        this.client = client;
        this.settings = settings;
        this.translator = new SentenceTransformerTranslator();

    }

    @Override
    public Output execute(Input input) {
        SentenceTransformerOutput output = AccessController.doPrivileged((PrivilegedAction<SentenceTransformerOutput>) () -> {
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            try {
                System.setProperty("PYTORCH_PRECXX11", "true");
                System.setProperty("DJL_CACHE_DIR", DJL_CACHE_PATH.toAbsolutePath().toString());
                System.setProperty("java.library.path", DJL_CACHE_PATH.toAbsolutePath().toString());
                Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

                Path outputPath = DJL_BUILT_IN_MODELS_PATH.resolve(version + "").resolve("sentence_transformer");

                if (predictor == null) {
                    Criteria<STInput, STOutput> criteria = Criteria.builder()
                            .setTypes(STInput.class, STOutput.class)
                            .optModelPath(outputPath)
                            .optTranslator(translator)
                            .optEngine("PyTorch")
                            .optProgress(new ProgressBar()).build();
                    ZooModel model = criteria.loadModel();
                    predictor = model.newPredictor(translator);
                }

                SentenceTransformerInput stInput = (SentenceTransformerInput) input;
                List<String> sentences = stInput.getSentences();
                Boolean remoteInference = stInput.getRemoteInference();
                String target = stInput.getGrpcTarget();
                if (remoteInference != null && remoteInference.booleanValue() && target == null) {
                    throw new IllegalArgumentException("Must set endpoint for remote inferencing");
                }
                DJLModelClient djlModelClient = remoteInference ? DJLModelClient.newSentenceTransformerClient(target) : null;

                String index = stInput.getIndex();

                List<String> tempArr = new ArrayList<>();
                BulkRequest bulkRequest = new BulkRequest();
                List<float[]> embeddings = new ArrayList<>();
                boolean hasEffectDocs = false;
                for (int i = 0 ;i<sentences.size() ;i++) {
                    String sentence = sentences.get(i);
                    tempArr.add(sentence);
                    STOutput predictResult;
                    if (remoteInference) {
                        predictResult = djlModelClient.predictSentenceTransformer(tempArr);
                        log.info("Remote inference result size: {}", predictResult.getEmbedding().size());
                    } else {
                        predictResult = predictor.predict(new STInput(tempArr));
                    }
                    embeddings.addAll(predictResult.getEmbedding());

                    float[] embedding = predictResult.getEmbedding().get(0);
                    if (index != null && embedding != null && embedding.length > 0) {
                        IndexRequest indexRequest = new IndexRequest();
                        indexRequest.index(index);
                        Map<String, Object> map = new HashMap<>();
                        map.put("my_vector", embedding);
                        map.put("content", sentence);
                        if (stInput.getDocUrl() != null) {
                            map.put("doc_url", stInput.getDocUrl());
                        }
                        if (stInput.getTopLevel() != null) {
                            map.put("top_level", stInput.getTopLevel());//TODO: remvoe these hard code testing part
                        }
                        if (stInput.getShortAnswer() != null) {
                            map.put("short_answer", stInput.getShortAnswer());//TODO: remvoe these hard code testing part
                        }
                        if (stInput.getQuestions() != null && stInput.getQuestions().size() > 0) {
                            map.put("question", stInput.getQuestions().get(i));//TODO: remvoe these hard code testing part
                        }
                        indexRequest.source(map);
                        bulkRequest.add(indexRequest);
                        hasEffectDocs = true;
                    }

                    tempArr.clear();
                }

                if (index != null && hasEffectDocs) {
                    bulkRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
                    client.bulk(bulkRequest, ActionListener.wrap(r->{
                        log.info("bulk index embeddings successfully");
                    }, e-> {
                        log.error("bulk index embeddings failed", e);
                    }));
                }

                if (stInput.getNotReturnEmbedding()) {
                    return SentenceTransformerOutput.builder().result(new ArrayList<>()).build();
                } else {
                    return SentenceTransformerOutput.builder().result(embeddings).build();
                }

            } catch (Exception e) {
                log.error("Failed to generate embedding", e);
                throw new MLException("Failed to generate embedding");
            } finally {
                Thread.currentThread().setContextClassLoader(contextClassLoader);
            }
        });
        return output;
    }
}
