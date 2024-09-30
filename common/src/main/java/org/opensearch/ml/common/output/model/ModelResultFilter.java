package org.opensearch.ml.common.output.model;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.XContentParser;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

/**
 * This class is to filter model results.
 */
@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class ModelResultFilter implements Writeable {

    public static final String RETURN_BYTES_FIELD = "return_bytes";
    // Return bytes in model output. This can be used together with return_bytes.
    public static final String RETURN_NUMBER_FIELD = "return_number";
    // Filter target response with name in model output
    public static final String TARGET_RESPONSE_FIELD = "target_response";
    // Filter target response with position in model output
    public static final String TARGET_RESPONSE_POSITIONS_FIELD = "target_response_positions";

    // Return model output as bytes. This could be useful if client side prefer
    // to parse the model output in its own way.
    protected boolean returnBytes;

    // Return model output as number types directly. For example float/double.
    protected boolean returnNumber;

    // Target response name which will return.
    // If it's null, will return all responses.
    protected List<String> targetResponse;

    // Target response position which will return. If model output doesn't have
    // name, user should use this to filter result.
    // If it's null, will return all responses.
    protected List<Integer> targetResponsePositions;

    @Builder
    public ModelResultFilter(
        boolean returnBytes,
        boolean returnNumber,
        List<String> targetResponse,
        List<Integer> targetResponsePositions
    ) {
        this.returnBytes = returnBytes;
        this.returnNumber = returnNumber;
        this.targetResponse = targetResponse;
        this.targetResponsePositions = targetResponsePositions;
    }

    public ModelResultFilter(StreamInput streamInput) throws IOException {
        this.returnBytes = streamInput.readBoolean();
        this.returnNumber = streamInput.readBoolean();
        targetResponse = streamInput.readOptionalStringList();
        if (streamInput.readBoolean()) {
            int size = streamInput.readInt();
            targetResponsePositions = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                targetResponsePositions.add(streamInput.readInt());
            }
        } else {
            targetResponsePositions = null;
        }
    }

    @Override
    public void writeTo(StreamOutput streamOutput) throws IOException {
        streamOutput.writeBoolean(returnBytes);
        streamOutput.writeBoolean(returnNumber);
        streamOutput.writeOptionalStringCollection(targetResponse);
        if (targetResponsePositions != null && targetResponsePositions.size() > 0) {
            streamOutput.writeBoolean(true);
            streamOutput.writeInt(targetResponsePositions.size());
            for (Integer value : targetResponsePositions) {
                streamOutput.writeInt(value);
            }
        } else {
            streamOutput.writeBoolean(false);
        }
    }

    public static ModelResultFilter parse(XContentParser parser) throws IOException {
        boolean returnBytes = false;
        boolean returnNumber = true;
        List<String> targetResponse = new ArrayList<>();
        List<Integer> targetResponsePositions = new ArrayList<>();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case RETURN_BYTES_FIELD:
                    returnBytes = parser.booleanValue();
                    break;
                case RETURN_NUMBER_FIELD:
                    returnNumber = parser.booleanValue();
                    break;
                case TARGET_RESPONSE_FIELD:
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        targetResponse.add(parser.text());
                    }
                    break;
                case TARGET_RESPONSE_POSITIONS_FIELD:
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        targetResponsePositions.add(parser.intValue());
                    }
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new ModelResultFilter(returnBytes, returnNumber, targetResponse, targetResponsePositions);
    }
}
