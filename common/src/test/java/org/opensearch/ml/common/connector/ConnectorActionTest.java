package org.opensearch.ml.common.connector;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Map;

public class ConnectorActionTest {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void constructor_NullActionType() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("action type can't null");
        ConnectorAction.ActionType actionType = null;
        String method = "post";
        String url = "https://test.com";
        Map<String, String> headers = null;
        String requestBody = null;
        String preProcessFunction = null;
        String postProcessFunction = null;
        new ConnectorAction(actionType, method, url, headers, requestBody, preProcessFunction, postProcessFunction);
    }
}
