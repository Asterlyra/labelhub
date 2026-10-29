"""外部系统集成适配模块。

本模块处理Agent与外部系统（主要是后端服务）之间的数据格式转换。

核心组件：
- backend_contract_mapper: 后端事件到内部审核载荷的映射器
  - backend_context_to_audit_payload(): 将后端RabbitMQ事件转换为内部审核载荷
  - 处理annotationSchema和annotationResult的兼容性（优先使用显式值，回退到materials映射）
  - 归一化reviewConfig（维度、阈值等）

- material_schema_adapter: 素材标签到标注模式的适配器
  - materials_to_annotation_schema(): 将后端materials数组转换为annotationSchema
  - material_to_component(): 单个素材到组件的映射
  - FIELD_TYPE_BY_LABEL: 标签类型映射表（中文标签->组件类型）
    支持：单选框、多选框、下拉选项框、输入框、文本说明、详情页内容、
    多行文本框、图片组件、文件组件、评分组件等
  - 处理select+multiple->multiSelect的类型转换
"""
