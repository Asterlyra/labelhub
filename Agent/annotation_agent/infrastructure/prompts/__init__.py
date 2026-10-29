"""提示词模板基础设施模块。

本模块管理LLM提示词模板的加载和访问。

核心组件：
- PromptTemplateRepository: 提示词模板仓储
  - get(template_id): 根据模板ID加载模板内容
  - 返回PromptTemplate对象（包含template_id和content）

- PromptTemplateLoader: 模板文件加载器
  - read_text(relative_path): 读取模板文件内容
  - 内置路径安全校验，防止目录遍历攻击
  - 模板文件根目录: infrastructure/prompts/templates/

- PromptTemplate: 提示词模板数据类
  - template_id: 模板标识符（如"audit_system"、"audit_user"）
  - content: 模板文本内容

已注册的模板：
- audit_system: 审核系统提示词模板
- audit_user: 审核用户提示词模板
- audit_default: 默认审核提示词模板
- suggestion_system: 标注建议系统提示词模板
- suggestion_user: 标注建议用户提示词模板
- retry_fix_json: 重试修复JSON的提示词模板
"""
