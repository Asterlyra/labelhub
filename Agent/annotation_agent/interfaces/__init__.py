"""外部接口适配层（Interface Layer）。

本层实现Agent服务与外部通信的适配器，将外部请求转换为应用层服务调用。

子模块：
- http: HTTP接口适配
  - suggestion_routes: 建议工作流HTTP路由（提交、重试、查询）
  - audit_query_routes: 审核结果查询HTTP路由

- messaging: 消息队列接口适配
  - rabbitmq_audit_consumer: RabbitMQ审核事件消费者
    - 监听后端审核事件队列
    - 支持阻塞启动和后台线程启动
    - 实现消息确认/拒绝和重试控制
"""
