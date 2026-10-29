import type { DesignerComponentMeta } from '../types/template'

export const designerComponents: DesignerComponentMeta[] = [
  {
    type: 'text-display',
    label: '文本展示',
    description: '展示原始文本、说明或样本文案',
  },
  {
    type: 'input',
    label: '输入框',
    description: '用于短文本字段录入',
  },
  {
    type: 'textarea',
    label: '多行文本框',
    description: '用于长文本、原因和备注',
  },
  {
    type: 'radio',
    label: '单选框',
    description: '用于互斥选项标注',
  },
  {
    type: 'checkbox',
    label: '多选框',
    description: '用于多标签、多类别选择',
  },
  {
    type: 'select',
    label: '下拉选择',
    description: '用于较多候选项选择',
  },
  {
    type: 'rich-text',
    label: '富文本编辑框',
    description: '用于编辑带格式的详情内容',
  },
  {
    type: 'image-upload',
    label: '图片组件',
    description: '上传图片物料并绑定字段',
  },
  {
    type: 'file-upload',
    label: '文件组件',
    description: '上传文档物料并绑定字段',
  },
  {
    type: 'json-editor',
    label: 'JSON编辑器',
    description: '编辑并校验规范的 JSON 内容',
  },
  {
    type: 'rating',
    label: '评分组件',
    description: '用于满意度、质量等级评分',
  },
  {
    type: 'llmInteraction',
    label: 'LLM触发器',
    description: '触发 AI 预审建议生成',
  },
]
