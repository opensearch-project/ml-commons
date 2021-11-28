package org.opensearch.ml.common.parameter;

import lombok.Getter;

public enum FunctionName {
    LINEAR_REGRESSION("linear_regression"),
    KMEANS("kmeans"),
    SAMPLE_ALGO("sample_algo"),
    LOCAL_SAMPLE_CALCULATOR("local_sample_calculator");

    @Getter
    private final String name;

    FunctionName(String name) {
        this.name = name;
    }

    public String toString() {
        return name;
    }

    public static FunctionName fromString(String name){
        for(FunctionName e : FunctionName.values()){
            if(e.name.equals(name)) return e;
        }
        return null;
    }
}
