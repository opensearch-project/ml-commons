/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.text_embedding;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionListener;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.ModelHelper;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.PrivilegedActionException;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.verify;

public class ModelHelperTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private ModelHelper modelHelper;
    private String modelId;

    @Mock
    ActionListener<Map<String, Object>> actionListener;

    @Before
    public void setup() throws URISyntaxException {
        MockitoAnnotations.openMocks(this);
        modelHelper = new ModelHelper();
        modelId = "model_id";
        MLEngine.setDjlCachePath(Path.of("/tmp/test" + modelId));
    }

    @Test
    public void testDownloadAndSplit_UrlFailure() {
        modelHelper.downloadAndSplit(modelId, "model_name", "1", "http://testurl", actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(PrivilegedActionException.class, argumentCaptor.getValue().getClass());
    }

    @Test
    public void testDownloadAndSplit() throws URISyntaxException {
        String modelUrl = getClass().getResource("traced_small_model.zip").toURI().toString();
        modelHelper.downloadAndSplit(modelId, "model_name", "1", modelUrl, actionListener);
        ArgumentCaptor<Map> argumentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
        assertNotNull(argumentCaptor.getValue());
        assertNotEquals(0, argumentCaptor.getValue().size());
    }
}
