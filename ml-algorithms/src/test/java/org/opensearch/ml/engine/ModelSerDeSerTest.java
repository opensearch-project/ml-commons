package org.opensearch.ml.engine;

import org.junit.Test;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

public class ModelSerDeSerTest {
    private final DummyModel dummyModel = new DummyModel();

    @Test
    public void testModelSerDeSer() throws IOException, ClassNotFoundException {
        byte[] modelBin = ModelSerDeSer.serialize(dummyModel);
        DummyModel model = (DummyModel) ModelSerDeSer.deserialize(modelBin);
        assertTrue(model.equals(dummyModel));
    }

}