/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.plugin;

import java.util.Map;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.spi.tools.Tool;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class DummyWrongTool implements Tool {
    public static final String TYPE = "DummyWrongTool";

    private String name = TYPE;

    static String DEFAULT_DESCRIPTION = "This is a dummy wrong tool.";

    private String description = DEFAULT_DESCRIPTION;

    public DummyWrongTool() {}

    @Override
    public <T> void run(Map<String, String> parameters, ActionListener<T> listener) {

    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public String getVersion() {
        return null;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public void setName(String s) {
        this.name = s;
    }

    @Override
    public String getDescription() {
        return DEFAULT_DESCRIPTION;
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public boolean validate(Map<String, String> parameters) {
        return true;
    }

    public static class Factory implements Tool.Factory<DummyWrongTool> {

        private static Factory INSTANCE;

        public static Factory getInstance() {
            if (INSTANCE != null) {
                return INSTANCE;
            }
            synchronized (DummyWrongTool.class) {
                if (INSTANCE != null) {
                    return INSTANCE;
                }
                INSTANCE = new Factory();
                return INSTANCE;
            }
        }

        public void init() {

        }

        @Override
        public DummyWrongTool create(Map<String, Object> map) {
            return new DummyWrongTool();
        }

        @Override
        public String getDefaultDescription() {
            return DEFAULT_DESCRIPTION;
        }
    }
}
