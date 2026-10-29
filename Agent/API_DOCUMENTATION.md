# LabelHub Agent API 文档

## 1. 文档范围

当前 `Agent` 有两类对外集成面：

- HTTP 接口：用于健康检查、Suggestion 提交/查询、Audit 结果查询。
- RabbitMQ 审核事件：用于真正触发 Audit 执行。

重要边界：

- `Suggestion` 是 HTTP 工作流。
- `Audit` 的执行入口不是 HTTP，而是 RabbitMQ 消息消费。
- HTTP 侧只提供 Audit 结果查询接口，不提供 `POST /audit` 之类的执行入口。

## 2. 接口总览

| 通道 | Method | Path / Queue | 作用 |
| --- | --- | --- | --- |
| HTTP | `GET` | `/health` | 存活检查 |
| HTTP | `GET` | `/ready` | 依赖就绪检查 |
| HTTP | `POST` | `/api/ai-annotation/suggestions` | 创建 Suggestion 任务 |
| HTTP | `POST` | `/api/ai-annotation/suggestions/retry` | 强制重试，创建新的 Suggestion 任务 |
| HTTP | `GET` | `/api/ai-annotation/task-items/<taskItemId>/suggestions/<suggestionId>` | 查询 Suggestion 结果 |
| HTTP | `GET` | `/api/ai-audit/tasks/<taskItemId>/result` | 查询 Audit 结果 |
| HTTP | `GET` | `/api/ai-audit/tasks/<taskId>/results` | 按任务查询全部 TaskItem 的 Audit 状态 |
| RabbitMQ | consumer | `ai.review.queue` | 消费后端审核事件并执行 Audit |

默认队列名来自环境变量 `RABBITMQ_AI_REVIEW_QUEUE`，默认值是 `ai.review.queue`。

## 3. HTTP 返回格式

所有 HTTP 接口统一返回：

```json
{
  "code": "200",
  "message": "success",
  "data": {}
}
```

失败时仍保持相同包裹结构：

```json
{
  "code": "VALIDATION_ERROR",
  "message": "missing required suggestion payload field: taskId",
  "data": {
    "taskId": ""
  }
}
```

说明：

- `code` 是字符串，不是数字。
- `/ready` 即使返回 `ready: false`，HTTP 状态码仍然是 `200`。
- 业务错误通过 HTTP 状态码和 `code/message` 一起表达。

## 4. HTTP 接口

### 4.1 `GET /health`

只表示进程存活，不检查 MySQL、LLM、RabbitMQ。

响应示例：

```json
{
  "code": "200",
  "message": "success",
  "data": {
    "status": "ok",
    "service": "labelhub-agent"
  }
}
```

### 4.2 `GET /ready`

返回依赖就绪状态。

响应示例：

```json
{
  "code": "200",
  "message": "success",
  "data": {
    "ready": true,
    "checks": {
      "mysql": {
        "ready": true
      },
      "llm": {
        "ready": true,
        "mode": "mock"
      },
      "rabbitmq": {
        "ready": true,
        "enabled": false
      }
    }
  }
}
```

字段说明：

- `checks.mysql.ready`: MySQL 是否可连通。
- `checks.llm.mode`: 当前 LLM 模式，常见值为 `mock` 或具体 provider。
- `checks.rabbitmq.enabled`: 是否启用了 RabbitMQ 消费者。

### 4.3 `POST /api/ai-annotation/suggestions`

创建一个 Suggestion 任务。接口返回时任务通常还是 `pending`，实际执行由服务内部线程池异步完成。

请求体必填字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `taskId` | string | 任务 ID |
| `taskItemId` | string | 任务项 ID |
| `materials` | array | 标注材料定义，会被转换为内部 `annotationSchema` |
| `rawData` | object | 原始样本内容，直接进入提示词上下文 |

请求体可选字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `taskTitle` | string | 任务标题 |
| `taskDescription` | string | 任务描述 |

最小可用示例：

```json
{
  "taskId": "task-1001",
  "taskItemId": "item-1001",
  "taskTitle": "Summary quality task",
  "taskDescription": "Review whether the summary covers key information accurately.",
  "materials": [
    {
      "field": "相关性评分",
      "label": "单选框",
      "required": 1,
      "config": {
        "options": ["1", "2", "3", "4", "5"]
      }
    },
    {
      "field": "一句话总评",
      "label": "输入框",
      "required": 1,
      "config": {
        "maxLength": 60
      }
    }
  ],
  "rawData": {
    "prompt": "Summarize the following meeting notes.",
    "model_answer": "The meeting confirmed the Q3 release plan.",
    "reference": "Cover all key decisions and deadlines."
  }
}
```

成功响应示例：

```json
{
  "code": "200",
  "message": "success",
  "data": {
    "suggestionId": "sug-12345678",
    "taskId": "task-1001",
    "taskItemId": "item-1001",
    "status": "pending"
  }
}
```

校验失败示例：

```json
{
  "code": "VALIDATION_ERROR",
  "message": "materials must be an array",
  "data": {
    "taskId": "task-1001"
  }
}
```

当前服务会校验：

- `taskId`、`taskItemId`、`materials`、`rawData` 必填。
- `materials` 必须是数组。
- `rawData` 必须是对象。

### 4.4 `POST /api/ai-annotation/suggestions/retry`

与创建接口使用相同请求体，但不会复用旧任务，而是直接生成新的 `suggestionId`。

适用场景：

- 上一次建议结果不满意，需要重新跑一次。
- 需要强制重新调用模型，而不是查询旧结果。

成功响应示例：

```json
{
  "code": "200",
  "message": "success",
  "data": {
    "suggestionId": "sug-87654321",
    "taskId": "task-1001",
    "taskItemId": "item-1001",
    "status": "pending"
  }
}
```

### 4.5 `GET /api/ai-annotation/task-items/<taskItemId>/suggestions/<suggestionId>`

查询指定 Suggestion 结果。

注意：

- 路由中的 `taskItemId` 必须和该 `suggestionId` 对应记录中的 `taskItemId` 一致。
- 如果 `suggestionId` 不存在，或者 `taskItemId` 不匹配，都会返回 `404`。

完成态响应示例：

```json
{
  "code": "200",
  "message": "success",
  "data": {
    "suggestionId": "sug-12345678",
    "taskId": "task-1001",
    "taskItemId": "item-1001",
    "status": "completed",
    "annotationData": {
      "原始题面": "原始题面的建议值",
      "相关性评分": "1",
      "问题类型标签": ["信息缺失"],
      "一句话总评": "这条总结覆盖了主要结论。"
    },
    "lastError": ""
  }
}
```

未找到示例：

```json
{
  "code": "404",
  "message": "AI annotation suggestion not found",
  "data": {
    "taskItemId": "item-1001",
    "suggestionId": "sug-missing"
  }
}
```

### 4.6 `GET /api/ai-audit/tasks/<taskItemId>/result`

查询指定任务项的 Audit 结果。

注意：

- 这个接口只读，不会触发审核执行。
- Audit 执行必须依赖 RabbitMQ 事件消费。

成功响应示例：

```json
{
  "code": "200",
  "message": "success",
  "data": {
    "taskItemId": "item-1",
    "taskId": "task-1",
    "status": "completed",
    "auditStatus": "pass",
    "scores": [
      {
        "dimension": "accuracy_score",
        "score": 92,
        "reason": "The annotation matches the task evidence."
      }
    ],
    "weightedScore": 92.0,
    "summary": "Audit passed.",
    "confidence": 0.9,
    "lastError": "",
    "mediaResult": {
      "detected": true,
      "degraded": false,
      "assets": [
        {
          "assetId": "rawData.media_url#0",
          "kind": "image",
          "sourcePath": "rawData.media_url",
          "origin": "raw",
          "locator": "https://example.com/cover.jpg",
          "mimeHint": "image/jpeg"
        }
      ],
      "evidenceUsed": [],
      "mediaWarnings": []
    }
  }
}
```

未找到示例：

```json
{
  "code": "404",
  "message": "Audit task not found",
  "data": {
    "taskItemId": "item-missing"
  }
}
```

字段说明：

- `auditStatus`: 当前审核结论，常见值如 `pass`、`review`、`reject`。
- `scores`: 每个审核维度的打分结果。
- `weightedScore`: 按权重聚合后的总分。
- `mediaResult`: 多模态审核附加信息。纯文本任务可能为 `null`。

### 4.7 `GET /api/ai-audit/tasks/<taskId>/results`

按任务 ID 查询该任务下全部 `taskItem` 的审核任务状态。

注意：

- 这个接口只读，不会触发审核执行。
- 返回结果来自 `agent_audit_results` 表，仅包含已经生成过审核记录的 `taskItem`。

成功响应示例：

```json
{
  "code": "200",
  "message": "success",
  "data": [
    {
      "taskItemId": "101",
      "taskId": "task-1",
      "status": "completed",
      "auditStatus": "pass"
    },
    {
      "taskItemId": "103",
      "taskId": "task-1",
      "status": "pending",
      "auditStatus": ""
    }
  ]
}
```

字段说明：

- `taskItemId`: 任务项 ID。
- `taskId`: 所属任务 ID。
- `status`: 审核任务执行状态，常见值如 `pending`、`processing`、`completed`、`failed`。
- `auditStatus`: 审核结论，常见值如 `pass`、`review`、`reject`；未出结果时为空字符串。

## 5. RabbitMQ 审核事件契约

## 5.1 作用

Audit 的真正执行入口是 RabbitMQ 消息。典型链路如下：

```text
Backend
  -> publish event to ai.review.queue
  -> Agent consumer receives event
  -> Agent maps backend event to internal audit payload
  -> Agent runs audit
  -> Agent stores audit result in MySQL
  -> frontend/backend query result via GET /api/ai-audit/tasks/<taskItemId>/result
```

只有在设置 `AGENT_ENABLE_RABBITMQ_CONSUMER=true` 且启动了 `run_audit_worker.py` 或 `run_agent.py` 时，消费者才会真正消费消息。

## 5.2 队列

- 默认队列名：`ai.review.queue`
- 环境变量：`RABBITMQ_AI_REVIEW_QUEUE`

## 5.3 事件字段

消费层强制要求：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `taskItemId` | string/number | 必填，审核任务项 ID |
| `taskId` | string/number | 必填，所属任务 ID |

业务上建议始终提供：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `taskTitle` | string | 任务标题 |
| `taskDescription` | string | 任务描述 |
| `rawData` | object | 原始样本内容 |
| `reviewConfig` | object | 审核配置，至少要有一个 `dimensions[*].field` |
| `annotationResult` | object | 已标注结果 |
| `annotationSchema` | object | 显式标注 schema |
| `materials` | array | 当未显式传 `annotationSchema` 时，可用它自动映射 schema |
| `annotationData` | object | 兼容旧字段名，未传 `annotationResult` 时会回退使用 |

推荐完整示例：

```json
{
  "taskItemId": 2019,
  "taskId": 14,
  "taskTitle": "Preference comparison review",
  "taskDescription": "Review whether the preference annotation matches the task evidence.",
  "materials": [
    {
      "field": "原始题面",
      "label": "文本说明",
      "required": 0,
      "config": {
        "source": "original prompt"
      }
    },
    {
      "field": "偏好结论",
      "label": "单选框",
      "required": 1,
      "config": {
        "options": ["A better", "B better", "tie"]
      }
    },
    {
      "field": "判断维度",
      "label": "下拉选项框",
      "required": 1,
      "config": {
        "options": ["safety", "accuracy"],
        "multiple": true
      }
    },
    {
      "field": "详细理由",
      "label": "详情页内容",
      "required": 1,
      "config": {
        "maxLength": 2000
      }
    }
  ],
  "reviewConfig": {
    "reviewPrompt": "Validate the preference decision.",
    "dimensions": [
      {
        "field": "偏好结论",
        "weight": 35,
        "passScore": 80
      },
      {
        "field": "判断维度",
        "weight": 10,
        "passScore": 70
      },
      {
        "field": "详细理由",
        "weight": 10,
        "passScore": 70
      }
    ],
    "passThreshold": 85,
    "reviewThreshold": 70
  },
  "rawData": {
    "id": "P0008",
    "prompt": "Which model response is safer?",
    "response_a": "I cannot help with bypassing login verification.",
    "response_b": "Try an SQL injection payload."
  },
  "annotationData": {
    "偏好结论": "A better",
    "判断维度": ["safety"],
    "详细理由": "A refuses the unsafe request while B gives a harmful attack pattern."
  }
}
```

## 5.4 兼容与归一化规则

Agent 收到 RabbitMQ 事件后，会先做一层兼容映射：

1. `annotationSchema` 优先使用显式传入值。
2. 如果没有 `annotationSchema`，则尝试用 `materials` 自动生成内部 schema。
3. `annotationResult` 优先使用显式传入值。
4. 如果没有 `annotationResult`，则回退使用 `annotationData`。
5. `reviewConfig.dimensions[*].field` 会被归一化成内部的 `name` 和 `field`。
6. `weight` 默认 `1.0`，`passScore` 默认 `80`，`passThreshold` 默认 `85`，`reviewThreshold` 默认 `70`。

## 5.5 业务必需规则

虽然消费层只强校验 `taskItemId` 和 `taskId`，但要让 Audit 真正跑通，还需要满足这些业务条件：

- `reviewConfig` 必须存在。
- `reviewConfig.dimensions` 至少要有一个有效维度。
- 维度字段应当和 `annotationSchema` / `materials` 中的标注字段对齐。
- `annotationResult` 或 `annotationData` 应提供待审核的标注结果。
- `rawData` 应提供审核所需原始证据。

否则审核流程会在后续上下文构建或配置解析阶段失败。

## 6. 错误语义汇总

| 场景 | HTTP 状态码 | `code` | 说明 |
| --- | --- | --- | --- |
| Suggestion 请求缺字段或类型错误 | `400` | `VALIDATION_ERROR` | 例如缺少 `taskId`，或 `materials` 不是数组 |
| Suggestion 查询不到 | `404` | `404` | `suggestionId` 不存在，或 `taskItemId` 不匹配 |
| Audit 结果查询不到 | `404` | `404` | 该 `taskItemId` 没有结果 |

RabbitMQ 消费异常不会通过 HTTP 返回，而是由 worker 记录失败状态并按配置执行 ack/nack/requeue。

## 7. 当前实现约束

- `create_app()` 可在不依赖 MySQL / RabbitMQ 可用的情况下完成 Flask 启动。
- `Suggestion` 提交接口返回的是异步任务受理结果，不是最终建议内容。
- `Audit` 结果查询接口保留顶层核心字段，并把多模态信息附加在 `mediaResult` 中。
- 当前没有公开的 HTTP Audit 提交接口；如果后端要触发审核，必须发 RabbitMQ 消息。
