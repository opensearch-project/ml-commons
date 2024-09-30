/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.parameter.ad;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.util.Locale;

import org.opensearch.core.ParseField;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.annotation.MLAlgoParameter;
import org.opensearch.ml.common.input.parameter.MLAlgoParams;

import lombok.Builder;
import lombok.Data;

@Data
@MLAlgoParameter(algorithms = { FunctionName.AD_LIBSVM })
public class AnomalyDetectionLibSVMParams implements MLAlgoParams {
    public static final String PARSE_FIELD_NAME = FunctionName.AD_LIBSVM.name();
    public static final NamedXContentRegistry.Entry XCONTENT_REGISTRY = new NamedXContentRegistry.Entry(
        MLAlgoParams.class,
        new ParseField(PARSE_FIELD_NAME),
        it -> parse(it)
    );

    public static final String KERNEL_FIELD = "kernel";
    public static final String GAMMA_FIELD = "gamma";
    public static final String NU_FIELD = "nu";
    public static final String COST_FIELD = "cost";
    public static final String COEFF_FIELD = "coeff";
    public static final String EPSILON_FIELD = "epsilon";
    public static final String DEGREE_FIELD = "degree";
    private ADKernelType kernelType;
    private Double gamma;
    private Double nu;
    private Double cost;
    private Double coeff;
    private Double epsilon;
    private Integer degree;

    @Builder(toBuilder = true)
    public AnomalyDetectionLibSVMParams(
        ADKernelType kernelType,
        Double gamma,
        Double nu,
        Double cost,
        Double coeff,
        Double epsilon,
        Integer degree
    ) {
        this.kernelType = kernelType;
        this.gamma = gamma;
        this.nu = nu;
        this.cost = cost;
        this.coeff = coeff;
        this.epsilon = epsilon;
        this.degree = degree;
    }

    public AnomalyDetectionLibSVMParams(StreamInput in) throws IOException {
        if (in.readBoolean()) {
            this.kernelType = in.readEnum(ADKernelType.class);
        }
        this.gamma = in.readOptionalDouble();
        this.nu = in.readOptionalDouble();
        this.cost = in.readOptionalDouble();
        this.coeff = in.readOptionalDouble();
        this.epsilon = in.readOptionalDouble();
        this.degree = in.readOptionalInt();
    }

    public static MLAlgoParams parse(XContentParser parser) throws IOException {
        ADKernelType kernelType = null;
        Double gamma = null;
        Double nu = null;
        Double cost = null;
        Double coeff = null;
        Double epsilon = null;
        Integer degree = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case KERNEL_FIELD:
                    kernelType = ADKernelType.from(parser.text().toUpperCase(Locale.ROOT));
                    break;
                case GAMMA_FIELD:
                    gamma = parser.doubleValue(false);
                    break;
                case NU_FIELD:
                    nu = parser.doubleValue(false);
                    break;
                case COST_FIELD:
                    cost = parser.doubleValue(false);
                    break;
                case COEFF_FIELD:
                    coeff = parser.doubleValue(false);
                    break;
                case EPSILON_FIELD:
                    epsilon = parser.doubleValue(false);
                    break;
                case DEGREE_FIELD:
                    degree = parser.intValue(false);
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new AnomalyDetectionLibSVMParams(kernelType, gamma, nu, cost, coeff, epsilon, degree);
    }

    @Override
    public String getWriteableName() {
        return PARSE_FIELD_NAME;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        if (kernelType == null) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            out.writeEnum(kernelType);
        }
        out.writeOptionalDouble(gamma);
        out.writeOptionalDouble(nu);
        out.writeOptionalDouble(cost);
        out.writeOptionalDouble(coeff);
        out.writeOptionalDouble(epsilon);
        out.writeOptionalInt(degree);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (kernelType != null) {
            builder.field(KERNEL_FIELD, kernelType);
        }
        if (gamma != null) {
            builder.field(GAMMA_FIELD, gamma);
        }
        if (nu != null) {
            builder.field(NU_FIELD, nu);
        }
        if (cost != null) {
            builder.field(COST_FIELD, cost);
        }
        if (coeff != null) {
            builder.field(COEFF_FIELD, coeff);
        }
        if (epsilon != null) {
            builder.field(EPSILON_FIELD, epsilon);
        }
        if (degree != null) {
            builder.field(DEGREE_FIELD, degree);
        }
        builder.endObject();
        return builder;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    public enum ADKernelType {
        LINEAR,
        POLY,
        RBF,
        SIGMOID;

        public static ADKernelType from(String value) {
            try {
                return ADKernelType.valueOf(value);
            } catch (Exception e) {
                throw new IllegalArgumentException("Wrong AD kernel type");
            }
        }
    }
}
