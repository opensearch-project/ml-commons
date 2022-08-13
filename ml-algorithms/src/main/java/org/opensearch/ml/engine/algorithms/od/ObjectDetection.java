/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.od;

import ai.djl.Application;
import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import lombok.extern.log4j.Log4j2;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.input.execute.od.ObjectDetectionInput;
import org.opensearch.ml.common.output.Output;
import org.opensearch.ml.common.output.execute.od.ObjectDetectionOutput;
import org.opensearch.ml.engine.Executable;
import org.opensearch.ml.engine.annotation.Function;

import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.List;
import java.util.stream.Collectors;

import static org.opensearch.ml.engine.MLEngine.DJL_CACHE_PATH;

@Log4j2
@Function(FunctionName.OBJECT_DETECTION)
public class ObjectDetection implements Executable {
    private Predictor<Image, DetectedObjects> predictor;

    public ObjectDetection() {
    }

    @Override
    public Output execute(Input input) {
        ObjectDetectionOutput objectDetectionOutput = AccessController.doPrivileged((PrivilegedAction<ObjectDetectionOutput>) () -> {
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            try {
                System.setProperty("DJL_CACHE_DIR", DJL_CACHE_PATH.toAbsolutePath().toString());
                System.setProperty("java.library.path", DJL_CACHE_PATH.toAbsolutePath().toString());
                Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

                String url = ((ObjectDetectionInput)input).getImageUrl();
                Image img = ImageFactory.getInstance().fromUrl(new URL(url));
                ZooModel<Image, DetectedObjects> model = null;
                try {
                    if (predictor == null) {
                        Criteria<Image, DetectedObjects> criteria =
                                Criteria.builder()
                                        .optApplication(Application.CV.OBJECT_DETECTION)
                                        .setTypes(Image.class, DetectedObjects.class)
                                        .optArtifactId("ssd")
                                        .optFilter("backbone", "mobilenet_v2")
                                        .optEngine("TensorFlow")
                                        .optProgress(new ProgressBar())
                                        .build();
                        model = criteria.loadModel();
                        predictor = model.newPredictor();
                    }

                    DetectedObjects result = predictor.predict(img);
                    List<String> classes =
                            result.items()
                                    .stream()
                                    .map(Classifications.Classification::getClassName)
                                    .collect(Collectors.toList());
                    return ObjectDetectionOutput.builder().objects(classes.toArray(new String[0])).build();
                } catch (Exception e) {
                    log.error("Failed to predict ", e);
                    throw new MLException("Failed to detect");
                }
            } catch (Exception e) {
                log.error("Failed to detect object from image", e);
                throw new MLException("Failed to detect object");
            } finally {
                Thread.currentThread().setContextClassLoader(contextClassLoader);
            }
        });
        return objectDetectionOutput;
    }
}
