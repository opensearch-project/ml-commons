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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.ml.engine.algorithms.clustering.KMeans;
import org.opensearch.ml.engine.exceptions.ModelSerDeSerException;
import org.opensearch.ml.engine.utils.ModelSerDeSer;
import org.tribuo.clustering.kmeans.KMeansModel;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.engine.helper.KMeansHelper.constructKMeansDataFrame;

public class ModelSerDeSerTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private final Object dummyModel = new Object();

    @Test
    public void testModelSerDeSerBlocklModel() {
        thrown.expect(ModelSerDeSerException.class);
        byte[] modelBin = ModelSerDeSer.serialize(dummyModel);
        Object model = ModelSerDeSer.deserialize(modelBin);
        assertTrue(model.equals(dummyModel));
    }

    @Test
    public void testModelSerDeSerKMeans() {
        KMeans kMeans = new KMeans(new ArrayList<>());
        Model model = kMeans.train(constructKMeansDataFrame(100));

        KMeansModel kMeansModel = (KMeansModel) ModelSerDeSer.deserialize(model.content);
        byte[] serializedModel = ModelSerDeSer.serialize(kMeansModel);
        assertFalse(Arrays.equals(serializedModel, model.content));
    }
}