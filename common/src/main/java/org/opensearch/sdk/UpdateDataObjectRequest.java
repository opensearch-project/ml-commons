/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.sdk;

import java.io.IOException;
import java.util.Map;

import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import static org.opensearch.index.seqno.SequenceNumbers.UNASSIGNED_SEQ_NO;

public class UpdateDataObjectRequest extends DataObjectRequest {

    private final Long ifSeqNo;
    private final Long ifPrimaryTerm;
    private final int retryOnConflict;
    private final ToXContentObject dataObject;

    /**
     * Instantiate this request with an index and data object.
     * <p>
     * For data storage implementations other than OpenSearch, an index may be referred to as a table and the data object may be referred to as an item.
     * @param index the index location to update the object
     * @param id the document id
     * @param tenantId the tenant id
     * @param ifSeqNo the sequence number to match or null if not required
     * @param ifPrimaryTerm the primary term to match or null if not required
     * @param retryOnConflict number of times to retry an update if a version conflict exists
     * @param dataObject the data object
     */
    public UpdateDataObjectRequest(
        String index,
        String id,
        String tenantId,
        Long ifSeqNo,
        Long ifPrimaryTerm,
        int retryOnConflict,
        ToXContentObject dataObject
    ) {
        super(index, id, tenantId);
        this.ifSeqNo = ifSeqNo;
        this.ifPrimaryTerm = ifPrimaryTerm;
        this.retryOnConflict = retryOnConflict;
        this.dataObject = dataObject;
    }

    /**
     * Returns the sequence number to match, or null if no match required
     * @return the ifSeqNo
     */
    public Long ifSeqNo() {
        return ifSeqNo;
    }

    /**
     * Returns the primary term to match, or null if no match required
     * @return the ifPrimaryTerm
     */
    public Long ifPrimaryTerm() {
        return ifPrimaryTerm;
    }

    /**
     * Returns the number of retries on version conflict
     * @return the number of retries
     */
    public int retryOnConflict() {
        return retryOnConflict;
    }

    /**
     * Returns the data object
     * @return the data object
     */
    public ToXContentObject dataObject() {
        return this.dataObject;
    }

    @Override
    public boolean isWriteRequest() {
        return true;
    }

    /**
     * Instantiate a builder for this object
     * @return a builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Class for constructing a Builder for this Request Object
     */
    public static class Builder extends DataObjectRequest.Builder<Builder> {
        private Long ifSeqNo = null;
        private Long ifPrimaryTerm = null;
        private int retryOnConflict = 0;
        private ToXContentObject dataObject = null;

        /**
         * Only perform this update request if the document's modification was assigned the given
         * sequence number. Must be used in combination with {@link #ifPrimaryTerm(long)}
         * <p>
         * Sequence number may be represented by a different document versioning key on non-OpenSearch data stores.
         */
        public Builder ifSeqNo(long seqNo) {
            if (seqNo < 0 && seqNo != UNASSIGNED_SEQ_NO) {
                throw new IllegalArgumentException("sequence numbers must be non negative. got [" + seqNo + "].");
            }
            this.ifSeqNo = seqNo;
            return this;
        }

        /**
         * Only performs this update request if the document's last modification was assigned the given
         * primary term. Must be used in combination with {@link #ifSeqNo(long)}
         * <p>
         * Primary term may not be relevant on non-OpenSearch data stores.
         */
        public Builder ifPrimaryTerm(long term) {
            if (term < 0) {
                throw new IllegalArgumentException("primary term must be non negative. got [" + term + "]");
            }
            this.ifPrimaryTerm = term;
            return this;
        }

        /**
         * Retry the update request on a version conflict exception.
         * @param retries Number of times to retry, if positive, otherwise will not retry
         * @return the updated builder
         */
        public Builder retryOnConflict(int retries) {
            this.retryOnConflict = retries;
            return this;
        }

        /**
         * Add a data object to this builder
         * @param dataObject the data object
         * @return the updated builder
         */
        public Builder dataObject(ToXContentObject dataObject) {
            this.dataObject = dataObject;
            return this;
        }

        /**
         * Add a data object as a map to this builder
         * @param dataObjectMap the data object as a map of fields
         * @return the updated builder
         */
        public Builder dataObject(Map<String, Object> dataObjectMap) {
            this.dataObject = new ToXContentObject() {
                @Override
                public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
                    return builder.map(dataObjectMap);
                }
            };
            return this;
        }

        /**
         * Builds the request
         * @return A {@link UpdateDataObjectRequest}
         */
        public UpdateDataObjectRequest build() {
            if ((ifSeqNo == null) != (ifPrimaryTerm == null)) {
                throw new IllegalArgumentException("Either ifSeqNo and ifPrimaryTerm must both be null or both must be non-null.");
            }
            return new UpdateDataObjectRequest(
                this.index,
                this.id,
                this.tenantId,
                this.ifSeqNo,
                this.ifPrimaryTerm,
                this.retryOnConflict,
                this.dataObject
            );
        }
    }
}
