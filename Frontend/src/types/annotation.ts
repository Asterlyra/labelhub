export type AnnotationType = 'text' | 'image' | 'json' | 'llm-evaluation'

export interface SchemaField {
  id: string
  label: string
  type: AnnotationType
  required: boolean
}

export interface AnnotationDraft {
  taskId: string
  values: Record<string, unknown>
  updatedAt: string
}
