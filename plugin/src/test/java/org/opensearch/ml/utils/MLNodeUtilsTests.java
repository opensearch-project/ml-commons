/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import static java.util.Collections.emptyMap;
import static org.opensearch.ml.utils.TestHelper.ML_ROLE;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;
import org.opensearch.Version;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodeRole;
import org.opensearch.common.bytes.BytesReference;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.MLTask;
import org.opensearch.test.OpenSearchTestCase;

public class MLNodeUtilsTests extends OpenSearchTestCase {

    public void testIsMLNode() {
        Set<DiscoveryNodeRole> roleSet = new HashSet<>();
        roleSet.add(DiscoveryNodeRole.DATA_ROLE);
        roleSet.add(DiscoveryNodeRole.INGEST_ROLE);
        DiscoveryNode normalNode = new DiscoveryNode("Normal node", buildNewFakeTransportAddress(), emptyMap(), roleSet, Version.CURRENT);
        Assert.assertFalse(MLNodeUtils.isMLNode(normalNode));

        roleSet.add(ML_ROLE);
        DiscoveryNode mlNode = new DiscoveryNode("ML node", buildNewFakeTransportAddress(), emptyMap(), roleSet, Version.CURRENT);
        Assert.assertTrue(MLNodeUtils.isMLNode(mlNode));
    }

    public void testCreateXContentParserFromRegistry() throws IOException {
        MLTask mlTask = MLTask.builder().taskId("taskId").modelId("modelId").build();
        XContentBuilder content = mlTask.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        BytesReference bytesReference = BytesReference.bytes(content);
        NamedXContentRegistry namedXContentRegistry = NamedXContentRegistry.EMPTY;
        XContentParser xContentParser = MLNodeUtils.createXContentParserFromRegistry(namedXContentRegistry, bytesReference);
        xContentParser.nextToken();
        MLTask parsedMLTask = MLTask.parse(xContentParser);
        assertEquals(mlTask, parsedMLTask);
    }
}
