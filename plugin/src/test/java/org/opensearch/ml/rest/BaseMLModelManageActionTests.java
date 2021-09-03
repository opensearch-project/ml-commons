package org.opensearch.ml.rest;

import static org.opensearch.ml.rest.BaseMLModelManageAction.*;

import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.common.Strings;
import org.opensearch.rest.RestHandler;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;

import com.google.common.collect.ImmutableMap;

public class BaseMLModelManageActionTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private BaseMLModelManageAction baseMLModelManageAction;

    @Before
    public void setup() {
        baseMLModelManageAction = new BaseMLModelManageAction();
    }

    @Test
    public void testConstructor() {
        BaseMLModelManageAction baseMLModelManageAction = new BaseMLModelManageAction();
        Assert.assertNotNull(baseMLModelManageAction);
    }

    @Test
    public void testGetName() {
        String actionName = baseMLModelManageAction.getName();
        assertFalse(Strings.isNullOrEmpty(actionName));
        assertEquals("base_ml_model_manage_action", actionName);
    }

    @Test
    public void testRoutes() {
        List<RestHandler.Route> routes = baseMLModelManageAction.routes();
        assertNotNull(routes);
        assertTrue(routes.isEmpty());
    }

    @Test
    public void testGetModelIdWithValidInput() {
        Map<String, String> param = ImmutableMap.<String, String>builder().put(PARAMETER_MODEL_ID, "123").build();
        FakeRestRequest fakeRestRequest = new FakeRestRequest.Builder(xContentRegistry()).withParams(param).build();
        String modelId = baseMLModelManageAction.getModelId(fakeRestRequest);
        assertFalse(Strings.isNullOrEmpty(modelId));
        assertEquals(modelId, "123");
    }

    @Test
    public void testGetModelIdWithEmptyInput() {
        Map<String, String> param = ImmutableMap.<String, String>builder().put(PARAMETER_MODEL_ID, "").build();
        FakeRestRequest fakeRestRequest = new FakeRestRequest.Builder(xContentRegistry()).withParams(param).build();
        String modelId = baseMLModelManageAction.getModelId(fakeRestRequest);
        assertTrue(Strings.isNullOrEmpty(modelId));
    }

    @Test
    public void testGetNameWithValidInput() {
        Map<String, String> param = ImmutableMap.<String, String>builder().put(PARAMETER_NAME, "test").build();
        FakeRestRequest fakeRestRequest = new FakeRestRequest.Builder(xContentRegistry()).withParams(param).build();
        String name = baseMLModelManageAction.getName(fakeRestRequest);
        assertFalse(Strings.isNullOrEmpty(name));
        assertEquals(name, "test");
    }

    @Test
    public void testGetNameWithEmptyInput() {
        Map<String, String> param = ImmutableMap.<String, String>builder().put(PARAMETER_NAME, "").build();
        FakeRestRequest fakeRestRequest = new FakeRestRequest.Builder(xContentRegistry()).withParams(param).build();
        String name = baseMLModelManageAction.getModelId(fakeRestRequest);
        assertTrue(Strings.isNullOrEmpty(name));
    }

    @Test
    public void testGetFormatWithValidInput() {
        Map<String, String> param = ImmutableMap.<String, String>builder().put(PARAMETER_FORMAT, "pmml").build();
        FakeRestRequest fakeRestRequest = new FakeRestRequest.Builder(xContentRegistry()).withParams(param).build();
        String format = baseMLModelManageAction.getFormat(fakeRestRequest);
        assertFalse(Strings.isNullOrEmpty(format));
        assertEquals(format, "pmml");
    }

    @Test
    public void testGetFormatWithEmptyInput() {
        Map<String, String> param = ImmutableMap.<String, String>builder().put(PARAMETER_FORMAT, "").build();
        FakeRestRequest fakeRestRequest = new FakeRestRequest.Builder(xContentRegistry()).withParams(param).build();
        String format = baseMLModelManageAction.getModelId(fakeRestRequest);
        assertTrue(Strings.isNullOrEmpty(format));
    }

    @Test
    public void testGetAlgorithmWithValidInput() {
        Map<String, String> param = ImmutableMap.<String, String>builder().put(PARAMETER_ALGORITHM, "isolationforest").build();
        FakeRestRequest fakeRestRequest = new FakeRestRequest.Builder(xContentRegistry()).withParams(param).build();
        String algorithm = baseMLModelManageAction.getAlgorithm(fakeRestRequest);
        assertFalse(Strings.isNullOrEmpty(algorithm));
        assertEquals(algorithm, "isolationforest");
    }

    @Test
    public void testGetAlgorithmWithEmptyInput() {
        Map<String, String> param = ImmutableMap.<String, String>builder().put(PARAMETER_ALGORITHM, "").build();
        FakeRestRequest fakeRestRequest = new FakeRestRequest.Builder(xContentRegistry()).withParams(param).build();
        String algorithm = baseMLModelManageAction.getModelId(fakeRestRequest);
        assertTrue(Strings.isNullOrEmpty(algorithm));
    }
}
