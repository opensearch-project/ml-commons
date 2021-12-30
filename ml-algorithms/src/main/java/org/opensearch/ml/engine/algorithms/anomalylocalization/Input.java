/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.engine.algorithms.anomalylocalization;

import java.util.List;
import java.util.Optional;

import org.opensearch.index.query.QueryBuilder;
import org.opensearch.search.aggregations.AggregationBuilder;

import lombok.Data;

/**
 * Information about aggregate, time, etc to localize.
 */
@Data
public class Input {

    private final String indexName; // name pattern of the data index
    private final List<String> attributeFieldNames; // name of the field to localize/slice with
    private final List<AggregationBuilder> aggregations; // aggregate data to localize/slice on
    private final String timeFieldName; // name of the timestamp field
    private final long startTime; // start of entire time range, including normal and anomaly
    private final long endTime; // end of entire time range, including normal and anomaly
    private final long minTimeInterval; // minimal time interval/bucket
    private final int numOutputs; // max number of values from localization/slicing
    private final Optional<Long> anomalyStartTime; // time when anomaly change starts
    private final Optional<QueryBuilder> filterQuery; // filter of data
}
