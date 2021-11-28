package org.opensearch.ml.common.parameter;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import org.opensearch.common.ParseField;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentParser;

import java.io.IOException;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

@Data
public class KMeansParams implements MLAlgoParams {

    public static final String PARSE_FIELD_NAME = "kmeans";
    public static final NamedXContentRegistry.Entry XCONTENT_REGISTRY = new NamedXContentRegistry.Entry(
            MLAlgoParams.class,
            new ParseField(PARSE_FIELD_NAME),
            it -> parse(it)
    );

    public static final String CENTROIDS_FIELD = "centroids";
    public static final String ITERATIONS_FIELD = "iterations";
    public static final String DISTANCE_TYPE_FIELD = "distance_type";

    //The number of centroids to use.
    private Integer centroids;
    //The maximum number of iterations
    private Integer iterations;
    //The distance function.
    private DistanceType distanceType;
    //TODO: expose number of thread and seed?

    @Builder
    public KMeansParams(Integer centroids, Integer iterations, DistanceType distanceType) {
        this.centroids = centroids;
        this.iterations = iterations;
        this.distanceType = distanceType;
    }

    public KMeansParams(StreamInput in) throws IOException {
        this.centroids = in.readOptionalInt();
        this.iterations = in.readOptionalInt();
        if (in.readBoolean()) {
            this.distanceType = in.readEnum(DistanceType.class);
        }
    }

    private static MLAlgoParams parse(XContentParser parser) throws IOException {
        Integer k = null;
        Integer iterations = null;
        DistanceType distanceType = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case CENTROIDS_FIELD:
                    k = parser.intValue();
                    break;
                case ITERATIONS_FIELD:
                    iterations = parser.intValue();
                    break;
                case DISTANCE_TYPE_FIELD:
                    distanceType = DistanceType.fromString(parser.text());
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new KMeansParams(k, iterations, distanceType);
    }

    @Override
    public String getWriteableName() {
        return PARSE_FIELD_NAME;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalInt(centroids);
        out.writeOptionalInt(iterations);
        if (distanceType != null) {
            out.writeBoolean(true);
            out.writeEnum(distanceType);
        } else {
            out.writeBoolean(false);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(CENTROIDS_FIELD, centroids);
        builder.field(ITERATIONS_FIELD, iterations);
        builder.field(DISTANCE_TYPE_FIELD, distanceType.getName());
        builder.endObject();
        return builder;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    public enum DistanceType {
        EUCLIDEAN("EUCLIDEAN"),
        COSINE("COSINE"),
        L1("L1");

        @Getter
        private final String name;

        DistanceType(String name) {
            this.name = name;
        }

        public String toString() {
            return name;
        }

        public static DistanceType fromString(String name) {
            for(DistanceType e : DistanceType.values()){
                if(e.getName().equals(name)) return e;
            }
            return null;
        }
    }
}
