"""持久化基础设施适配模块。

本模块提供数据持久化的适配实现，当前支持MySQL。

子模块：
- mysql: MySQL持久化适配
  - MySQLAuditResultRepository: 审核结果仓储
  - MySQLSuggestionJobRepository: 建议任务仓储
  - MySQLConnectionFactory: 数据库连接工厂
  - MySQLMigrationRunner: 数据库迁移执行器
"""
