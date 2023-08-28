package org.opensearch.ml.engine.algorithms.text_embedding;
import ai.djl.Application;
import ai.djl.Device;
import ai.djl.MalformedModelException;
import ai.djl.Model;
import ai.djl.engine.Engine;
import ai.djl.inference.Predictor;
import ai.djl.modality.Input;
import ai.djl.modality.Output;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import ai.djl.nn.Block;
import ai.djl.nn.BlockList;
import ai.djl.nn.Parameter;
import ai.djl.nn.ParameterList;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.ParameterStore;
import ai.djl.training.initializer.Initializer;
import ai.djl.translate.TranslateException;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorFactory;
import ai.djl.util.PairList;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.function.Predicate;


public class EmptyModel {
    public static ZooModel<Input, Output> newInstance(Translator translator)
    {
        Block block = new Block() {
            @Override
            public NDList forward(ParameterStore parameterStore, NDList inputs, boolean training, PairList<String, Object> params) {
                return inputs;
            }

            @Override
            public void setInitializer(Initializer initializer, Parameter.Type type) {

            }

            @Override
            public void setInitializer(Initializer initializer, String paramName) {

            }

            @Override
            public void setInitializer(Initializer initializer, Predicate<Parameter> predicate) {

            }

            @Override
            public void initialize(NDManager manager, DataType dataType, Shape... inputShapes) {

            }

            @Override
            public boolean isInitialized() {
                return false;
            }

            @Override
            public void cast(DataType dataType) {

            }

            @Override
            public void clear() {

            }

            @Override
            public PairList<String, Shape> describeInput() {
                return null;
            }

            @Override
            public BlockList getChildren() {
                return null;
            }

            @Override
            public ParameterList getDirectParameters() {
                return null;
            }

            @Override
            public ParameterList getParameters() {
                return null;
            }

            @Override
            public Shape[] getOutputShapes(Shape[] inputShapes) {
                return new Shape[0];
            }

            @Override
            public Shape[] getInputShapes() {
                return new Shape[0];
            }

            @Override
            public DataType[] getOutputDataTypes() {
                return new DataType[0];
            }

            @Override
            public void saveParameters(DataOutputStream os) throws IOException {

            }

            @Override
            public void loadParameters(NDManager manager, DataInputStream is) throws IOException, MalformedModelException {

            }
        };
        Model model = Model.newInstance("dummyModel");
        ZooModel<Input, Output> dummyModel = new ZooModel<>(model, translator);
        return dummyModel;
    }
}
