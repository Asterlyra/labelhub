"""审核校验规则模块。

本模块实现确定性校验规则链，在调用LLM之前对标注结果进行格式和完整性校验。

校验规则（均实现ValidationRule协议）：
- RequiredFieldRule: 必填字段校验，检查required=True的字段是否有值
- SingleChoiceRule: 单选字段校验（radio/select/rating类型），确保值在允许选项内且非列表
- MultiChoiceRule: 多选字段校验（checkbox/multiSelect/tags类型），确保值为列表且所有项合法
- JsonFieldRule: JSON字段校验（json/jsonEditor类型），确保值为合法JSON
- ScoreRangeRule: 评分字段校验，确保数值在有效评分范围内

执行机制：
- RuleValidationChain: 规则执行链，先检查必填，再依次执行其他规则
  - 必填校验失败时直接返回，不再执行后续规则
  - 值为空且非必填时跳过所有规则

共享辅助函数（validation_rule.py）：
- issue(): 构造校验问题字典
- is_empty(): 判断值是否为空
- option_value(): 提取选项值（支持dict和原始值）
- looks_like_score_field(): 判断字段是否为评分类型
"""
