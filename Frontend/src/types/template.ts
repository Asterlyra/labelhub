export type TemplateTab = 'hot' | 'mine'

export interface AnnotationTemplate {
  id: string
  name: string
  scene: string
  category: string
  fields: number
  usageCount: number
  updatedAt: string
  owner: string
  tags: string[]
}

export type DesignerComponentType =
  | 'text-display'
  | 'input'
  | 'textarea'
  | 'radio'
  | 'checkbox'
  | 'select'
  | 'rich-text'
  | 'image-upload'
  | 'file-upload'
  | 'json-editor'
  | 'llmInteraction'
  | 'rating'

export interface DesignerComponentMeta {
  type: DesignerComponentType
  label: string
  description: string
}

export interface CanvasComponentConfig {
  field: string
  label: string
  source?: string
  content?: string
  jsonAdditionalContent?: string
  required?: boolean
  maxLength?: number
  placeholder?: string
  multiple?: boolean
  accept?: string
  maxSize?: number
  maxCount?: number
  uploadFile?: File | null
  uploadFileName?: string
  uploadPreviewUrl?: string
  options: string[]
}

export interface CanvasComponent {
  id: string
  type: DesignerComponentType
  label: string
  fieldName: string
  config: CanvasComponentConfig
}
