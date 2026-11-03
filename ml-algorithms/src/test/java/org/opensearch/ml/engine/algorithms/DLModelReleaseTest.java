/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.output.Output;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.engine.algorithms.text_embedding.TextEmbeddingDenseModel;
import org.opensearch.ml.engine.algorithms.tokenize.SparseTokenizerModel;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;

import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.Translator;

/**
 * Every deploy attempt of a model on a node shares the model's local files. An attempt that loses the race to publish
 * its instance discards it with release() while another attempt may still be loading from those files. These tests use
 * the real cleanup on real directories: release() frees the instance and leaves the files in place, close() (undeploy)
 * still deletes them.
 */
public class DLModelReleaseTest {

    private static final String MODEL_ID = "model_id";

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private ModelHelper modelHelper;
    private List<Path> sharedFiles;

    @Before
    public void setUp() throws IOException {
        MLEngine mlEngine = new MLEngine(tmp.getRoot().toPath(), new EncryptorImpl(null, "m+dWmfmnNRiNlOdej/QelEkvMTyH//frS2TBeS2BP4w="));
        modelHelper = new ModelHelper(mlEngine);
        // Files of another deploy attempt of the same model, in each directory deleteFileCache removes.
        sharedFiles = List
            .of(
                mlEngine.getModelCachePath(MODEL_ID, "model", "1").resolve("model.pt"),
                mlEngine.getDeployModelChunkPath(MODEL_ID, 0),
                mlEngine.getRegisterModelPath(MODEL_ID, "model", "1").resolve("tokenizer.json")
            );
        for (Path file : sharedFiles) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, "another deploy attempt");
        }
    }

    @Test
    public void dlModel_Release_ClosesItsPredictorsAndModels_KeepsModelFiles() {
        Predictor<ai.djl.modality.Input, ai.djl.modality.Output> predictor = mock(Predictor.class);
        ZooModel<ai.djl.modality.Input, ai.djl.modality.Output> zooModel = mock(ZooModel.class);
        DLModel model = deployedDLModel(predictor, zooModel);

        model.release();

        verify(predictor).close();
        verify(zooModel).close();
        assertFalse(model.isModelReady());
        assertSharedFiles(true);
    }

    @Test
    public void dlModel_Close_AlsoDeletesModelFiles() {
        Predictor<ai.djl.modality.Input, ai.djl.modality.Output> predictor = mock(Predictor.class);
        ZooModel<ai.djl.modality.Input, ai.djl.modality.Output> zooModel = mock(ZooModel.class);
        DLModel model = deployedDLModel(predictor, zooModel);

        model.close();

        verify(predictor).close();
        verify(zooModel).close();
        assertSharedFiles(false);
    }

    @Test
    public void dlModelExecute_Release_KeepsModelFiles_CloseDeletesThem() {
        Predictor<float[][], ai.djl.modality.Output> predictor = mock(Predictor.class);
        ZooModel<float[][], ai.djl.modality.Output> zooModel = mock(ZooModel.class);
        DLModelExecute executable = new DLModelExecute() {
            @Override
            public void execute(Input input, ActionListener<Output> listener) {}

            @Override
            public Translator getTranslator() {
                return null;
            }
        };
        executable.modelHelper = modelHelper;
        executable.modelId = MODEL_ID;
        executable.predictors = new Predictor[] { predictor };
        executable.models = new ZooModel[] { zooModel };

        executable.release();
        verify(predictor).close();
        verify(zooModel).close();
        assertSharedFiles(true);

        executable.close();
        assertSharedFiles(false);
    }

    @Test
    public void sparseTokenizerModel_Release_KeepsModelFiles_CloseDeletesThem() throws ReflectiveOperationException {
        SparseTokenizerModel model = new SparseTokenizerModel();
        model.modelHelper = modelHelper;
        model.modelId = MODEL_ID;
        Field idf = SparseTokenizerModel.class.getDeclaredField("idf");
        idf.setAccessible(true);
        idf.set(model, Map.of("token", 1.0f));

        model.release();
        assertNull(idf.get(model));
        assertSharedFiles(true);

        model.close();
        assertSharedFiles(false);
    }

    private DLModel deployedDLModel(
        Predictor<ai.djl.modality.Input, ai.djl.modality.Output> predictor,
        ZooModel<ai.djl.modality.Input, ai.djl.modality.Output> zooModel
    ) {
        DLModel model = new TextEmbeddingDenseModel();
        model.modelHelper = modelHelper;
        model.modelId = MODEL_ID;
        model.predictors = new Predictor[] { predictor };
        model.models = new ZooModel[] { zooModel };
        return model;
    }

    private void assertSharedFiles(boolean exist) {
        for (Path file : sharedFiles) {
            assertTrue(file + (exist ? " was deleted" : " was not deleted"), Files.exists(file) == exist);
        }
    }
}
