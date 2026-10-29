import type {
  AnnotationFormData,
  WorkbenchItemDetail,
  WorkbenchMaterial,
  WorkbenchNavigationItem,
  WorkbenchProgress,
  WorkbenchTask,
} from './workbench'

export type ApiId = number | string

export type ApiPrimitive = string | number | boolean

export type ApiQueryValue = ApiPrimitive | null | undefined | ApiPrimitive[]

export type ApiQueryParams = Record<string, ApiQueryValue>

export interface ApiResponse<T = unknown> {
  code?: number
  message?: string
  msg?: string
  data: T
  [key: string]: unknown
}

export interface ApiPageResult<T> {
  records: T[]
  total?: number
  size?: number
  current?: number
  pages?: number
  [key: string]: unknown
}

export interface ApiFileField {
  file: File
  field: string
}

export interface GetTemplatesParams extends ApiQueryParams {
  keyword?: string
  status?: number
  pageNum?: number
  pageSize?: number
}

export interface GetDatasetParams extends ApiQueryParams {
  keyword?: string
  status?: string
  sourceFormat?: string
  pageNum?: number
  pageSize?: number
}

export interface PageQueryParams extends ApiQueryParams {
  pageNum?: number
  pageSize?: number
}

export interface TaskListParams extends PageQueryParams {
  keyword?: string
  status?: string
  tag?: string
}

export interface TaskItemsDetailParams extends PageQueryParams {
  status?: string
}

export interface UserRegisterPayload {
  username: string
  password: string
  nickname?: string
  email?: string
  phone?: string
}

export interface UpdateProfilePayload {
  nickname?: string
  email?: string
  phone?: string
  avatar?: string
}

export interface UserInfo {
  id?: ApiId
  username?: string
  nickname?: string
  email?: string
  phone?: string
  avatar?: string
  roles?: string[]
  role?: string
  [key: string]: unknown
}

export interface LoginData {
  token?: string
  satoken?: string
  user?: UserInfo
  roles?: string[]
  [key: string]: unknown
}

export type LoginResponse = ApiResponse<LoginData>

export type UserInfoResponse = ApiResponse<UserInfo>

export type UserRolesResponse = ApiResponse<string[] | UserInfo>

export type UserRegisterResponse = ApiResponse<UserInfo>

export type UpdateProfileResponse = ApiResponse<UserInfo>

export interface TemplatePayload {
  id?: ApiId
  name?: string
  title?: string
  description?: string
  category?: string
  tags?: string[]
  components?: unknown[]
  [key: string]: unknown
}

export interface TemplateRecord extends TemplatePayload {
  creatorId?: ApiId
  creatorName?: string
  nickname?: string
  createTime?: string
  updateTime?: string
  status?: string | number
}

export type TemplateListResponse = ApiResponse<ApiPageResult<TemplateRecord> | TemplateRecord[]>

export type TemplateDetailResponse = ApiResponse<TemplateRecord>

export type TemplateMutationResponse = ApiResponse<TemplateRecord | null>

export interface DatasetSchemaField {
  sourceName?: string
  targetName?: string
  sampleValue?: unknown
  enabled?: boolean
  sampleType?: string
  name?: string
  [key: string]: unknown
}

export interface DatasetSchema {
  fields?: DatasetSchemaField[]
  [key: string]: unknown
}

export interface DatasetRecord {
  id?: ApiId
  name?: string
  description?: string
  sourceFormat?: string
  successCount?: number
  totalCount?: number
  status?: string
  schema?: DatasetSchema | string
  files?: Array<{
    fileUrl?: string
    fileName?: string
    sourceFormat?: string
    [key: string]: unknown
  }>
  createTime?: string
  updateTime?: string
  [key: string]: unknown
}

export interface DatasetPreviewData {
  sourceFormat?: string
  fields: DatasetSchemaField[]
}

export type DatasetListResponse = ApiResponse<ApiPageResult<DatasetRecord> | DatasetRecord[]>

export type DatasetDetailResponse = ApiResponse<DatasetRecord>

export type DatasetPreviewResponse = ApiResponse<DatasetPreviewData>

export type DatasetMutationResponse = ApiResponse<DatasetRecord | null>

export type DistributionStrategyType =
  | 'FIRST_COME_FIRST_SERVED'
  | 'ASSIGNMENT'
  | 'AVERAGE'
  | 'RANDOM'
  | string

export interface TaskDistributionConfig {
  strategyType: DistributionStrategyType
  maxClaimPerUser?: number
  claimCount?: number
  labelerIds?: ApiId[]
  config?: {
    maxClaimPerUser?: number
    claimCount?: number
    labelerIds?: ApiId[]
    [key: string]: unknown
  }
  [key: string]: unknown
}

export interface TaskAiReviewDimension {
  field?: string
  weight?: number
  passScore?: number
  [key: string]: unknown
}

export interface TaskAiReviewConfig {
  id?: ApiId
  taskId?: ApiId
  reviewPrompt?: string
  passThreshold?: number
  reviewThreshold?: number
  dimensions?: TaskAiReviewDimension[]
  createTime?: string
  updateTime?: string
  [key: string]: unknown
}

export interface CreateTaskPayload {
  title?: string
  description?: string
  datasetId?: ApiId
  templateId?: ApiId
  quota?: number
  rewardRule?: string
  deadline?: string
  tags?: string[]
  reviewerIds?: ApiId[]
  distributionConfig?: TaskDistributionConfig
  aiReviewEnabled?: boolean
  aiReviewConfig?: TaskAiReviewConfig
  [key: string]: unknown
}

export type UpdateTaskPayload = Partial<CreateTaskPayload>

export interface TaskProgress {
  taskId?: ApiId
  totalCount?: number
  unassignedCount?: number
  claimedCount?: number
  submittedCount?: number
  rejectedCount?: number
  reviewedCount?: number
  approvedCount?: number
  pendingCount?: number
  [key: string]: unknown
}

export interface TaskRecord {
  id?: ApiId
  taskId?: ApiId
  title?: string
  taskTitle?: string
  description?: string
  taskDescription?: string
  quota?: number
  availableCount?: number
  claimedCount?: number
  annotatedCount?: number
  approvedCount?: number
  rejectedCount?: number
  pendingCount?: number
  totalCount?: number
  status?: string
  taskStatus?: string
  deadline?: string
  createTime?: string
  updateTime?: string
  tags?: string[]
  progress?: TaskProgress
  datasetName?: string
  templateName?: string
  aiReviewEnabled?: boolean
  reviewerIds?: ApiId[]
  distributionConfig?: TaskDistributionConfig
  aiReviewConfig?: TaskAiReviewConfig
  [key: string]: unknown
}

export interface TaskItemRecord {
  id?: ApiId
  taskId?: ApiId
  datasetItemId?: ApiId
  labelerId?: ApiId
  labelerName?: string
  status?: string
  annotationData?: AnnotationFormData
  reviewerId?: ApiId | null
  reviewerName?: string | null
  reviewComment?: string | null
  createTime?: string
  updateTime?: string
  [key: string]: unknown
}

export type TaskListResponse = ApiResponse<ApiPageResult<TaskRecord> | TaskRecord[]>

export type TaskDetailResponse = ApiResponse<TaskRecord>

export type TaskMutationResponse = ApiResponse<TaskRecord | null>

export type TaskItemsDetailResponse = ApiResponse<ApiPageResult<TaskItemRecord>>

export type TaskMarketplaceTagsResponse = ApiResponse<string[]>

export interface ReviewerRecord extends UserInfo {
  id?: ApiId
  reviewerId?: ApiId
}

export interface LabelerRecord extends UserInfo {
  id?: ApiId
  labelerId?: ApiId
}

export type ReviewersResponse = ApiResponse<ReviewerRecord[]>

export type LabelersResponse = ApiResponse<ApiPageResult<LabelerRecord> | LabelerRecord[]>

export interface WorkbenchWorkspaceData {
  task?: WorkbenchTask
  materials?: WorkbenchMaterial[]
  items?: WorkbenchNavigationItem[]
  progress?: WorkbenchProgress
  currentItem?: WorkbenchItemDetail
  [key: string]: unknown
}

export type WorkbenchInitResponse = ApiResponse<WorkbenchWorkspaceData>

export type WorkbenchItemDetailResponse = ApiResponse<WorkbenchItemDetail>

export type AnnotationMutationResponse = ApiResponse<WorkbenchItemDetail | null>

export interface ReviewActionPayload {
  reviewComment?: string
  annotationData?: AnnotationFormData
  [key: string]: unknown
}

export interface ReviewWorkspaceData extends WorkbenchWorkspaceData {
  taskItems?: WorkbenchNavigationItem[]
}

export type ReviewTasksResponse = ApiResponse<ApiPageResult<TaskRecord> | TaskRecord[]>

export type ReviewTaskDetailResponse = ApiResponse<WorkbenchItemDetail>

export type ReviewWorkspaceResponse = ApiResponse<ReviewWorkspaceData>

export type ReviewActionResponse = ApiResponse<WorkbenchItemDetail | null>

export interface TaskTimelineRecord {
  id?: ApiId
  taskItemId?: ApiId
  operatorName?: string
  operatorAction?: 'REJECT' | 'SUBMIT' | 'APPROVE' | 'AI_PRE_REVIEW' | string
  operatorOrder?: number
  comment?: string | null
  createTime?: string
  [key: string]: unknown
}

export type TaskTimelineResponse = ApiResponse<TaskTimelineRecord[]>

export interface AiSuggestionData {
  taskId?: ApiId
  taskItemId?: ApiId
  suggestionId?: string
  status?: 'pending' | 'processing' | 'completed' | 'failed' | string
  annotationData?: AnnotationFormData
  lastError?: string | null
  [key: string]: unknown
}

export type AiSuggestionResponse = ApiResponse<AiSuggestionData>

export interface AiAuditScore {
  dimension?: string
  passScore?: number
  reason?: string
  score?: number
  weight?: number
  [key: string]: unknown
}

export interface AiAuditResult {
  auditStatus?: 'pass' | 'review' | 'manual_review' | 'reject' | string
  confidence?: number
  lastError?: string | null
  mediaResult?: unknown
  scores?: AiAuditScore[]
  status?: 'pending' | 'processing' | 'completed' | 'failed' | string
  summary?: string
  taskId?: ApiId
  taskItemId?: ApiId
  weightedScore?: number
  [key: string]: unknown
}

export type AiAuditListResponse = ApiResponse<ApiPageResult<AiAuditResult> | AiAuditResult[]>

export type AiAuditDetailResponse = ApiResponse<AiAuditResult>

export interface OutputPreviewField {
  sourceName?: string
  sampleValue?: unknown
  targetName?: string
  enabled?: boolean
  [key: string]: unknown
}

export type OutputFormat = 'json' | 'jsonl' | 'csv' | 'xlsx'

export interface CreateOutputTaskPayload {
  format?: OutputFormat
  fields?: OutputPreviewField[]
  [key: string]: unknown
}

export interface OutputTaskHistoryRecord {
  id?: ApiId
  taskId?: ApiId
  format?: OutputFormat | string
  fileName?: string
  fileUrl?: string
  downloadUrl?: string
  status?: string
  createTime?: string
  updateTime?: string
  config?: {
    fields?: OutputPreviewField[]
    [key: string]: unknown
  }
  [key: string]: unknown
}

export type OutputPreviewResponse = ApiResponse<OutputPreviewField[]>

export type OutputTaskResponse = ApiResponse<OutputTaskHistoryRecord | null>

export type OutputTaskHistoryResponse = ApiResponse<ApiPageResult<OutputTaskHistoryRecord> | OutputTaskHistoryRecord[]>

export type OutputTaskHistoryDetailResponse = ApiResponse<OutputTaskHistoryRecord>
