# 本地测试：用户自定义 id 开关（user_defined_id_enabled）

本文档说明如何在本地启动 ml-commons,并手动验证“自定义 agent-id / model-id / connector-id”功能在
新增 cluster setting `plugins.ml_commons.user_defined_id_enabled` 控制下的行为。

- 该 setting **默认关闭(false)**：传自定义 id 会被拒绝(403 FORBIDDEN)。
- 开启后:可以用自定义 id 创建资源;重复 id 会冲突报错(409 CONFLICT)而不是覆盖。

---

## 1. 本地启动集群

在仓库根目录执行:

```bash
./gradlew run
```

- 启动单节点集群,并自动安装当前改动后的 ml-commons 插件。
- 默认无 security,HTTP 端口 `localhost:9200`。
- 日志位于 `build/cluster/run node0/opensearch-<version>/logs`。
- 需要调试时:`./gradlew run --debug-jvm`(JVM 挂起,等待 debugger 连到 `localhost:8000`)。

确认集群已就绪:

```bash
curl -s localhost:9200/ | jq .
```

> 先决条件:已设置 `plugins.ml_commons.trusted_connector_endpoints_regex` 等以允许 connector 注册(见下方第 2 步会一并设置)。

---

## 2. 验证：功能默认关闭时,自定义 id 被拒绝

不修改任何设置(默认 false)。

### 2.1 connector(应返回 403)

```bash
curl -s -X POST localhost:9200/_plugins/_ml/connectors/_create \
  -H 'Content-Type: application/json' -d '{
    "id": "my-custom-connector",
    "name": "Test Connector",
    "description": "should be rejected",
    "version": 1,
    "protocol": "http",
    "parameters": { "endpoint": "api.test.com", "model": "test-model" },
    "credential": { "key": "placeholder_api_key" },
    "actions": [{
      "action_type": "predict",
      "method": "POST",
      "url": "https://api.test.com/v1/completions",
      "request_body": "{}"
    }]
  }' | jq .
```

预期:HTTP 403,错误信息形如
`Specifying a custom id is not enabled. To enable, please update the setting plugins.ml_commons.user_defined_id_enabled`。

### 2.2 model(应返回 403)

```bash
curl -s -X POST localhost:9200/_plugins/_ml/models/_register \
  -H 'Content-Type: application/json' -d '{
    "id": "my-custom-model",
    "name": "Test Model",
    "function_name": "remote",
    "model_group_id": "some_group_id",
    "description": "should be rejected"
  }' | jq .
```

预期:HTTP 403,同样的 disabled 提示。

### 2.3 agent(应返回 403)

```bash
curl -s -X POST localhost:9200/_plugins/_ml/agents/_register \
  -H 'Content-Type: application/json' -d '{
    "id": "my-custom-agent",
    "name": "Test Agent",
    "type": "flow",
    "description": "should be rejected"
  }' | jq .
```

预期:HTTP 403,同样的 disabled 提示。

> 对照:把上述请求体里的 `"id": ...` 这一行去掉,资源应能正常创建(系统自动生成 id),说明只有“传自定义 id”这条路径被拦截。

---

## 3. 开启功能

```bash
curl -s -X PUT localhost:9200/_cluster/settings \
  -H 'Content-Type: application/json' -d '{
    "persistent": {
      "plugins.ml_commons.user_defined_id_enabled": true,
      "plugins.ml_commons.trusted_connector_endpoints_regex": ["^.*$"]
    }
  }' | jq .
```

该 setting 是 `Dynamic` 的,无需重启即可生效。

---

## 4. 验证：功能开启后,自定义 id 生效

### 4.1 用自定义 id 创建 connector(应成功,返回的 connector_id == 自定义值)

```bash
curl -s -X POST localhost:9200/_plugins/_ml/connectors/_create \
  -H 'Content-Type: application/json' -d '{
    "id": "my-custom-connector",
    "name": "Test Connector",
    "description": "custom id test",
    "version": 1,
    "protocol": "http",
    "parameters": { "endpoint": "api.test.com", "model": "test-model" },
    "credential": { "key": "placeholder_api_key" },
    "actions": [{
      "action_type": "predict",
      "method": "POST",
      "url": "https://api.test.com/v1/completions",
      "request_body": "{}"
    }]
  }' | jq .
```

预期:返回 `{"connector_id": "my-custom-connector"}`。

### 4.2 重复同一个 id 再创建一次(应返回 409 CONFLICT)

重复执行 4.1 的请求。预期:HTTP 409,提示文档已存在,而**不是**覆盖原有 connector。

### 4.3 用自定义 id 注册 agent(应成功)

```bash
curl -s -X POST localhost:9200/_plugins/_ml/agents/_register \
  -H 'Content-Type: application/json' -d '{
    "id": "my-custom-agent",
    "name": "Test Agent",
    "type": "flow",
    "description": "custom id test",
    "tools": []
  }' | jq .
```

预期:返回 `{"agent_id": "my-custom-agent"}`。可再用
`GET localhost:9200/_plugins/_ml/agents/my-custom-agent` 确认。

---

## 5. 关闭功能(回归默认)

```bash
curl -s -X PUT localhost:9200/_cluster/settings \
  -H 'Content-Type: application/json' -d '{
    "persistent": { "plugins.ml_commons.user_defined_id_enabled": null }
  }' | jq .
```

置为 `null` 即恢复默认值 `false`,之后再传自定义 id 又会被 403 拒绝。

---

## 6. 跑自动化测试(可选)

```bash
# 相关单元测试
./gradlew :opensearch-ml-common:test --tests "org.opensearch.ml.common.settings.MLFeatureEnabledSettingTests"
./gradlew :opensearch-ml-plugin:test \
  --tests "org.opensearch.ml.settings.MLFeatureEnabledSettingTests" \
  --tests "org.opensearch.ml.action.agents.RegisterAgentTransportActionTests" \
  --tests "org.opensearch.ml.action.connector.TransportCreateConnectorActionTests" \
  --tests "org.opensearch.ml.action.register.TransportRegisterModelActionTests"

# 端到端集成测试(IT 内部会先把 setting 打开)
./gradlew integTest --tests "org.opensearch.ml.rest.RestMLCustomIdIT"
```
