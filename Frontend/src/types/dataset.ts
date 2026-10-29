export interface DatasetSummary {
  id: string
  projectId: string
  name: string
  sampleCount: number
  status: 'pending' | 'parsed' | 'failed'
}

export interface DatasetFieldMapping {
  sourceField: string
  targetField: string
  required: boolean
}
