/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.ml.model;

import java.io.IOException;
import java.util.Base64;

import lombok.Builder;
import lombok.Getter;

import org.opensearch.common.xcontent.ToXContentObject;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.commons.authuser.User;
import org.opensearch.ml.common.parameter.FunctionName;
import org.opensearch.ml.engine.Model;

@Getter
public class MLModel implements ToXContentObject {
    public static final String ALGORITHM = "algorithm";
    public static final String MODEL_NAME = "name";
    public static final String MODEL_VERSION = "version";
    public static final String MODEL_CONTENT = "content";
    public static final String USER = "user";

    private String name;
    private FunctionName algorithm;
    private Integer version;
    private String content;
    private User user;

    @Builder
    public MLModel(String name, FunctionName algorithm, Integer version, String content, User user) {
        this.name = name;
        this.algorithm = algorithm;
        this.version = version;
        this.content = content;
        this.user = user;
    }

    public MLModel(FunctionName algorithm, Model model) {
        this(model.getName(), algorithm, model.getVersion(), Base64.getEncoder().encodeToString(model.getContent()), null);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (name != null) {
            builder.field(MODEL_NAME, name);
        }
        if (algorithm != null) {
            builder.field(ALGORITHM, algorithm);
        }
        if (version != null) {
            builder.field(MODEL_VERSION, version);
        }
        if (content != null) {
            builder.field(MODEL_CONTENT, content);
        }
        if (user != null) {
            builder.field(USER, user);
        }
        builder.endObject();
        return builder;
    }

}
