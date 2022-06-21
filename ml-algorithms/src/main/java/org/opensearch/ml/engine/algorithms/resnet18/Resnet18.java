/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.resnet18;

import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.modality.cv.transform.CenterCrop;
import ai.djl.modality.cv.transform.Normalize;
import ai.djl.modality.cv.transform.Resize;
import ai.djl.modality.cv.transform.ToTensor;
import ai.djl.modality.cv.translator.ImageClassificationTranslator;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.DownloadUtils;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.Translator;
import lombok.extern.log4j.Log4j2;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.input.execute.resnet18.Resnet18Input;
import org.opensearch.ml.common.output.Output;
import org.opensearch.ml.common.output.execute.resnet18.Resnet18Output;
import org.opensearch.ml.engine.Executable;
import org.opensearch.ml.engine.annotation.Function;

import java.nio.file.Paths;
import java.security.AccessController;
import java.security.PrivilegedAction;

@Log4j2
@Function(FunctionName.RESNET18)
public class Resnet18 implements Executable {
    private Predictor<Image, Classifications> predictor;

    public Resnet18() {
    }

    @Override
    public Output execute(Input input) {
        Resnet18Output output = AccessController.doPrivileged((PrivilegedAction<Resnet18Output>) () -> {
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            try {
                System.setProperty("PYTORCH_PRECXX11", "true");
                System.setProperty("DJL_CACHE_DIR", "/djl");
                System.setProperty("java.library.path", "/djl/java");
                Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

                String outputPath = "/djl/pytorch_models/resnet18/";
                String modelPath = outputPath + "resnet18.pt";
                DownloadUtils.download("https://djl-ai.s3.amazonaws.com/mlrepo/model/cv/image_classification/ai/djl/pytorch/resnet/0.0.1/traced_resnet18.pt.gz",
                        modelPath, new ProgressBar());

                String synsetPath = outputPath + "synset.txt";
                DownloadUtils.download("https://djl-ai.s3.amazonaws.com/mlrepo/model/cv/image_classification/ai/djl/pytorch/synset.txt",
                        synsetPath, new ProgressBar());

                Translator<Image, Classifications> translator = ImageClassificationTranslator.builder()
                        .addTransform(new Resize(256))
                        .addTransform(new CenterCrop(224, 224))
                        .addTransform(new ToTensor())
                        .addTransform(new Normalize(
                                new float[] {0.485f, 0.456f, 0.406f},
                                new float[] {0.229f, 0.224f, 0.225f}))
                        .optApplySoftmax(true)
                        .build();

                Criteria<Image, Classifications> criteria = Criteria.builder()
                        .setTypes(Image.class, Classifications.class)
                        .optModelPath(Paths.get(outputPath))
                        .optOption("mapLocation", "true")
                        .optTranslator(translator)
                        .optEngine("PyTorch")
                        .optProgress(new ProgressBar()).build();

                if (predictor == null) {
                    ZooModel model = criteria.loadModel();
                    predictor = model.newPredictor();
                }
                String url = ((Resnet18Input)input).getImageUrl();
                var img = ImageFactory.getInstance().fromUrl(url);
                Classifications classifications = predictor.predict(img);
                return Resnet18Output.builder().classifications(classifications.toJson()).build();
            } catch (Exception e) {
                log.error("Failed to run RESNET18", e);
                throw new MLException("Failed to run RESNET18");
            } finally {
                Thread.currentThread().setContextClassLoader(contextClassLoader);
            }
        });
        return output;
    }
}
