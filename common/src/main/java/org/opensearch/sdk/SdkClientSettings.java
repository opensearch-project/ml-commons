/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.sdk;

import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Setting.Property;

/** Settings applicable to the SdkClient */
public class SdkClientSettings {

    /** The key for remote metadata type. */
    public static final String REMOTE_METADATA_TYPE_KEY = "plugins.ml_commons.remote_metadata_type";

    /** The value for remote metadata type for a remote cluster. */
    public static final String REMOTE_OPENSEARCH = "RemoteOpenSearch";
    /** The value for remote metadata type for a remote cluster on AWS OpenSearch Service. */
    public static final String AWS_OPENSEARCH_SERVICE = "AWSOpenSearchService";
    /** The value for remote metadata type for a remote cluster on AWS Dynamo DB. */
    public static final String AWS_DYNAMO_DB = "AWSDynamoDB";
    
    /** The key for remote metadata endpoint, applicable to remote clusters or DynamoDB. */
    public static final String REMOTE_METADATA_ENDPOINT_KEY = "plugins.ml_commons.remote_metadata_endpoint";
    /** The key for remote metadata region, applicable to AWS remote clusters or DynamoDB. */
    public static final String REMOTE_METADATA_REGION_KEY = "plugins.ml_commons.remote_metadata_region";
    
    public static final Setting<String> REMOTE_METADATA_TYPE = Setting.simpleString(REMOTE_METADATA_TYPE_KEY, Property.NodeScope, Property.Final);
    public static final Setting<String> REMOTE_METADATA_ENDPOINT = Setting.simpleString(REMOTE_METADATA_ENDPOINT_KEY, Property.NodeScope, Property.Final);
    public static final Setting<String> REMOTE_METADATA_REGION = Setting.simpleString(REMOTE_METADATA_REGION_KEY, Property.NodeScope, Property.Final);
    
}
