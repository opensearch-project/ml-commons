package org.opensearch.ml.common.parameter;

import lombok.Getter;

public enum MLAlgoName {
    LINEAR_REGRESSION("linear_regression"),
    KMEANS("kmeans"),
    SAMPLE_ALGO("sample_algo"),
    LOCAL_SAMPLE_CALCULATOR("local_sample_calculator");

    @Getter
    private final String name;

    MLAlgoName(String name) {
        this.name = name;
    }

    public String toString() {
        return name;
    }

    public static MLAlgoName fromString(String name){
        for(MLAlgoName e : MLAlgoName.values()){
            if(e.name.equals(name)) return e;
        }
        return null;
    }
}
