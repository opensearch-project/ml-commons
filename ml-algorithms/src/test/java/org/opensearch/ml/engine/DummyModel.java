package org.opensearch.ml.engine;

import lombok.Getter;

import java.io.Serializable;

public class DummyModel implements Serializable {
    @Getter
    private String name = "dummy model";

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (object == null) {
            return false;
        }

        if (object instanceof DummyModel) {
            return ((DummyModel) object).getName().equals(this.name);
        }

        return false;
    }
}
