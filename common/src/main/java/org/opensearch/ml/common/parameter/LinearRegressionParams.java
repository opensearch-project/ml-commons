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
public class LinearRegressionParams implements MLAlgoParams {

    public static final String PARSE_FIELD_NAME = "linear_regression";
    public static final NamedXContentRegistry.Entry XCONTENT_REGISTRY = new NamedXContentRegistry.Entry(
            MLAlgoParams.class,
            new ParseField(PARSE_FIELD_NAME),
            it -> parse(it)
    );

    public static final String OBJECTIVE_FIELD = "objective";
    public static final String OPTIMISER_FIELD = "optimiser";
    public static final String LEARNING_RATE_FIELD = "learning_rate";
    public static final String MOMENTUM_TYPE_FIELD = "momentum_type";
    public static final String MOMENTUM_FACTOR_FIELD = "momentum_factor";
    public static final String EPSILON_FIELD = "epsilon";
    public static final String BETA1_FIELD = "beta1";
    public static final String BETA2_FIELD = "beta2";
    public static final String DECAY_RATE_FIELD = "decay_rate";
    public static final String EPOCHS_FIELD = "epochs";
    public static final String BATCH_SIZE_FIELD = "batch_size";
    public static final String SEED_FIELD = "seed";
    public static final String TARGET_FIELD = "target";

    private ObjectiveType objectiveType;
    private OptimizerType optimizerType;
    private Double learningRate;
    private MomentumType momentumType;
    private Double momentumFactor;
    private Double epsilon;
    private Double beta1;
    private Double beta2;
    private Double decayRate;
    private Integer epochs;
    private Integer batchSize;
    private Long seed;
    private String target;

    @Builder
    public LinearRegressionParams(ObjectiveType objectiveType, OptimizerType optimizerType, Double learningRate, MomentumType momentumType, Double momentumFactor, Double epsilon, Double beta1, Double beta2, Double decayRate, Integer epochs, Integer batchSize, Long seed, String target) {
        this.objectiveType = objectiveType;
        this.optimizerType = optimizerType;
        this.learningRate = learningRate;
        this.momentumType = momentumType;
        this.momentumFactor = momentumFactor;
        this.epsilon = epsilon;
        this.beta1 = beta1;
        this.beta2 = beta2;
        this.decayRate = decayRate;
        this.epochs = epochs;
        this.batchSize = batchSize;
        this.seed = seed;
        this.target = target;
    }

    public LinearRegressionParams(StreamInput in) throws IOException {
        this.objectiveType = in.readEnum(ObjectiveType.class);
        this.optimizerType = in.readEnum(OptimizerType.class);
        this.learningRate = in.readOptionalDouble();
        this.momentumType = in.readEnum(MomentumType.class);
        this.momentumFactor = in.readOptionalDouble();
        this.epsilon = in.readOptionalDouble();
        this.beta1 = in.readOptionalDouble();
        this.beta2 = in.readOptionalDouble();
        this.decayRate = in.readOptionalDouble();
        this.epochs = in.readOptionalInt();
        this.batchSize = in.readOptionalInt();
        this.seed = in.readOptionalLong();
        this.target = in.readOptionalString();
    }

    private static MLAlgoParams parse(XContentParser parser) throws IOException {
        ObjectiveType objective = null;
        OptimizerType optimizerType = null;
        Double learningRate = null;
        MomentumType momentumType = null;
        Double momentumFactor = null;
        Double epsilon = null;
        Double beta1 = null;
        Double beta2 = null;
        Double decayRate = null;
        Integer epochs = null;
        Integer batchSize = null;
        Long seed = null;
        String target = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case OBJECTIVE_FIELD:
                    objective = ObjectiveType.fromString(parser.text());
                    break;
                case OPTIMISER_FIELD:
                    optimizerType = OptimizerType.fromString(parser.text());
                    break;
                case LEARNING_RATE_FIELD:
                    learningRate = parser.doubleValue();
                    break;
                case MOMENTUM_TYPE_FIELD:
                    momentumType = MomentumType.fromString(parser.text());
                    break;
                case MOMENTUM_FACTOR_FIELD:
                    momentumFactor = parser.doubleValue();
                    break;
                case EPSILON_FIELD:
                    epsilon = parser.doubleValue();
                    break;
                case BETA1_FIELD:
                    beta1 = parser.doubleValue();
                    break;
                case BETA2_FIELD:
                    beta2 = parser.doubleValue();
                    break;
                case DECAY_RATE_FIELD:
                    decayRate = parser.doubleValue();
                    break;
                case EPOCHS_FIELD:
                    epochs = parser.intValue();
                    break;
                case BATCH_SIZE_FIELD:
                    batchSize = parser.intValue();
                    break;
                case SEED_FIELD:
                    seed = parser.longValue();
                    break;
                case TARGET_FIELD:
                    target = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new LinearRegressionParams(objective,  optimizerType,  learningRate,  momentumType,  momentumFactor, epsilon, beta1, beta2,decayRate, epochs, batchSize, seed, target);
    }

    @Override
    public String getWriteableName() {
        return PARSE_FIELD_NAME;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeEnum(objectiveType);
        out.writeEnum(optimizerType);
        out.writeOptionalDouble(learningRate);
        out.writeEnum(momentumType);
        out.writeOptionalDouble(momentumFactor);
        out.writeOptionalDouble(epsilon);
        out.writeOptionalDouble(beta1);
        out.writeOptionalDouble(beta2);
        out.writeOptionalDouble(decayRate);
        out.writeOptionalInt(epochs);
        out.writeOptionalInt(batchSize);
        out.writeOptionalLong(seed);
        out.writeOptionalString(target);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(OBJECTIVE_FIELD, objectiveType.getName());
        builder.field(OPTIMISER_FIELD, optimizerType.getName());
        builder.field(LEARNING_RATE_FIELD, learningRate);
        builder.field(MOMENTUM_TYPE_FIELD, momentumType.getName());
        builder.field(MOMENTUM_FACTOR_FIELD, momentumFactor);
        builder.field(EPSILON_FIELD, epsilon);
        builder.field(BETA1_FIELD, beta1);
        builder.field(BETA2_FIELD, beta2);
        builder.field(DECAY_RATE_FIELD, decayRate);
        builder.field(EPOCHS_FIELD, epochs);
        builder.field(BATCH_SIZE_FIELD, batchSize);
        builder.field(SEED_FIELD, seed);
        builder.field(TARGET_FIELD, target);
        builder.endObject();
        return builder;
    }

    @Override
    public int getVersion() {
        return 0;
    }

    public enum ObjectiveType {
        SQUARED_LOSS("SQUARED_LOSS"),
        ABSOLUTE_LOSS("ABSOLUTE_LOSS"),
        HUBER("HUBER");
        @Getter
        private final String name;

        ObjectiveType(String name) {
            this.name = name;
        }

        public String toString() {
            return name;
        }

        public static ObjectiveType fromString(String name) {
            for(ObjectiveType e : ObjectiveType.values()){
                if(e.getName().equals(name)) return e;
            }
            return null;
        }
    }

    public enum MomentumType {
        STANDARD("STANDARD"),
        NESTEROV("NESTEROV");
        @Getter
        private final String name;

        MomentumType(String name) {
            this.name = name;
        }

        public String toString() {
            return name;
        }

        public static MomentumType fromString(String name) {
            for(MomentumType e : MomentumType.values()){
                if(e.getName().equals(name)) return e;
            }
            return null;
        }
    }

    public enum OptimizerType {
        SIMPLE_SGD("SIMPLE_SGD"),
        LINEAR_DECAY_SGD("LINEAR_DECAY_SGD"),
        SQRT_DECAY_SGD("SQRT_DECAY_SGD"),
        ADA_GRAD("ADA_GRAD"),
        ADA_DELTA("ADA_DELTA"),
        ADAM("ADAM"),
        RMS_PROP("RMS_PROP");
        @Getter
        private final String name;

        OptimizerType(String name) {
            this.name = name;
        }

        public String toString() {
            return name;
        }

        public static OptimizerType fromString(String name) {
            for(OptimizerType e : OptimizerType.values()){
                if(e.getName().equals(name)) return e;
            }
            return null;
        }
    }
}
