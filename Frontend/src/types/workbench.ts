export type WorkbenchId = number | string

export type AnnotationFormData = Record<string, unknown>

export interface WorkbenchMaterialConfig {
  field?: string
  label?: string
  required?: boolean | number | string
  multiple?: boolean | number | string
  options?: string[] | string
  placeholder?: string
  maxLength?: number | string
  accept?: string
  maxSize?: number | string
  maxCount?: number | string
  source?: string
  content?: string
  [key: string]: unknown
}

export interface WorkbenchMaterial {
  id?: WorkbenchId
  type?: string
  field?: string
  label?: string
  config?: WorkbenchMaterialConfig
  options?: string[] | string
  required?: boolean | number | string
  multiple?: boolean | number | string
  placeholder?: string
  maxLength?: number | string
  accept?: string
  maxSize?: number | string
  maxCount?: number | string
  url?: string
  content?: string
  [key: string]: unknown
}

export interface WorkbenchTask {
  id?: WorkbenchId
  taskId?: WorkbenchId
  title?: string
  name?: string
  description?: string
  templateId?: WorkbenchId
  datasetId?: WorkbenchId
  deadline?: string
  aiReviewEnabled?: boolean | number | string
  aiPreReviewEnabled?: boolean | number | string
  aiReviewConfig?: unknown
  template?: unknown
  templateJson?: unknown
  templateContent?: unknown
  templateSchema?: unknown
  materials?: WorkbenchMaterial[]
  components?: WorkbenchMaterial[]
  fields?: WorkbenchMaterial[]
  schema?: unknown
  [key: string]: unknown
}

export interface WorkbenchNavigationItem {
  taskItemId?: WorkbenchId
  id?: WorkbenchId
  index?: number | string
  status?: string
  displayStatus?: string
  displayStatusName?: string
  [key: string]: unknown
}

export interface WorkbenchProgress {
  taskId?: WorkbenchId
  totalCount?: number
  total?: number
  pendingCount?: number
  pending?: number
  draft?: number
  submitted?: number
  rejectedCount?: number
  rejected?: number
  approvedCount?: number
  reviewed?: number
  [key: string]: unknown
}

export interface WorkbenchItemDetail {
  id?: WorkbenchId
  taskItemId?: WorkbenchId
  taskId?: WorkbenchId
  rawData?: Record<string, unknown>
  annotationData?: AnnotationFormData
  displayStatus?: string
  displayStatusName?: string
  aiPreReviewResult?: unknown
  [key: string]: unknown
}
