"""消息队列接口适配模块。

本模块实现基于RabbitMQ的异步消息消费，用于接收后端审核事件。

核心组件：
- RabbitMQAuditConsumer: RabbitMQ审核事件消费者
  - start_blocking(): 阻塞式启动消费者，持续监听队列
  - start_in_background(): 后台线程启动消费者（守护线程）
  - stop(): 停止消费和连接
  - _connect(): 建立RabbitMQ连接，配置QoS和消费回调
  - _on_message(): 消息处理回调
    - 解码JSON消息体
    - 将审核任务投递到本地线程池
    - 在RabbitMQ I/O线程中执行basic_ack/basic_nack
    - 重试耗尽时确认消息（避免无限重投）
    - 其他失败时根据配置决定拒绝重投（basic_nack + requeue）

- RabbitMQConsumerUnavailable: 依赖不可用异常（pika未安装）

配置项（通过RabbitMQSettings）：
  - enabled: 是否启用消费者
  - host/port/username/password/vhost: 连接参数
  - queue_name: 监听的队列名
  - prefetch_count: 预取消息数量
  - worker_concurrency: 单实例内审核并发数
  - heartbeat/blocked_connection_timeout: 连接参数
  - requeue_on_failure: 失败时是否重新入队
  - max_retries: 最大重试次数
"""
