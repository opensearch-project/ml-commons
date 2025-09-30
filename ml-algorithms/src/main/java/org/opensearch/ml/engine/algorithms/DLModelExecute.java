package org.opensearch.ml.engine.algorithms;

import static org.opensearch.ml.engine.ModelHelper.ONNX_FILE_EXTENSION;
import static org.opensearch.ml.engine.ModelHelper.PYTORCH_ENGINE;
import static org.opensearch.ml.engine.ModelHelper.PYTORCH_FILE_EXTENSION;
import static org.opensearch.ml.engine.utils.FileUtils.deleteFileQuietly;

import java.io.File;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.FileUtils;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.input.Input;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.output.Output;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.MLExecutable;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.engine.utils.ZipUtils;

import ai.djl.Application;
import ai.djl.Device;
import ai.djl.engine.Engine;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.Translator;
import lombok.extern.log4j.Log4j2;

@Log4j2
public abstract class DLModelExecute implements MLExecutable {
    public static final String MODEL_ZIP_FILE = "model_zip_file";
    public static final String MODEL_HELPER = "model_helper";
    public static final String ML_ENGINE = "ml_engine";
    protected ModelHelper modelHelper;
    protected MLEngine mlEngine;
    protected String modelId;

    protected Predictor<float[][], ai.djl.modality.Output>[] predictors;
    protected ZooModel[] models;
    protected Device[] devices;
    protected AtomicInteger nextDevice = new AtomicInteger(0);

    public abstract void execute(Input input, ActionListener<Output> listener);

    protected Predictor<float[][], ai.djl.modality.Output> getPredictor() {
        int currentDevice = nextDevice.getAndIncrement();
        if (currentDevice > devices.length - 1) {
            currentDevice = currentDevice % devices.length;
            nextDevice.set(currentDevice + 1);
        }
        return predictors[currentDevice];
    }

    @Override
    public void initModel(MLModel model, Map<String, Object> params) {
        String engine;
        if (Objects.requireNonNull(model.getModelFormat()) == MLModelFormat.TORCH_SCRIPT) {
            engine = PYTORCH_ENGINE;
        } else {
            throw new IllegalArgumentException("unsupported engine");
        }

        File modelZipFile = (File) params.get(MODEL_ZIP_FILE);
        modelHelper = (ModelHelper) params.get(MODEL_HELPER);
        mlEngine = (MLEngine) params.get(ML_ENGINE);
        if (modelZipFile == null) {
            throw new IllegalArgumentException("model file is null");
        }
        if (modelHelper == null) {
            throw new IllegalArgumentException("model helper is null");
        }
        if (mlEngine == null) {
            throw new IllegalArgumentException("ML engine is null");
        }
        modelId = model.getModelId();
        if (modelId == null) {
            throw new IllegalArgumentException("model id is null");
        }
        if (model.getAlgorithm() != FunctionName.METRICS_CORRELATION) {
            throw new IllegalArgumentException("wrong function name");
        }
        loadModel(modelZipFile, modelId, model.getName(), model.getVersion(), engine);
    }

    @Override
    public void close() {
        if (modelHelper != null && modelId != null) {
            modelHelper.deleteFileCache(modelId);
            if (predictors != null) {
                closePredictors(predictors);
                predictors = null;
            }
            if (models != null) {
                closeModels(models);
                models = null;
            }
        }
    }

    public abstract Translator getTranslator();

    /**
     * TODO: This method is mostly similar to the loadModel of the DLMODEL class. While most of the
     *  functionalities are similar we will refactor this to have a parent class in the next release with
     *  more breaking down into smaller functions.
     *
     * @param modelZipFile zip file of the model
     * @param modelId id of the model
     * @param modelName name of the model
     * @param version version of the model
     * @param engine engine where model will be run. For now, we are supporting only pytorch engine only.
     */
    @SuppressWarnings("removal")
    private void loadModel(File modelZipFile, String modelId, String modelName, String version, String engine) {
        try {
            List<Predictor<ai.djl.modality.Input, ai.djl.modality.Output>> predictorList = new ArrayList<>();
            List<ZooModel<ai.djl.modality.Input, ai.djl.modality.Output>> modelList = new ArrayList<>();
            AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
                try {
                    System.setProperty("PYTORCH_PRECXX11", "true");
                    System.setProperty("PYTORCH_VERSION", "2.5.1");
                    System.setProperty("DJL_CACHE_DIR", mlEngine.getMlCachePath().toAbsolutePath().toString());
                    // DJL will read "/usr/java/packages/lib" if don't set "java.library.path". That will throw
                    // access denied exception
                    System.setProperty("java.library.path", mlEngine.getMlCachePath().toAbsolutePath().toString());
                    System.setProperty("ai.djl.pytorch.num_interop_threads", "1");
                    System.setProperty("ai.djl.pytorch.num_threads", "1");
                    Thread.currentThread().setContextClassLoader(ai.djl.Model.class.getClassLoader());
                    Path modelPath = mlEngine.getModelCachePath(modelId, modelName, version);
                    File pathFile = new File(modelPath.toUri());
                    if (pathFile.exists()) {
                        FileUtils.deleteDirectory(pathFile);
                    }
                    ZipUtils.unzip(modelZipFile, modelPath);
                    boolean findModelFile = false;
                    for (File file : Objects.requireNonNull(pathFile.listFiles())) {
                        String name = file.getName();
                        if (name.endsWith(PYTORCH_FILE_EXTENSION) || name.endsWith(ONNX_FILE_EXTENSION)) {
                            if (findModelFile) {
                                throw new IllegalArgumentException("found multiple models");
                            }
                            findModelFile = true;
                            int dotIndex = name.lastIndexOf(".");
                            String suffix = name.substring(dotIndex);
                            String targetModelFileName = modelPath.getFileName().toString();
                            if (!targetModelFileName.equals(name.substring(0, dotIndex))) {
                                file.renameTo(new File(modelPath.resolve(targetModelFileName + suffix).toUri()));
                            }
                        }
                    }
                    devices = Engine.getEngine(engine).getDevices();
                    for (int i = 0; i < devices.length; i++) {
                        log.debug("Deploy model {} on device {}: {}", modelId, i, devices[i]);
                        Criteria.Builder<ai.djl.modality.Input, ai.djl.modality.Output> criteriaBuilder = Criteria
                            .builder()
                            .setTypes(ai.djl.modality.Input.class, ai.djl.modality.Output.class)
                            .optApplication(Application.UNDEFINED)
                            .optEngine(engine)
                            .optDevice(devices[i])
                            .optModelPath(modelPath);
                        Translator translator = getTranslator();
                        if (translator != null) {
                            criteriaBuilder.optTranslator(translator);
                        }

                        Criteria<ai.djl.modality.Input, ai.djl.modality.Output> criteria = criteriaBuilder.build();
                        ZooModel<ai.djl.modality.Input, ai.djl.modality.Output> model = criteria.loadModel();
                        Predictor<ai.djl.modality.Input, ai.djl.modality.Output> predictor = model.newPredictor();
                        predictorList.add(predictor);
                        modelList.add(model);
                    }
                    if (predictorList.size() > 0) {
                        this.predictors = predictorList.toArray(new Predictor[0]);
                        predictorList.clear();
                    }
                    if (modelList.size() > 0) {
                        this.models = modelList.toArray(new ZooModel[0]);
                        modelList.clear();
                    }
                    log.info("Model {} is successfully deployed on {} devices", modelId, devices.length);
                    return null;
                } catch (Throwable e) {
                    String errorMessage = "Failed to deploy model " + modelId;
                    log.error(errorMessage, e);
                    close();
                    if (predictorList.size() > 0) {
                        closePredictors(predictorList.toArray(new Predictor[0]));
                        predictorList.clear();
                    }
                    if (modelList.size() > 0) {
                        closeModels(modelList.toArray(new ZooModel[0]));
                        modelList.clear();
                    }
                    throw new MLException(errorMessage, e);
                } finally {
                    deleteFileQuietly(mlEngine.getDeployModelPath(modelId));
                    Thread.currentThread().setContextClassLoader(contextClassLoader);
                }
            });
        } catch (PrivilegedActionException e) {
            String errorMsg = "Failed to deploy model " + modelId;
            log.error(errorMsg, e);
            throw new MLException(errorMsg, e);
        }
    }

    protected void closePredictors(Predictor[] predictors) {
        log.debug("will close {} predictor for model {}", predictors.length, modelId);
        for (Predictor predictor : predictors) {
            predictor.close();
        }
    }

    protected void closeModels(ZooModel[] models) {
        log.debug("will close {} zoo model for model {}", models.length, modelId);
        for (ZooModel model : models) {
            model.close();
        }
    }
}
