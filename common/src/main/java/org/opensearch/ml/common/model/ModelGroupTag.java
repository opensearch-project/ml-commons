/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.common.model;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.util.*;
import org.opensearch.common.Nullable;
import org.opensearch.common.inject.internal.ToStringBuilder;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.common.Strings;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

public final class ModelGroupTag implements Writeable, ToXContent {
  public static final String TAG_KEY_FIELD = "key";
  public static final String TAG_TYPE_FIELD = "type";

  @Nullable private final String key;
  @Nullable private final String type;

  public ModelGroupTag() {
    key = "";
    type = "";
  }

  public ModelGroupTag(@Nullable final String key, @Nullable final String type) {
    this.key = key;
    this.type = type;
  }

  public ModelGroupTag(String json) {
    if (Strings.isNullOrEmpty(json)) {
      throw new IllegalArgumentException("Response json cannot be null");
    }

    Map<String, Object> mapValue =
        XContentHelper.convertToMap(JsonXContent.jsonXContent, json, false);
    key = (String) mapValue.get(TAG_KEY_FIELD);
    type = (String) mapValue.get(TAG_TYPE_FIELD);
  }

  public ModelGroupTag(StreamInput in) throws IOException {
    this.key = in.readString();
    this.type = in.readString();
  }

  public static ModelGroupTag parse(XContentParser parser) throws IOException {
    String key = "";
    String type = "";

    ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
    while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
      String fieldName = parser.currentName();
      parser.nextToken();
      switch (fieldName) {
        case TAG_KEY_FIELD:
          key = parser.text();
          break;
        case TAG_TYPE_FIELD:
          type = parser.text();
          break;
        default:
          break;
      }
    }
    return new ModelGroupTag(key, type);
  }

  @Override
  public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
    builder.startObject().field(TAG_KEY_FIELD, key).field(TAG_TYPE_FIELD, type);
    return builder.endObject();
  }

  @Override
  public void writeTo(StreamOutput out) throws IOException {
    out.writeString(key);
    out.writeString(type);
  }

  @Override
  public String toString() {
    ToStringBuilder builder = new ToStringBuilder(this.getClass());
    builder.add(TAG_KEY_FIELD, key);
    builder.add(TAG_TYPE_FIELD, type);
    return builder.toString();
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ModelGroupTag)) {
      return false;
    }
    ModelGroupTag that = (ModelGroupTag) obj;
    return this.key.equals(that.key) && this.type.equals(that.type);
  }

  @Nullable
  public String getKey() {
    return key;
  }

  @Nullable
  public String getType() {
    return type;
  }
}
