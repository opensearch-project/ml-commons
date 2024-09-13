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

import java.util.Set;

/** Settings applicable to the SdkClient */
public class SdkClientSettings {

    /** The key for remote metadata type. */
    public static final String REMOTE_METADATA_TYPE_KEY = "plugins.ml_commons.remote_metadata_type";

    /** The value for remote metadata type for a remote OpenSearch cluster compatible with OpenSearch Java Client. */
    public static final String REMOTE_OPENSEARCH = "RemoteOpenSearch";
    /** The value for remote metadata type for a remote cluster on AWS OpenSearch Service using AWS SDK v2. */
    public static final String AWS_OPENSEARCH_SERVICE = "AWSOpenSearchService";
    private static final String AOS_SERVICE_NAME = "es";
    private static final String AOSS_SERVICE_NAME = "aoss";
    /** Service Names compatible with AWS SDK v2. */
    public static final Set<String> VALID_AWS_OPENSEARCH_SERVICE_NAMES = Set.of(AOS_SERVICE_NAME, AOSS_SERVICE_NAME);
        
    /** The value for remote metadata type for a remote cluster on AWS Dynamo DB and Zero-ETL replication to OpenSearch */
    public static final String AWS_DYNAMO_DB = "AWSDynamoDB";
    
    /** The key for remote metadata endpoint, applicable to remote clusters or Zero-ETL DynamoDB sinks */
    public static final String REMOTE_METADATA_ENDPOINT_KEY = "plugins.ml_commons.remote_metadata_endpoint";
    /** The key for remote metadata region, applicable for AWS SDK v2 connections */
    public static final String REMOTE_METADATA_REGION_KEY = "plugins.ml_commons.remote_metadata_region";
    /** The key for remote metadata service name used by service-specific SDKs */
    public static final String REMOTE_METADATA_SERVICE_NAME_KEY = "plugins.ml_commons.remote_metadata_service_name";
    
    public static final Setting<String> REMOTE_METADATA_TYPE = Setting.simpleString(REMOTE_METADATA_TYPE_KEY, Property.NodeScope, Property.Final);
    public static final Setting<String> REMOTE_METADATA_ENDPOINT = Setting.simpleString(REMOTE_METADATA_ENDPOINT_KEY, Property.NodeScope, Property.Final);
    public static final Setting<String> REMOTE_METADATA_REGION = Setting.simpleString(REMOTE_METADATA_REGION_KEY, Property.NodeScope, Property.Final);
    public static final Setting<String> REMOTE_METADATA_SERVICE_NAME = Setting.simpleString(REMOTE_METADATA_SERVICE_NAME_KEY, Property.NodeScope, Property.Final);
}
