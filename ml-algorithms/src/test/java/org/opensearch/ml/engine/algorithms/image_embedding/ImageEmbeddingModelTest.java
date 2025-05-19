/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.engine.algorithms.image_embedding;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.opensearch.ml.engine.algorithms.DLModel.ML_ENGINE;
import static org.opensearch.ml.engine.algorithms.DLModel.MODEL_HELPER;
import static org.opensearch.ml.engine.algorithms.DLModel.MODEL_ZIP_FILE;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.dataset.ImageEmbeddingInputDataSet;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;
import org.opensearch.ml.engine.utils.FileUtils;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class ImageEmbeddingModelTest {

    private File modelZipFile;
    private MLModel model;
    private ModelHelper modelHelper;
    private Map<String, Object> params;
    private ImageEmbeddingModel imageEmbeddingModel;
    private Path mlCachePath;
    private ImageEmbeddingInputDataSet inputDataSet;
    private ImageEmbeddingInputDataSet listInputDataSet;
    private MLEngine mlEngine;
    private Encryptor encryptor;

    @Before
    public void setUp() throws URISyntaxException, IOException {
        mlCachePath = Path.of("/tmp/ml_cache" + UUID.randomUUID());
        encryptor = new EncryptorImpl("m+dWmfmnNRiNlOdej/QelEkvMTyH//frS2TBeS2BP4w=");
        mlEngine = new MLEngine(mlCachePath, encryptor);
        model = MLModel
            .builder()
            .modelFormat(MLModelFormat.TORCH_SCRIPT)
            .name("test_image_embedding_model")
            .modelId("test_image_embedding_model_id")
            .algorithm(FunctionName.IMAGE_EMBEDDING)
            .version("1.0.0")
            .modelState(MLModelState.TRAINED)
            .build();

        modelHelper = new ModelHelper(mlEngine);
        params = new HashMap<>();
        modelZipFile = new File(getClass().getResource("image_embedding_pt.zip").toURI());
        params.put(MODEL_ZIP_FILE, modelZipFile);
        params.put(MODEL_HELPER, modelHelper);
        params.put(ML_ENGINE, mlEngine);
        imageEmbeddingModel = new ImageEmbeddingModel();

        inputDataSet = ImageEmbeddingInputDataSet
            .builder()
            .base64Images(
                List
                    .of(
                        new String(
                            Base64.getEncoder().encode(Files.readAllBytes(Path.of(getClass().getResource("opensearch_logo.jpg").toURI())))
                        )
                    )
            )
            .build();

        listInputDataSet = ImageEmbeddingInputDataSet
            .builder()
            .base64Images(
                List
                    .of(
                        new String(
                            Base64.getEncoder().encode(Files.readAllBytes(Path.of(getClass().getResource("opensearch_logo.jpg").toURI())))
                        ),
                        new String(
                            Base64.getEncoder().encode(Files.readAllBytes(Path.of(getClass().getResource("opensearch_logo.jpg").toURI())))
                        )
                    )
            )
            .build();
    }

    @Test
    public void initModel_predict_TorchScript_ImageEmbedding() throws URISyntaxException {
        imageEmbeddingModel.initModel(model, params, encryptor);
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.IMAGE_EMBEDDING).inputDataset(inputDataSet).build();
        ModelTensorOutput output = (ModelTensorOutput) imageEmbeddingModel.predict(mlInput);
        List<ModelTensors> mlModelOutputs = output.getMlModelOutputs();
        assertEquals(1, mlModelOutputs.size());
        for (int i = 0; i < mlModelOutputs.size(); i++) {
            ModelTensors tensors = mlModelOutputs.get(i);
            List<ModelTensor> mlModelTensors = tensors.getMlModelTensors();
            ;
            assertEquals(1, mlModelTensors.size());
        }
        imageEmbeddingModel.close();
    }

    @Test
    public void initModel_predict_list_TorchScript_ImageEmbedding() throws URISyntaxException {
        imageEmbeddingModel.initModel(model, params, encryptor);
        MLInput mlInput = MLInput.builder().algorithm(FunctionName.IMAGE_EMBEDDING).inputDataset(listInputDataSet).build();
        ModelTensorOutput output = (ModelTensorOutput) imageEmbeddingModel.predict(mlInput);
        List<ModelTensors> mlModelOutputs = output.getMlModelOutputs();
        assertEquals(2, mlModelOutputs.size());
        for (int i = 0; i < mlModelOutputs.size(); i++) {
            ModelTensors tensors = mlModelOutputs.get(i);
            List<ModelTensor> mlModelTensors = tensors.getMlModelTensors();
            ;
            assertEquals(1, mlModelTensors.size());
        }
        imageEmbeddingModel.close();
    }

    @Test
    public void initModel_NullModelHelper() throws URISyntaxException {
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_ZIP_FILE, modelZipFile);
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> imageEmbeddingModel.initModel(model, params, encryptor)
        );
        assert (e.getMessage().equals("model helper is null"));
    }

    @Test
    public void initModel_NullMLEngine() throws URISyntaxException {
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_ZIP_FILE, modelZipFile);
        params.put(MODEL_HELPER, modelHelper);
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> imageEmbeddingModel.initModel(model, params, encryptor)
        );
        assert (e.getMessage().equals("ML engine is null"));
    }

    @Test
    public void initModel_NullModelId() {
        model.setModelId(null);
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> imageEmbeddingModel.initModel(model, params, encryptor)
        );
        assert (e.getMessage().equals("model id is null"));
    }

    @Test
    public void initModel_WrongModelFile() throws URISyntaxException {
        Map<String, Object> params = new HashMap<>();
        params.put(MODEL_HELPER, modelHelper);
        params.put(MODEL_ZIP_FILE, new File(getClass().getResource("../text_embedding/wrong_zip_with_2_pt_file.zip").toURI()));
        params.put(ML_ENGINE, mlEngine);
        MLException e = assertThrows(MLException.class, () -> imageEmbeddingModel.initModel(model, params, encryptor));
        Throwable rootCause = e.getCause();
        assert (rootCause instanceof IllegalArgumentException);
        assert (rootCause.getMessage().equals("found multiple models"));
    }

    @Test
    public void initModel_WrongFunctionName() {
        MLModel mlModel = model.toBuilder().algorithm(FunctionName.KMEANS).build();
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> imageEmbeddingModel.initModel(mlModel, params, encryptor)
        );
        assert (e.getMessage().equals("wrong function name"));
    }

    @Test
    public void predict_NullModelHelper() {
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> imageEmbeddingModel.predict(MLInput.builder().algorithm(FunctionName.IMAGE_EMBEDDING).inputDataset(inputDataSet).build())
        );
        assert (e.getMessage().equals("model not deployed"));
    }

    @Test
    public void predict_NullModelId() {
        model.setModelId(null);
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> imageEmbeddingModel.initModel(model, params, encryptor)
        );
        assert (e.getMessage().equals("model id is null"));
        IllegalArgumentException e2 = assertThrows(
            IllegalArgumentException.class,
            () -> imageEmbeddingModel.predict(MLInput.builder().algorithm(FunctionName.IMAGE_EMBEDDING).inputDataset(inputDataSet).build())
        );
        assert (e2.getMessage().equals("model not deployed"));
    }

    @Test
    public void predict_AfterModelClosed() {
        imageEmbeddingModel.initModel(model, params, encryptor);
        imageEmbeddingModel.close();
        MLException e = assertThrows(
            MLException.class,
            () -> imageEmbeddingModel.predict(MLInput.builder().algorithm(FunctionName.IMAGE_EMBEDDING).inputDataset(inputDataSet).build())
        );
        log.info(e.getMessage());
        assert (e.getMessage().startsWith("Failed to inference IMAGE_EMBEDDING"));
    }

    @After
    public void tearDown() {
        FileUtils.deleteFileQuietly(mlCachePath);
    }

}
