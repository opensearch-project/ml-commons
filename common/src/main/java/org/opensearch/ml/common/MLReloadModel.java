package org.opensearch.ml.common;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.ToXContentObject;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentParser;

/**
 * @author frank woo(吴峻申) <br> email:<a
 * href="mailto:frank_wjs@hotmail.com">frank_wjs@hotmail.com</a> <br>
 * @date 2022/12/16 16:17<br>
 */
@Getter
public class MLReloadModel implements ToXContentObject {
	public static final String NODE_ID_FIELD = "node_id";
	public static final String MODEL_LOAD_RETRY_TIMES_FIELD = "retry_times";

	@Setter
	private String nodeId;

	@Setter
	private Integer retryTimes;

	@Builder(toBuilder = true)
	public MLReloadModel(String nodeId, Integer retryTimes) {
		this.nodeId = nodeId;
		this.retryTimes = retryTimes;
	}

	public MLReloadModel(StreamInput input) throws IOException {
		if (input.available() > 0) {
			nodeId = input.readOptionalString();
			retryTimes = input.readOptionalInt();
		}
	}

	public void writeTo(StreamOutput out) throws IOException {
		out.writeOptionalString(nodeId);
		out.writeOptionalInt(retryTimes);
	}

	@Override
	public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
		builder.startObject();

		if (nodeId != null) {
			builder.field(NODE_ID_FIELD, nodeId);
		}
		if (retryTimes != null) {
			builder.field(MODEL_LOAD_RETRY_TIMES_FIELD, retryTimes);
		}

		builder.endObject();
		return builder;
	}

	public static MLReloadModel parse(XContentParser parser) throws IOException {
		String nodeId = null;
		Integer retryTimes = null;

		ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
		while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
			String fieldName = parser.currentName();
			parser.nextToken();

			switch (fieldName) {
				case NODE_ID_FIELD:
					nodeId = parser.text();
					break;
				case MODEL_LOAD_RETRY_TIMES_FIELD:
					retryTimes = parser.intValue(false);
					break;
				default:
					parser.skipChildren();
					break;
			}
		}

		return MLReloadModel.builder()
				.nodeId(nodeId)
				.retryTimes(retryTimes)
				.build();
	}

	public static MLReloadModel fromStream(StreamInput in) throws IOException {
		return new MLReloadModel(in);
	}
}
