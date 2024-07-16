package org.opensearch.ml.common.spi.tools;

import org.opensearch.core.action.ActionListener;
import java.util.Map;

/**
 * Abstract base class for tools, providing common functionality.
 */
public abstract class AbstractTool implements Tool {

    private String tenantId;

    @Override
    public String getTenantId() {
        return tenantId;
    }

    @Override
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    @Override
    public <T> void run(Map<String, String> parameters, ActionListener<T> listener) {
        // Default implementation or leave it abstract
    }

    @Override
    public void setInputParser(Parser<?, ?> parser) {
        // Default implementation or leave it abstract
    }

    @Override
    public void setOutputParser(Parser<?, ?> parser) {
        // Default implementation or leave it abstract
    }

    @Override
    public boolean validate(Map<String, String> parameters) {
        return parameters != null && !parameters.isEmpty();
    }

    // Other methods can remain abstract if they must be implemented by subclasses
}
