# LabelHub Agent

## 概述

`Agent/` 是一个独立的 Flask 服务，负责两个核心工作流：

- **AI 建议**：在人工标注员开始标注前，生成 `annotationData` 草稿作为参考。
- **AI 审核**：消费后端 RabbitMQ 事件，执行审核任务，并通过只读 HTTP 接口暴露审核结果。

服务采用严格的分层架构，已不再使用旧的 `workflows / storage / schemas / llm / api` 结构。

## 架构

```text
annotation_agent/
  app/             # 启动引导、依赖容器、运行时配置
  contracts/       # 外部请求/响应契约
  interfaces/      # HTTP 和 RabbitMQ 适配器
  application/     # 用例编排服务
  domain/          # 业务规则、校验、决策逻辑
  infrastructure/  # MySQL、LLM、提示词模板、后端映射、媒体适配器
```

依赖方向为单向：

```text
interfaces -> application -> domain
               |
               -> infrastructure（通过端口/适配器）
```

## 主要流程

审核执行流程：

```text
RabbitMQ 事件
  -> 契约映射
  -> 审核上下文构建
  -> 媒体证据服务（可选）
  -> 确定性校验
  -> LLM 网关
  -> 决策策略
  -> MySQL 结果仓库
```

建议流程：

```text
HTTP 请求
  -> 建议请求契约
  -> 任务上下文构建
  -> 提示词构建（system + user 模板）
  -> LLM 网关
  -> 建议校验（Schema 检查 + 全空结果拒绝）
  -> MySQL 建议仓库
```

## 运行时日志

审核和建议流程会向 stdout 输出中文阶段日志，包括：

- 输入/输出摘要，使用字段数量和文本长度代替完整载荷内容；
- 状态转换，如 `pending`、`processing`、`completed`、`failed` 和重试耗尽；
- 上下文统计，如 Schema 字段数、审核维度数、提示词长度、输入哈希前缀；
- 各主要阶段的 LLM 调用状态和耗时。

## 接口端点

- `GET /health`：进程存活检查
- `GET /ready`：依赖就绪检查
- `POST /api/ai-annotation/suggestions`
- `POST /api/ai-annotation/suggestions/retry`
- `GET /api/ai-annotation/task-items/<taskItemId>/suggestions/<suggestionId>`
- `GET /api/ai-audit/tasks/<taskItemId>/result`
- `GET /api/ai-audit/tasks/<taskId>/results`

重要边界说明：

- `Suggestion` 通过 HTTP 触发。
- `Audit` 执行由 RabbitMQ 消息触发，没有 HTTP 提交端点。
- `GET /api/ai-audit/tasks/<taskItemId>/result` 为只读查询。
- `GET /api/ai-audit/tasks/<taskId>/results` 为只读查询，返回 `agent_audit_results` 中已有的该任务审核记录。

## 存储

MySQL Schema 通过显式 SQL 迁移文件管理，位于：

```text
annotation_agent/infrastructure/persistence/mysql/migrations/
```

服务不再使用破坏性的表重建方式。已应用的版本记录在 `agent_schema_migrations` 表中。

运行时持久化使用轻量级 PyMySQL 连接池，可通过以下参数调优：

- `AGENT_MYSQL_POOL_SIZE`
- `AGENT_MYSQL_POOL_TIMEOUT`
- `AGENT_MYSQL_POOL_RECYCLE_SECONDS`

RabbitMQ 审核消费者使用进程内线程池，可通过以下参数调优：

- `RABBITMQ_WORKER_CONCURRENCY`
- `RABBITMQ_PREFETCH_COUNT`

多模态审核证据相关配置：

- `AI_AUDIT_MEDIA_DOWNLOAD_TIMEOUT`：媒体资源下载超时（秒）
- `AI_AUDIT_VIDEO_TIMEOUT`：视频处理超时（秒）
- `AI_AUDIT_MAX_VISUAL_EVIDENCE`：最多提取的视觉证据数量

## 本地运行

```bash
cd labelhub/Agent
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
copy .env.example .env
python app.py
```

这将仅启动 HTTP 服务。默认的 `.env.example` 使用 Mock LLM 路径，本地启动不需要外部模型供应商。

如需本地运行审核消费者：

```bash
cd labelhub/Agent
python run_audit_worker.py
```

该 Worker 是审核执行的实际入口。当 `AGENT_ENABLE_RABBITMQ_CONSUMER=true` 时，它会从 `RABBITMQ_AI_REVIEW_QUEUE`（默认 `ai.review.queue`）消费消息，并在消费前确保默认的直连交换机拓扑存在。

如需在单个终端中同时查看 HTTP 和审核日志：

```bash
cd labelhub/Agent
python run_agent.py
```

该启动器始终启动 Flask HTTP 服务，当 `AGENT_ENABLE_RABBITMQ_CONSUMER=true` 时同时在后台线程启动 RabbitMQ 审核消费者。在 `FLASK_DEBUG=true` 模式下，消费者仅在 Werkzeug 服务进程内启动，避免重载器重复消费消息。

从仓库数据集生成后端风格的 MQ 重放载荷：

```bash
cd labelhub/Agent
python tests/tooling/build_dataset_mq_payloads.py
```

生成的测试数据写入 `tests/fixtures/generated/`：

- `qa_quality_mq_messages.json`
- `preference_compare_mq_messages.json`
- `all_mq_messages.json`
- `all_mq_messages.jsonl`
- `manifest.json`

将测试数据发布到 RabbitMQ 并记录每条消息的处理结果：

```bash
cd labelhub/Agent
python tests/tooling/run_mq_pressure_test.py --timeout 180
```

压力测试工具：

- 使用交换机 `ai.review.exchange` 和路由键 `ai.review` 向 RabbitMQ 发布后端风格的审核事件；
- 每次运行重写 `taskId` 和 `taskItemId` 以避免与历史结果冲突；
- 轮询 MySQL 中的 `agent_audit_results`，记录 `status`、`auditStatus`、`attemptCount`、`summary`、`lastError` 和每条消息的延迟；
- 将 JSON 和 CSV 报告写入 `tests/fixtures/generated/reports/`。

如需 macOS 双击启动，Agent 还提供了：

- `tests/manual/run_isolated_smoke_pressure_test.command`：生成载荷、启动隔离 Mock Worker、运行 5 条冒烟测试；
- `tests/manual/run_isolated_full_pressure_test.command`：生成载荷、启动隔离 Mock Worker、运行完整 42 条重放测试。

使用独立测试队列（避免与共享队列冲突）：

```bash
cd labelhub/Agent
python tests/tooling/run_mq_pressure_test.py \
  --exchange agent.pressure.test \
  --routing-key agent.pressure.test \
  --queue-name agent.pressure.test \
  --declare-topology
```

生产部署应分离角色：

- `web`：提供 HTTP 端点
- `audit-worker`：消费 RabbitMQ 并执行 LLM 审核

`run_agent.py` 仅用于本地开发调试，不适用于生产部署。

通过增加 `audit-worker` 副本数和调大 `RABBITMQ_WORKER_CONCURRENCY` 来提升审核吞吐量。

## 测试

当前测试目录结构：

```text
tests/
  conftest.py
  unit/         # 纯规则、校验器、映射器、适配器逻辑
  integration/  # 应用/容器/API/任务生命周期覆盖
  fixtures/     # 引导 SQL、示例载荷、生成的重放数据
  tooling/      # 测试数据生成和 MQ 压力测试辅助脚本
  manual/       # macOS 双击压力测试入口
```

```bash
cd labelhub/Agent
python -m pytest -q tests
```

## 备注

- `tests/fixtures/` 包含 MySQL 引导 SQL、多模态示例载荷和生成的数据集重放测试数据。
- `reviewConfig` 是审核请求的必填字段。
- 后端审核事件可以直接发送 `annotationResult`，也可以发送旧版 `annotationData` 由 Agent 内部映射。
- 后端审核事件可以发送显式的 `annotationSchema`，也可以发送 `materials` 由 Agent 内部推导 Schema。
- 审核结果保留顶层字段，并在 `mediaResult` 下追加多模态证据详情。
- 媒体处理作为审核流程的专用子模块：`application/audit/media_evidence_service.py` 配合 `infrastructure/media/`。
- 如果 OpenAI 兼容供应商拒绝多模态 `image_url` 消息块，审核客户端会自动降级为纯文本附件摘要，不会导致整个审核失败。
- 建议和审核提示词均使用 `annotation_agent/infrastructure/prompts/templates/` 下的分离式 `system` 和 `user` 模板。
- 建议和审核仓库与后端存储表隔离。
- RabbitMQ 消费者仅负责传输层，审核生命周期由应用服务处理。
