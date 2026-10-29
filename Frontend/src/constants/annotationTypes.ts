import type { AnnotationType } from '../types/annotation'

export const ANNOTATION_TYPES: { value: AnnotationType; label: string }[] = [
  { value: 'text', label: '文本标注' },
  { value: 'image', label: '图片标注' },
  { value: 'json', label: 'JSON 标注' },
  { value: 'llm-evaluation', label: 'LLM 评价' },
]
