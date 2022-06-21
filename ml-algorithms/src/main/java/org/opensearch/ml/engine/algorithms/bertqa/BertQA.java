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

import java.nio.file.Paths;
import java.security.AccessController;
import java.security.PrivilegedAction;

@Log4j2
@Function(FunctionName.BERT_QA)
public class BertQA implements Executable {
    private Predictor<QAInput, String> predictor;

    public BertQA() {
    }

    @Override
    public Output execute(Input input) {
        BertQAOutput output = AccessController.doPrivileged((PrivilegedAction<BertQAOutput>) () -> {
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            try {
                System.setProperty("PYTORCH_PRECXX11", "true");
                System.setProperty("DJL_CACHE_DIR", "/djl");
                System.setProperty("java.library.path", "/djl/java");
                Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

                String outputPath = "/djl/pytorch_models/bertqa/";
                String modelPath = outputPath + "bertqa.pt";
                DownloadUtils.download("https://djl-ai.s3.amazonaws.com/mlrepo/model/nlp/question_answer/ai/djl/pytorch/bertqa/0.0.1/trace_bertqa.pt.gz",
                        modelPath, new ProgressBar());

                BertTranslator translator = new BertTranslator();

                String vocabPath = outputPath + "vocab.txt";
                DownloadUtils.download("https://djl-ai.s3.amazonaws.com/mlrepo/model/nlp/question_answer/ai/djl/pytorch/bertqa/0.0.1/bert-base-uncased-vocab.txt.gz",
                        vocabPath, new ProgressBar());


                Criteria<QAInput, String> criteria = Criteria.builder()
                        .setTypes(QAInput.class, String.class)
                        .optModelPath(Paths.get(outputPath))
                        .optTranslator(translator)
                        .optEngine("PyTorch")
                        .optProgress(new ProgressBar()).build();

                ZooModel model = criteria.loadModel();
                String predictResult = null;
                BertQAInput bertQAInput = (BertQAInput) input;
                String question = bertQAInput.getQuestion();
                String resourceDocument = bertQAInput.getDoc();
                QAInput qaInput = new QAInput(question, resourceDocument);

                if (predictor == null) {
                    predictor = model.newPredictor(translator);
                }
                predictResult = predictor.predict(qaInput);
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
