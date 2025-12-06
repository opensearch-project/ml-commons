#!/bin/bash
#
# Log Ingestion Script for ML Commons
# Tails OpenSearch logs and indexes them into ml-commons-logs-* index
#
# Usage: ./ingest-logs.sh [log_directory] [opensearch_url]
#

set -e

# Configuration
LOG_DIR="${1:-}"
OPENSEARCH_URL="${2:-http://localhost:9200}"
INDEX_PREFIX="ml-commons-logs"
BATCH_SIZE=50
BATCH_TIMEOUT_MS=500
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INDEX_TEMPLATE_FILE="${SCRIPT_DIR}/index-template.json"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Auto-detect log directory if not provided
detect_log_dir() {
    if [[ -n "$LOG_DIR" && -d "$LOG_DIR" ]]; then
        echo "$LOG_DIR"
        return
    fi

    # Try common locations
    local base_dir="${SCRIPT_DIR}/../.."
    local candidates=(
        "${base_dir}/build/testclusters/integTest-0/logs"
        "${base_dir}/plugin/build/testclusters/integTest-0/logs"
        "${base_dir}/build/cluster/run node0/opensearch-*/logs"
    )

    for candidate in "${candidates[@]}"; do
        # Use glob expansion
        for dir in $candidate; do
            if [[ -d "$dir" ]]; then
                echo "$dir"
                return
            fi
        done
    done

    log_error "Could not auto-detect log directory. Please provide it as an argument."
    exit 1
}

# Wait for cluster to be healthy
wait_for_cluster() {
    log_info "Waiting for OpenSearch cluster to be healthy..."
    local max_attempts=60
    local attempt=0

    while [[ $attempt -lt $max_attempts ]]; do
        if curl -s "${OPENSEARCH_URL}/_cluster/health" 2>/dev/null | grep -qE '"status"\s*:\s*"(green|yellow)"'; then
            log_info "Cluster is healthy!"
            return 0
        fi
        attempt=$((attempt + 1))
        sleep 1
    done

    log_error "Cluster did not become healthy within ${max_attempts} seconds"
    exit 1
}

# Create index template
create_index_template() {
    log_info "Creating index template..."

    if [[ ! -f "$INDEX_TEMPLATE_FILE" ]]; then
        log_error "Index template file not found: $INDEX_TEMPLATE_FILE"
        exit 1
    fi

    local response
    response=$(curl -s -X PUT "${OPENSEARCH_URL}/_index_template/${INDEX_PREFIX}" \
        -H "Content-Type: application/json" \
        -d @"$INDEX_TEMPLATE_FILE" 2>&1)

    if echo "$response" | grep -q '"acknowledged":true'; then
        log_info "Index template created successfully"
    else
        log_warn "Index template response: $response"
    fi
}

# Get today's index name
get_index_name() {
    echo "${INDEX_PREFIX}-$(date +%Y.%m.%d)"
}

# Parse a single log line into JSON
# OpenSearch log format: [timestamp][LEVEL ][logger] [node] message
parse_log_line() {
    local line="$1"
    local source_file="$2"

    # Try to parse structured log format
    if [[ "$line" =~ ^\[([^\]]+)\]\[([A-Z]+)[[:space:]]*\]\[([^\]]+)\][[:space:]]*\[([^\]]+)\][[:space:]]*(.*) ]]; then
        local timestamp="${BASH_REMATCH[1]}"
        local level="${BASH_REMATCH[2]}"
        local logger="${BASH_REMATCH[3]}"
        local node="${BASH_REMATCH[4]}"
        local message="${BASH_REMATCH[5]}"

        # Convert timestamp format (replace comma with dot for millis)
        timestamp="${timestamp//,/.}"

        # Escape special characters in message for JSON
        message=$(echo "$message" | sed 's/\\/\\\\/g; s/"/\\"/g; s/\t/\\t/g' | tr -d '\r')

        echo "{\"@timestamp\":\"${timestamp}\",\"level\":\"${level}\",\"logger\":\"${logger}\",\"node\":\"${node}\",\"message\":\"${message}\",\"source_file\":\"${source_file}\"}"
    else
        # Fallback for unstructured lines
        local escaped_line
        escaped_line=$(echo "$line" | sed 's/\\/\\\\/g; s/"/\\"/g; s/\t/\\t/g' | tr -d '\r')
        local timestamp
        timestamp=$(date -u +"%Y-%m-%dT%H:%M:%S.000")

        echo "{\"@timestamp\":\"${timestamp}\",\"level\":\"UNKNOWN\",\"logger\":\"raw\",\"node\":\"unknown\",\"message\":\"${escaped_line}\",\"source_file\":\"${source_file}\"}"
    fi
}

# Send batch to OpenSearch
send_batch() {
    local bulk_data="$1"

    if [[ -z "$bulk_data" ]]; then
        return
    fi

    local index_name
    index_name=$(get_index_name)

    local response
    response=$(curl -s -X POST "${OPENSEARCH_URL}/_bulk" \
        -H "Content-Type: application/x-ndjson" \
        --data-binary "$bulk_data" 2>&1)

    if echo "$response" | grep -q '"errors":true'; then
        log_warn "Some documents failed to index"
    fi
}

# Main ingestion loop
ingest_logs() {
    local log_dir="$1"
    local batch=""
    local batch_count=0
    local last_send_time
    # macOS doesn't support %3N, so use seconds only
    last_send_time=$(date +%s)

    log_info "Starting log ingestion from: $log_dir"
    log_info "Indexing to: $(get_index_name)"
    log_info "Press Ctrl+C to stop"

    # Find all .log files and tail them
    local log_files=()
    while IFS= read -r -d '' file; do
        log_files+=("$file")
    done < <(find "$log_dir" -name "*.log" -type f -print0 2>/dev/null)

    if [[ ${#log_files[@]} -eq 0 ]]; then
        log_warn "No log files found in $log_dir"
        log_info "Waiting for log files to appear..."

        # Wait for log files
        while [[ ${#log_files[@]} -eq 0 ]]; do
            sleep 2
            while IFS= read -r -d '' file; do
                log_files+=("$file")
            done < <(find "$log_dir" -name "*.log" -type f -print0 2>/dev/null)
        done
    fi

    log_info "Found ${#log_files[@]} log file(s)"

    local index_name
    index_name=$(get_index_name)

    # Use tail -F to follow all log files
    tail -F -n 0 "${log_files[@]}" 2>/dev/null | while IFS= read -r line; do
        # Skip empty lines and tail headers
        if [[ -z "$line" || "$line" =~ ^==\>.*/.*\<==$ ]]; then
            continue
        fi

        # Extract source file from tail output (if present)
        local source_file="unknown"
        if [[ "$line" =~ ^==\>\ (.*)\ \<==$ ]]; then
            continue
        fi

        # Parse and add to batch
        local json_doc
        json_doc=$(parse_log_line "$line" "$source_file")

        if [[ -n "$json_doc" ]]; then
            batch+="{\"index\":{\"_index\":\"${index_name}\"}}"$'\n'
            batch+="${json_doc}"$'\n'
            batch_count=$((batch_count + 1))
        fi

        # Check if we should send the batch (use seconds for macOS compatibility)
        local current_time
        current_time=$(date +%s)
        local time_diff=$((current_time - last_send_time))

        # Send batch every 1 second or when batch is full
        if [[ $batch_count -ge $BATCH_SIZE || $time_diff -ge 1 ]]; then
            if [[ $batch_count -gt 0 ]]; then
                send_batch "$batch"
                batch=""
                batch_count=0
                last_send_time=$current_time
            fi
        fi
    done
}

# Cleanup on exit
cleanup() {
    log_info "Shutting down log ingestion..."
    # Kill any background processes
    jobs -p | xargs -r kill 2>/dev/null || true
    exit 0
}

trap cleanup SIGINT SIGTERM

# Main execution
main() {
    log_info "ML Commons Log Ingestion Script"
    log_info "================================"

    LOG_DIR=$(detect_log_dir)
    log_info "Log directory: $LOG_DIR"
    log_info "OpenSearch URL: $OPENSEARCH_URL"

    wait_for_cluster
    create_index_template
    ingest_logs "$LOG_DIR"
}

main "$@"
