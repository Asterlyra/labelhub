export type ExportFormat = 'json' | 'jsonl' | 'csv' | 'coco' | 'yolo'

export const EXPORT_FORMATS: { value: ExportFormat; label: string }[] = [
  { value: 'json', label: 'JSON' },
  { value: 'jsonl', label: 'JSONL' },
  { value: 'csv', label: 'CSV' },
  { value: 'coco', label: 'COCO' },
  { value: 'yolo', label: 'YOLO' },
]
