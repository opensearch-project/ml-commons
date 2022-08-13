/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.grpc;

import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.engine.algorithms.sentence_transformer.STOutput;
import org.opensearch.ml.serving.bertqa.BertQaGrpc;
import org.opensearch.ml.serving.bertqa.BertQaRequest;
import org.opensearch.ml.serving.bertqa.BertQaResponse;
import org.opensearch.ml.serving.st.SentenceTransformerGrpc;
import org.opensearch.ml.serving.st.TransformSentenceRequest;
import org.opensearch.ml.serving.st.TransformSentenceResponse;
import org.opensearch.ml.serving.st.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DJLModelClient {
    private static final Logger logger = Logger.getLogger(DJLModelClient.class.getName());

    private final SentenceTransformerGrpc.SentenceTransformerBlockingStub sentenceTransformerBlockingStub;
    private final BertQaGrpc.BertQaBlockingStub bertQaBlockingStub;

    /**
     * Construct client for accessing HelloWorld server using the existing channel.
     */
    public DJLModelClient(Channel channel) {
        sentenceTransformerBlockingStub = SentenceTransformerGrpc.newBlockingStub(channel);
        bertQaBlockingStub = BertQaGrpc.newBlockingStub(channel);
    }

    public static DJLModelClient newSentenceTransformerClient(String target) {
        ManagedChannel channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
        return new DJLModelClient(channel);
    }

    public STOutput predictSentenceTransformer(List<String> sentences) {
        TransformSentenceRequest request = TransformSentenceRequest.newBuilder().addAllSentence(sentences).build();
        TransformSentenceResponse response;
        try {
            response = sentenceTransformerBlockingStub.transformSentence(request);
            List<Vector> vectorsList = response.getVectorsList();
            List<float[]> embeddings = new ArrayList<>();
            for (Vector vector : vectorsList) {
                List<Float> vectorList = vector.getVectorList();
                float[] embedding = new float[vectorList.size()];
                for (int i = 0; i < vectorList.size(); i++) {
                    embedding[i] = vectorList.get(i).floatValue();
                }
                embeddings.add(embedding);
            }
            STOutput output = new STOutput(embeddings);
            return output;
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            return null;
        }
    }

    public String findAnswer(String question, String doc) {
        BertQaRequest request = BertQaRequest.newBuilder().setQuestion(question).setDoc(doc).build();
        BertQaResponse response;
        try {
            response = bertQaBlockingStub.findAnswer(request);
        } catch (StatusRuntimeException e) {
            logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
            throw new MLException("Failed to call BertQA on remote server");
        }
        String answer = response.getAnswer();
        logger.info("Answer: " + answer);
        return answer;
    }

}
