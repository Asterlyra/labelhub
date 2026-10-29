"""MySQL持久化适配模块。

本模块实现基于MySQL的数据持久化，包括审核结果和建议任务的存储。

核心组件：
- MySQLConnectionFactory: 数据库连接工厂
  - 基于PyMySQL创建连接，配置主机、端口、认证、字符集等
  - 自动提交模式，使用DictCursor返回字典结果

- MySQLAuditResultRepository: 审核结果仓储
  - save_result(): 保存/更新审核结果（UPSERT语义）
  - get_result(): 按task_item_id查询审核结果
  - get_result_by_input_hash(): 按输入哈希查询（用于幂等判断）
  - save_execution_trace(): 保存执行追踪（MQ载荷、LLM提示词）
  - mark_pending/processing/completed/failed/max_retries_exceeded(): 任务状态流转

- MySQLSuggestionJobRepository: 建议任务仓储
  - create_job(): 创建pending状态的建议任务
  - get_job(): 按suggestion_id查询任务
  - mark_processing/completed/failed(): 任务状态流转

- MySQLMigrationRunner: 数据库迁移执行器
  - 非破坏性迁移：只执行未应用的SQL文件
  - 通过agent_schema_migrations表跟踪已应用版本

辅助函数：
- dumps_json(): JSON序列化（ensure_ascii=False）
- loads_json(): JSON反序列化（支持bytes和None默认值）

数据库表：
- agent_audit_results: 审核结果表
- agent_annotation_suggestions: 建议任务表
- agent_schema_migrations: 迁移版本跟踪表
"""
