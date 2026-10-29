"""应用引导与配置模块。

本模块负责Agent服务的启动引导、配置管理和依赖注入。

核心组件：
- bootstrap: Flask应用引导
  - create_app(): 创建并配置Flask应用
    - 配置日志格式和级别
    - 注册HTTP路由蓝图（suggestion、audit_query）
    - 注册健康检查和就绪检查端点
    - 初始化服务容器并存入app.extensions

- container: 依赖注入容器（ServiceContainer）
  - 采用惰性单例模式（_get_or_create），按需创建服务实例
  - 管理所有服务的生命周期和依赖关系
  - 属性链：settings -> connection_factory -> repository -> service
  - run_migrations(): 首次访问仓储时自动执行数据库迁移
  - readiness_report(): 生成服务就绪报告（MySQL/LLM/RabbitMQ状态）

- settings: 运行时配置
  - AgentSettings: 顶层配置（llm/mysql/rabbitmq/backend）
  - LLMSettings: LLM配置（provider/api_key/model_name/temperature/use_mock）
  - MySQLSettings: MySQL配置（host/port/user/password/database/pool_size等）
  - RabbitMQSettings: RabbitMQ配置（enabled/host/port/queue_name/worker_concurrency等）
  - load_settings(): 从环境变量加载配置（支持.env文件）
"""
