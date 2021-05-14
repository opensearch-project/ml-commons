/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.engine;

import org.junit.Test;
import org.opensearch.ml.engine.utils.ModelSerDeSer;

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