package org.opensearch.ml.rest;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.client.node.NodeClient;
import org.opensearch.common.Strings;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.ml.common.transport.upload.UploadTaskAction;
import org.opensearch.ml.common.transport.upload.UploadTaskRequest;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.pmml4s.model.Model;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class RestMLUploadAction extends BaseMLModelManageAction {
    private static final String ML_UPLOAD_ACTION = "ml_upload_action";

    /**
     * Constructor
     */
    public RestMLUploadAction() {}

    @Override
    public String getName() {
        return ML_UPLOAD_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList.of(new Route(RestRequest.Method.POST, ML_BASE_URI + "/_upload/"));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        Map<String, String> parameters = parseParameters(parser);
        UploadTaskRequest uploadTaskRequest = getRequest(request, parameters);
        return channel -> client.execute(UploadTaskAction.INSTANCE, uploadTaskRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a UploadTaskRequest from a RestRequest.
     * Upload requires all parameters to be non-null.
     *
     * @param request    RestRequest
     * @param parameters parameters for the upload request
     * @return UploadTaskRequest
     */
    @VisibleForTesting
    UploadTaskRequest getRequest(RestRequest request, Map<String, String> parameters) {
        String name = parameters.get(PARAMETER_NAME);
        String format = parameters.get(PARAMETER_FORMAT);
        String algorithm = parameters.get(PARAMETER_ALGORITHM);
        String body = parameters.get(PARAMETER_BODY);

        if (Strings.isNullOrEmpty(name)) {
            throw new IllegalArgumentException("Request should contain name!");
        }
        if (Strings.isNullOrEmpty(format)) {
            throw new IllegalArgumentException("Request should contain format!");
        }
        if (Strings.isNullOrEmpty(algorithm)) {
            throw new IllegalArgumentException("Request should contain algorithm!");
        }
        if (Strings.isNullOrEmpty(body)) {
            throw new IllegalArgumentException("Request should contain body!");
        }
        // make sure model body (base64 encoded string) can be decoded and turned into a valid model
        try {
            byte[] bodyBytes = Base64.getDecoder().decode(body);
            Model model = Model.fromBytes(bodyBytes);
        } catch (Exception e) {
            throw new IllegalArgumentException("can't retrieve model from body passed in");
        }

        return new UploadTaskRequest(name, format, algorithm, body);
    }

    /**
     * Parses for the model body
     *
     * @param parser XContentParser
     * @return parameters for the upload request
     * @throws IOException IOException if content can't be parsed correctly
     */

    Map<String, String> parseParameters(XContentParser parser) throws IOException {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(PARAMETER_NAME, null);
        parameters.put(PARAMETER_FORMAT, null);
        parameters.put(PARAMETER_ALGORITHM, null);
        parameters.put(PARAMETER_BODY, null);
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case PARAMETER_NAME:
                    parameters.put(PARAMETER_NAME, parser.text());
                    break;
                case PARAMETER_FORMAT:
                    parameters.put(PARAMETER_FORMAT, parser.text());
                    break;
                case PARAMETER_ALGORITHM:
                    parameters.put(PARAMETER_ALGORITHM, parser.text());
                    break;
                case PARAMETER_BODY:
                    parameters.put(PARAMETER_BODY, parser.text());
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return parameters;
    }
}
