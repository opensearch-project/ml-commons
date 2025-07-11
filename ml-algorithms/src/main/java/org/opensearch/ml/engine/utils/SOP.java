package org.opensearch.ml.engine.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.Getter;

public class SOP {
    @Getter
    private String currentStep;

    @Getter
    private List<SOP> nextSteps;

    @Getter
    private String entranceCondition;

    @Getter
    private String judgement;

    public SOP(Map<String, Object> input) {
        String currentStep = (String) input.get("currentStep");
        String entranceCondition = (String) input.get("entranceCondition");
        List<Object> nextSteps = (List<Object>) input.get("nextSteps");
        this.entranceCondition = entranceCondition;
        this.currentStep = currentStep;
        this.nextSteps = new ArrayList<>();
        this.judgement = (String) input.get("judgement");
        for (Object o : nextSteps) {
            this.nextSteps.add(new SOP((Map<String, Object>) o));
        }
    }

    public String formatNextStep() {
        StringBuilder result = new StringBuilder("The judgement is:\n");
        result.append(judgement);
        result.append("\nCurrently we have the following options with its entrance condition:");

        int index = 0;
        for (SOP nextStep : nextSteps) {
            result.append("Option ");
            result.append(index + 1);
            result.append(": ");
            result.append(nextStep.getEntranceCondition());
            result.append("\n");
            index += 1;
        }
        return result.toString();
    }

    public List<SOP> getNextSteps() {
        return nextSteps;
    }
}
