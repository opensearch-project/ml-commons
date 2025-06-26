/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent.tracing;

import java.util.Map;

import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.telemetry.tracing.Span;
import org.opensearch.telemetry.tracing.Tracer;

public abstract class AbstractMLTracer {
    protected final Tracer tracer;
    protected final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    protected AbstractMLTracer(Tracer tracer, MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.tracer = tracer;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    public abstract Span startSpan(String name, Map<String, String> attributes, Span parentSpan);

    public abstract void endSpan(Span span);
}
