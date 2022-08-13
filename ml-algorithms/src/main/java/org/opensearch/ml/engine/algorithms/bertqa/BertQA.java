/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.bertqa;

import ai.djl.inference.Predictor;
import ai.djl.modality.nlp.qa.QAInput;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.DownloadUtils;
import ai.djl.training.util.ProgressBar;
import lombok.extern.log4j.Log4j2;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.input.execute.bertqa.BertQAInput;
import org.opensearch.ml.common.output.Output;
import org.opensearch.ml.common.output.execute.bertqa.BertQAOutput;
import org.opensearch.ml.engine.Executable;
import org.opensearch.ml.engine.annotation.Function;
import org.opensearch.ml.engine.grpc.DJLModelClient;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.PrivilegedAction;

import static org.opensearch.ml.engine.MLEngine.DJL_BUILT_IN_MODELS_PATH;
import static org.opensearch.ml.engine.MLEngine.DJL_CACHE_PATH;

@Log4j2
@Function(FunctionName.BERT_QA)
public class BertQA implements Executable {
    private Predictor<QAInput, String> predictor;
    private final int version = 1;

    public BertQA() {
    }

    @Override
    public Output execute(Input input) {
        BertQAOutput output = AccessController.doPrivileged((PrivilegedAction<BertQAOutput>) () -> {
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            try {
                System.setProperty("PYTORCH_PRECXX11", "true");
                System.setProperty("DJL_CACHE_DIR", DJL_CACHE_PATH.toAbsolutePath().toString());
                System.setProperty("java.library.path", DJL_CACHE_PATH.toAbsolutePath().toString());
                Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

                String predictResult = null;
                BertQAInput bertQAInput = (BertQAInput) input;
                String question = bertQAInput.getQuestion();
                String resourceDocument = bertQAInput.getDoc();

                Boolean remoteInference = bertQAInput.getRemoteInference();
                String target = bertQAInput.getGrpcTarget();
                if (remoteInference != null && remoteInference.booleanValue() && target == null) {
                    throw new IllegalArgumentException("Must set endpoint for remote inferencing");
                }
                DJLModelClient djlModelClient = remoteInference ? DJLModelClient.newSentenceTransformerClient(target) : null;

                QAInput qaInput = new QAInput(question, resourceDocument);
                if (predictor == null && remoteInference == false) {
                    Path outputPath = DJL_BUILT_IN_MODELS_PATH.resolve(version + "").resolve("bertqa");
                    DownloadUtils.download("https://djl-ai.s3.amazonaws.com/mlrepo/model/nlp/question_answer/ai/djl/pytorch/bertqa/0.0.1/trace_bertqa.pt.gz",
                            outputPath.resolve("bertqa.pt").toString(), new ProgressBar());
                    DownloadUtils.download("https://djl-ai.s3.amazonaws.com/mlrepo/model/nlp/question_answer/ai/djl/pytorch/bertqa/0.0.1/bert-base-uncased-vocab.txt.gz",
                            outputPath.resolve("vocab.txt").toString(), new ProgressBar());

                    BertQATranslator translator = new BertQATranslator();
                    Criteria<QAInput, String> criteria = Criteria.builder()
                            .setTypes(QAInput.class, String.class)
                            .optModelPath(outputPath)
                            .optTranslator(translator)
                            .optEngine("PyTorch")
                            .optProgress(new ProgressBar()).build();

                    ZooModel model = criteria.loadModel();
                    predictor = model.newPredictor(translator);
                }
                if (remoteInference) {
                    log.info("execute berqa on remote server " + target);
                    predictResult = djlModelClient.findAnswer(question, resourceDocument);
                } else {
                    log.info("execute berqa locally");
                    predictResult = predictor.predict(qaInput);
                }
                return BertQAOutput.builder().result(predictResult).build();
            } catch (Exception e) {
                log.error("Failed to detect object from image", e);
                throw new MLException("Failed to detect object");
            } finally {
                Thread.currentThread().setContextClassLoader(contextClassLoader);
            }
        });
        return output;
    }
}
