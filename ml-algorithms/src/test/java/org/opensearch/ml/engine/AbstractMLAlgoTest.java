package org.opensearch.ml.engine;

import junit.framework.TestCase;
import org.opensearch.ml.common.dataframe.DataFrame;

import java.io.IOException;

public class AbstractMLAlgoTest extends TestCase {
    private final DummyModel dummyModel = new DummyModel();
    private AbstractMLAlgo abstractMLAlgo;

    public void setUp() throws Exception {
        super.setUp();
        abstractMLAlgo = new AbstractMLAlgo() {
            @Override
            public DataFrame predict(DataFrame dataFrame, String model) {
                return null;
            }

            @Override
            public String train(DataFrame dataFrame) {
                return null;
            }
        };
    }

    public void testModelSerDeSer() throws IOException, ClassNotFoundException {
        String modelStr = abstractMLAlgo.modelToString(dummyModel);
        DummyModel model = (DummyModel) abstractMLAlgo.stringToModel(modelStr);
        assertTrue(model.equals(dummyModel));
    }

}