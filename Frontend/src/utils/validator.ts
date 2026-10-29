import type { AnnotationDraft, SchemaField } from '../types/annotation'

export interface ValidationIssue {
  field: string
  message: string
}

export function validateAnnotationDraft(fields: SchemaField[], draft: AnnotationDraft) {
  return fields.reduce<ValidationIssue[]>((issues, field) => {
    if (field.required && draft.values[field.id] === undefined) {
      issues.push({ field: field.id, message: `${field.label} 为必填项` })
    }
    return issues
  }, [])
}
