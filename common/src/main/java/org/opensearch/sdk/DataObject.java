/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.sdk;

import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentParser;

import java.io.IOException;

/**
 * A structured object used for Data Exchange.
 */
public interface DataObject extends ToXContentObject {
    /**
     * Create an instance of this object from the provided {@link XContentParser}.
     * @param parser A parser for the {@link MediaType} by which the data was serialied. 
     * @return  An instance of the implemented object.
     * @throws IOException on a parsing error.
     */
    DataObject parse(XContentParser parser) throws IOException;
}
