# Setting Up Agent Tracing and Visualization in OpenSearch

This tutorial explains how to configure and set up agent tracing and visualization in OpenSearch using OpenTelemetry, Data Prepper, and the OpenTelemetry Collector.


## Prerequisites
- Docker and Docker Compose installed
- Basic understanding of OpenSearch and OpenTelemetry concepts


## 1. Configuration Setup

### Create Configuration Directory
```bash
mkdir opensearch-tracing
cd opensearch-tracing
```

### Create Configuration Files
Create the following configuration files in your directory:
- `docker-compose.yml`: Defines the services
- `otel-collector-config.yaml`: Configures the OpenTelemetry collector
- `pipelines.yaml`: Configures Data Prepper pipelines
- `data-prepper-config.yaml`: Basic Data Prepper configuration


## 2. Docker Compose File Setup

The `docker-compose.yml` file orchestrates all the services required for agent tracing and visualization, including OpenSearch, OpenSearch Dashboards, Data Prepper, and the OpenTelemetry Collector.

### Key Points
- **Volume Mounts:**
  - Data Prepper expects its configuration files to be mounted directly to `/usr/share/data-prepper/config/data-prepper-config.yaml` and `/usr/share/data-prepper/pipelines/pipelines.yaml` inside the container.
  - Incorrect mount paths (such as mounting into subdirectories) will cause Data Prepper to fail to start.
- **Environment Variables:**
  - For OpenSearch, ensure the following environment variables are set to enable tracing features:
    - `opensearch.experimental.feature.telemetry.enabled=true`
    - `telemetry.feature.tracer.enabled=true`
    - `telemetry.tracer.enabled=true`
    - `telemetry.tracer.sampler.probability=1.0`
    - `telemetry.otel.tracer.span.exporter.class=io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter`
    - `plugins.ml_commons.tracing_enabled=true`
    - `plugins.ml_commons.agent_tracing_enabled=true`

### Minimal Example
Below is a minimal working example of the relevant parts of a `docker-compose.yml` for this setup:

```yaml
version: '3'
services:
  opensearch-node1:
    image: opensearchproject/opensearch:3.1.0
    container_name: opensearch-node1
    environment:
      - cluster.name=opensearch-cluster
      - node.name=opensearch-node1
      - discovery.type=single-node
      - bootstrap.memory_lock=true # along with the memlock settings below, disables swapping
      - "OPENSEARCH_JAVA_OPTS=-Xms512m -Xmx512m -Dopensearch.experimental.feature.telemetry.enabled=true"
      - OPENSEARCH_INITIAL_ADMIN_PASSWORD=MyPassword123!
      - DISABLE_SECURITY_PLUGIN=true
      - opensearch.experimental.feature.telemetry.enabled=true
      - telemetry.feature.tracer.enabled=true
      - telemetry.tracer.enabled=true
      - telemetry.tracer.sampler.probability=1.0
      - telemetry.otel.tracer.span.exporter.class=io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
      - plugins.ml_commons.tracing_enabled=true
      - plugins.ml_commons.agent_tracing_enabled=true
    ulimits:
      memlock:
        soft: -1
        hard: -1
      nofile:
        soft: 65536 # maximum number of open files for the OpenSearch user, set to at least 65536 on modern systems
        hard: 65536
    volumes:
      - opensearch-data1:/usr/share/opensearch/data
      # USE THE BELOW TO MOUNT YOUR LOCAL BUILD TO AN INSTANCE
      # - <YOUR_ML_COMMONS_DIRECTORY>/plugin/build/distributions:/usr/share/opensearch/<YOUR_NAME> 
    # command: bash -c "bin/opensearch-plugin remove opensearch-skills; bin/opensearch-plugin remove opensearch-ml; bin/opensearch-plugin install --batch file:///usr/share/opensearch/<YOUR_NAME>/opensearch-ml-2.16.0.0-SNAPSHOT.zip; ./opensearch-docker-entrypoint.sh opensearch"
    command: >
      bash -c "
      bin/opensearch-plugin remove opensearch-skills; 
      bin/opensearch-plugin remove opensearch-ml; 
      bin/opensearch-plugin install --batch telemetry-otel --batch file:///usr/share/opensearch/ml-plugin/opensearch-ml-3.1.0.0-SNAPSHOT.zip; 
      OPENSEARCH_LOG4J_CONFIG_FILE=/usr/share/opensearch/config/telemetry-log4j2.xml ./opensearch-docker-entrypoint.sh opensearch"
    ports:
      - 9200:9200
      - 9600:9600 # required for Performance Analyzer
    networks:
      - opensearch-net
    depends_on:
      - otel-collector
    extra_hosts:
      - "localhost:172.17.0.1"  # This maps localhost to the Docker host IP

  opensearch-dashboards:
    image: opensearchproject/opensearch-dashboards:3.1.0
    container_name: opensearch-dashboards
    ports:
      - 5601:5601
    expose:
      - "5601"
    environment:
      OPENSEARCH_HOSTS: '["http://opensearch-node1:9200"]'
      DISABLE_SECURITY_DASHBOARDS_PLUGIN: "true"
    networks:
      - opensearch-net
    depends_on:
      - opensearch-node1

  data-prepper:
    restart: unless-stopped
    container_name: data-prepper
    image: opensearchproject/data-prepper:2
    volumes:
      - ./data-prepper-config.yaml:/usr/share/data-prepper/config/data-prepper-config.yaml
      - ./pipelines.yaml:/usr/share/data-prepper/pipelines/pipelines.yaml
      - <PATH_TO_INDEX_MAPPING>/ml_agent_trace.json:/usr/share/data-prepper/ml_agent_trace.json
    ports:
      - "21890:21890"
    networks:
      - opensearch-net

  otel-collector:
    image: otel/opentelemetry-collector-contrib:latest
    container_name: otel-collector
    command: ["--config=/etc/otel-collector-config.yaml"]
    volumes:
      - ./otel-collector-config.yaml:/etc/otel-collector-config.yaml
    ports:
      - "4317:4317"  # OTLP gRPC port
      - "4318:4318"  # OTLP HTTP port
    networks:
      - opensearch-net
    depends_on:
      - data-prepper

volumes:
  opensearch-data1:

networks:
  opensearch-net:
```

**Note:**
- Adjust the file paths on the left side of the `volumes` section to match your actual directory structure.
- The above example assumes you are running `docker-compose` from the directory containing your config files.
- The environment variables for OpenSearch are critical for enabling agent tracing features.


## 3. Configure OpenTelemetry Collector

The OpenTelemetry Collector receives traces from OpenSearch agents and forwards them to Data Prepper.

Create `otel-collector-config.yaml`:
```yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318

processors:
  filter/traces:
    spans:
      include:
        match_type: regexp
        attributes:
          - key: service.type
            value: agent
  batch/traces:
    timeout: 5s
    send_batch_size: 50

exporters:
  debug:
    verbosity: detailed
  otlp/data-prepper:
    endpoint: data-prepper:21890
    tls:
      insecure: true

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [filter/traces, batch/traces]
      exporters: [debug, otlp/data-prepper]
```


## 4. Configure Data Prepper

Data Prepper processes and transforms the traces before sending them to OpenSearch.

Create `pipelines.yaml`:
```yaml
entry-pipeline:
  workers: 8
  source:
    otel_trace_source:
      ssl: false
      authentication:
        unauthenticated:
  buffer:
    bounded_blocking:
      buffer_size: 512
      batch_size: 8
  sink:
    - pipeline:
        name: "raw-trace-pipeline"
    - pipeline:
        name: "service-map-pipeline"

raw-trace-pipeline:
  workers: 8
  source:
    pipeline:
      name: "entry-pipeline"
  buffer:
    bounded_blocking:
      buffer_size: 512
      batch_size: 64
  processor:
    - otel_trace_raw:
        # flatten_attributes: false # functionality to be implemented by data prepper in the future
    - otel_trace_group:
        hosts: [ "http://opensearch-node1:9200" ]
        username: "admin"
        password: "MyPassword123!"
  sink:
    - opensearch:
        hosts: ["http://opensearch-node1:9200"]
        insecure: true
        username: admin
        password: MyPassword123!
        index_type: custom
        index: otel-v1-apm-span-agent
        template_file: ml_agent_trace.json
        template_type: index-template

service-map-pipeline:
  workers: 8
  source:
    pipeline:
      name: "entry-pipeline"
  processor:
    - service_map_stateful:
        window_duration: 360
  buffer:
    bounded_blocking:
      buffer_size: 512
      batch_size: 8
  sink:
    - opensearch:
        hosts: ["http://opensearch-node1:9200"]
        insecure: true
        username: admin
        password: MyPassword123!
        index_type: trace-analytics-service-map
```

Create `data-prepper-config.yaml`:
```yaml
ssl: false
```

## 5. Index Mapping Configuration

### Custom Index Mapping File

The `ml_agent_trace.json` file contains the custom index mapping for agent traces and connector traces. This file defines the structure and field types for the `otel-v1-apm-span-agent` index where your agent traces will be stored.

#### Locating the Index Mapping File

The `ml_agent_trace.json` file is provided in this tutorial folder at:
```
ml-commons/docs/tutorials/agent_tracing/ml_agent_trace.json
```

#### Copying and Mounting the Index Mapping

1. **Copy the file** from the tutorial folder to your configuration directory:
   ```bash
   cp ml-commons/docs/tutorials/agent_tracing/ml_agent_trace.json ./ml_agent_trace.json
   ```

2. **Update the volume mount** in your `docker-compose.yml`:
   ```yaml
   data-prepper:
     volumes:
       - ./ml_agent_trace.json:/usr/share/data-prepper/ml_agent_trace.json
   ```

#### Alternative: Custom Index Mapping

If you need to customize the index mapping for your specific use case, you can:

1. **Create your own mapping file** based on the provided `ml_agent_trace.json` from this tutorial
2. **Modify the volume mount** to point to your custom file:
   ```yaml
   data-prepper:
     volumes:
       - ./your-custom-mapping.json:/usr/share/data-prepper/ml_agent_trace.json
   ```

#### Important Notes

- **File Location**: The file must be mounted at `/usr/share/data-prepper/ml_agent_trace.json` inside the Data Prepper container
- **Template Reference**: The `pipelines.yaml` references this file in the `template_file` field
- **Index Creation**: Data Prepper will use this template to create the `otel-v1-apm-span-agent` index with the proper mapping
- **Field Compatibility**: Ensure your custom mapping includes all required fields for proper trace visualization in OpenSearch Dashboards


## 6. Start the Services

Start all services:
```bash
docker-compose up -d
```

Verify services are running:
```bash
docker-compose ps
```


## 7. Enable/Disable Agent Tracing at Runtime

You can enable or disable agent tracing and connector tracing at runtime using the OpenSearch cluster settings API:

```bash
curl -X PUT "localhost:9200/_cluster/settings" -H 'Content-Type: application/json' -d'
{
  "persistent": {
    "plugins.ml_commons.agent_tracing_enabled": true,
    "plugins.ml_commons.connector_tracing_enabled": true
  }
}'
```
Note: The `plugins.ml_commons.agent_tracing_enabled` setting only takes effect if the static setting `plugins.ml_commons.tracing_enabled` is already enabled in your OpenSearch configuration.

Use this API to turn agent tracing on or off without restarting your cluster, as long as tracing is enabled globally.


## 8. View Traces

- Open OpenSearch Dashboards at [http://localhost:5601](http://localhost:5601)
- Navigate to **Observability → Traces**
- You should see agent traces appearing as you execute agents


## 9. Understanding the Visualizations

The traces visualization provides several main views to help you analyze and understand your trace data:

### Table View
![Table View](images/trace_table_view.png)
*Table View: This view displays each span as a row in a table, with columns such as Span ID, Trace ID, Parent Span ID, Duration, and Status. It allows you to include and exclude different columns as well. Shown above is a customization that might be useful for agent trace understanding. The columns include `Span ID`, `Duration (ms)`, `span.attributes.gen_ai@operation@name`, `span.attributes.gen_ai@usage@total_tokens`, `span.attributes.gen_ai@agent@task`, and `span.attributes.gen_ai@agent@result`.*

After clicking into one specific trace (agent execution) you arrive at the Trace Analytics visualizations shown below.

### Timeline/List View
![Timeline View](images/trace_timeline_view.png)
*Timeline/Waterfall View: The timeline or waterfall view presents traces in chronological order, showing the sequence of spans as they occurred. Each entry is also clickable which creates a pop-up of all the information stored within that span.*

### Hierarchical List View
![Hierarchical View](images/trace_hierarchical_view.png)
*Hierarchical Tree View: This view organizes spans in a tree-like structure, reflecting the parent-child relationships between operations. It helps you visualize the call hierarchy and understand how different services and components interact within a trace.*

---

### Vega Visualization: Trace Graph

In addition to Trace Analytics, OpenSearch Dashboards also supports [Vega](https://vega.github.io/vega/) visualizations for advanced, custom graphing. You can use Vega to create interactive graphs of your trace data, such as service dependency graphs or span relationships.

Simply copy `trace-graph-vega.json` into the Vega editor in OpenSearch Dashboards and change the `traceId` to be the traceId of the run that was just executed.

> **How to use:**
> 1. Go to **OpenSearch Dashboards → Visualize → Create visualization → Vega**.
> 2. Paste the Vega spec into the editor.
> 3. Adjust the data and layout as needed for your trace data.

![Vega Graph View](images/vega_trace_graph.png)
*Vega Graph View: This custom graph visualization shows the relationships between services and spans in your trace data, helping you understand dependencies and flow at a glance.*

---

## Troubleshooting

If traces aren't appearing:
- Check OpenTelemetry Collector logs: `docker-compose logs otel-collector`
- Check Data Prepper logs: `docker-compose logs data-prepper`
- Verify index creation: `curl localhost:9200/_cat/indices`
- Verify index tempate: `curl localhost:9200/_index_template`

**Common issues:**
- Incorrect endpoint configurations
- Missing permissions
- Disabled tracing settings

---

## Additional Notes
- Traces are stored in the `otel-v1-apm-span-agent` index
- The sampling rate is set to 100% (`1.0`) for development
- For production, consider adjusting sampling rate and buffer sizes

---

## References
- [OpenSearch Observability documentation](https://opensearch.org/docs/latest/observing-your-data/)
- [OpenTelemetry documentation](https://opentelemetry.io/docs/)
- [Data Prepper documentation](https://opensearch.org/docs/latest/data-prepper/) 