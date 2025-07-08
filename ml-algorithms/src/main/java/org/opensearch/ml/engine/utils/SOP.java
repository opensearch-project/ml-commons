package org.opensearch.ml.engine.utils;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SOP {
    @Getter
    private String currentStep;
    private List<SOP> nextSteps;
    public SOP(Object input) {
        if (input instanceof String) {
            currentStep = (String) input;
            nextSteps = new ArrayList<>();
        } else if (input instanceof Map<?, ?>) {
            Map<String, Object> inputMap = (Map<String, Object>) input;
            currentStep = (String) inputMap.keySet().iterator().next();
            Object value = inputMap.get(currentStep);
            if (value instanceof String) {
                nextSteps = List.of(new SOP(value));
            } else if (value instanceof Map<?,?>) {
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    nextSteps.add(new SOP(Map.of(entry.getKey(), entry.getValue())));
                }
            }
        }
    }

    public List<String> getNextSteps() {
        List<String> nextStepString = new ArrayList<>();
        for (SOP sop: nextSteps) {
            nextStepString.add(sop.getCurrentStep());
        }
        return nextStepString;
    }
}
