"""建议领域模块。

当前建议领域的核心逻辑复用shared模块的TaskContext和SchemaField。
建议特有的领域行为（如结果工厂、提示词构建）分布在application层的suggestion子包中。

未来可在此模块扩展建议特有的领域模型，如：
- SuggestionResult: 建议结果实体
- SuggestionPolicy: 建议生成策略
"""
