import axios from 'axios'
import type {
  AiAuditDetailResponse,
  AiAuditListResponse,
  AiSuggestionResponse,
  AnnotationMutationResponse,
  ApiFileField,
  ApiId,
  ApiQueryParams,
  CreateOutputTaskPayload,
  CreateTaskPayload,
  DatasetDetailResponse,
  DatasetListResponse,
  DatasetMutationResponse,
  DatasetPreviewResponse,
  GetDatasetParams,
  GetTemplatesParams,
  LabelersResponse,
  LoginResponse,
  OutputPreviewResponse,
  OutputTaskHistoryDetailResponse,
  OutputTaskHistoryResponse,
  OutputTaskResponse,
  ReviewActionPayload,
  ReviewActionResponse,
  ReviewersResponse,
  ReviewTaskDetailResponse,
  ReviewTasksResponse,
  ReviewWorkspaceResponse,
  TaskDetailResponse,
  TaskItemsDetailParams,
  TaskItemsDetailResponse,
  TaskListParams,
  TaskListResponse,
  TaskMarketplaceTagsResponse,
  TaskMutationResponse,
  TaskTimelineResponse,
  TemplateDetailResponse,
  TemplateListResponse,
  TemplateMutationResponse,
  TemplatePayload,
  UpdateProfilePayload,
  UpdateProfileResponse,
  UpdateTaskPayload,
  UserInfoResponse,
  UserRegisterPayload,
  UserRegisterResponse,
  UserRolesResponse,
  WorkbenchInitResponse,
  WorkbenchItemDetailResponse,
} from '../types/api_types'

const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'

export const API_BASE_URL = baseURL

const instance = axios.create({
  baseURL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

const multipartInstance = axios.create({
  baseURL,
  timeout: 300000,
})

export const getApiErrorMessage = (error: unknown, fallback = '请求失败，请稍后重试。') => {
  if (axios.isAxiosError(error) && error.response) {
    console.error('服务器错误:', error.response.data)
    return error.response.data?.message || error.response.data?.msg || fallback
  }

  if (axios.isAxiosError(error) && error.request) {
    console.error('未收到服务器响应:', error.request)
    return '未收到服务器响应。'
  }

  if (error instanceof Error) {
    console.error('请求错误:', error.message)
    return error.message || fallback
  }

  console.error('请求错误:', error)
  return fallback
}

//用户登录
export const PostUserLogin = async (username: string, password: string): Promise<LoginResponse> => {
  try {
    const response = await instance.post('/auth/login', {
      username,
      password,
    })
    console.log(response.data)
    return response.data
  } catch (error: unknown) {
    getApiErrorMessage(error)
    throw error
  }
}

// 用户注册
export const PostUserRegister = async (data: UserRegisterPayload): Promise<UserRegisterResponse> => {
  try {
    const response = await instance.post('/auth/register', data)

    console.log(response.data)
    return response.data
  } catch (error: unknown) {
    getApiErrorMessage(error)
    throw error
  }
}


//鉴权
export const GetUserInfo = async (satoken: string): Promise<UserInfoResponse> => {
  try {
    const response = await instance.get('/user/info', {
      headers: {
        satoken: 'LabelHub '+ satoken,
      },
    })

    return response.data
  } catch (error: unknown) {
    getApiErrorMessage(error)
    throw error
  }
}

//鉴权优化接口
export const GetUserInfoPlus = async (satoken: string): Promise<UserRolesResponse> => {
  try {
    const response = await instance.get('/user/roles', {
      headers: {
        satoken: 'LabelHub '+ satoken,
      },
    })

    return response.data
  } catch (error: unknown) {
    getApiErrorMessage(error)
    throw error
  }
}



// --------------------------------------------------------------------------------------------------------
//创建模板
export const PostDesignTemplate = async (
  template: TemplatePayload,
  fileList: ApiFileField[]
): Promise<TemplateMutationResponse> => {
  const satoken = localStorage.getItem('satoken')

  const formData = new FormData()

  // template �?JSON 请求�?
  formData.append('template', JSON.stringify(template))

  // 多个文件：files �?fileFields 一组一组往后加
  fileList.forEach((item) => {
    formData.append('files', item.file, item.file.name)
    formData.append('fileFields', item.field)
  })

  try {
    const response = await multipartInstance.post('/templates', formData, {
      headers: {
        satoken: 'LabelHub ' + satoken,
      },
    })

    console.log(response)
    return response.data
  } catch (error: unknown) {
    getApiErrorMessage(error)
    throw error
  }
}


//获取模板
export const GetTemplates = async (params: GetTemplatesParams = {}): Promise<TemplateListResponse> => {
  const satoken = localStorage.getItem('satoken')

  try {
    const response = await instance.get('/templates', {
      params,
      headers: {
        satoken: 'LabelHub ' + satoken,
      },
    })

    console.log(response.data)
    return response.data
  } catch (error: unknown) {
    getApiErrorMessage(error)
    throw error
  }
}



// 查询我的模板列表
export const GetMyTemplates = async (
  params: GetTemplatesParams = {}
): Promise<TemplateListResponse> => {
  const satoken = localStorage.getItem('satoken')

  try {
    const response = await instance.get('/templates/my', {
      params,
      headers: {
        satoken: 'LabelHub ' + satoken,
      },
    })

    console.log(response.data)
    return response.data
  } catch (error: unknown) {
    getApiErrorMessage(error)
    throw error
  }
}


//查看模板详情
export const GetTemplateDetail = async (id: ApiId): Promise<TemplateDetailResponse> => {
  const satoken = localStorage.getItem('satoken')

  try {
    const response = await instance.get(`/templates/${id}`, {
      headers: {
        satoken: 'LabelHub ' + satoken,
      }
    })

      console.log(response.data)
    return response.data
  } catch (error: unknown) {
    getApiErrorMessage(error)
    throw error
  }
}

//删除模板
export const DeleteTemplate = async (id: ApiId): Promise<TemplateMutationResponse> => {
  const satoken = localStorage.getItem('satoken')

  try {
    const response = await instance.delete(`/templates/${id}`, {
      headers: {
        satoken: 'LabelHub '+ satoken
      }
    })

    console.log(response.data)
    return response.data
  } catch (error: unknown) {
    getApiErrorMessage(error)
    throw error
  }
}

// 更新模板
export const UpdateDesignTemplate = async (
  id: ApiId,
  template: TemplatePayload,
  fileList: ApiFileField[] = []
): Promise<TemplateMutationResponse> => {
  const satoken = localStorage.getItem('satoken')

  const formData = new FormData()

  // template �?JSON 请求体，格式和创建模板保持一�?
  formData.append('template', JSON.stringify(template))

  // 多个文件：files �?fileFields 一组一组往后加
  fileList.forEach((item) => {
    formData.append('files', item.file, item.file.name)
    formData.append('fileFields', item.field)
  })

  try {
    const response = await instance.put(`/templates/${id}`, formData, {
      headers: {
        satoken: 'LabelHub ' + satoken,
      },
    })

    console.log(response)
    return response.data
  } catch (error: unknown) {
    getApiErrorMessage(error)
    throw error
  }
}



// --------------------------------------------------------------------------------------------------------
// 上传数据�?
export const PostDataset = async (
  name: string,
  description: string,
  file: File,
  schema?: string
): Promise<DatasetMutationResponse> => {
  const satoken = localStorage.getItem('satoken')

  const formData = new FormData()

  formData.append('name', name)
  formData.append('description', description)
  formData.append('file', file, file.name)

  if (schema) {
    formData.append('schema', schema)
  }

  try {
    const response = await multipartInstance.post('/datasets/upload', formData, {
      headers: {
        satoken: 'LabelHub ' + satoken,
      },
    })

    console.log(response.data)
    return response.data
  } catch (error: unknown) {
    getApiErrorMessage(error)
    throw error
  }
}

// 预览数据集字�?
export const PreviewDataset = async (file: File): Promise<DatasetPreviewResponse> => {
  const satoken = localStorage.getItem('satoken')

  const formData = new FormData()
  formData.append('file', file, file.name)

  try {
    const response = await multipartInstance.post('/datasets/preview-fields', formData, {
      headers: {
        satoken: 'LabelHub ' + satoken,
      },
    })

    return response.data
  } catch (error: unknown) {
    getApiErrorMessage(error)
    throw error
  }
}

// 查询数据集列�?
export const GetDataset = async (params: GetDatasetParams = {}): Promise<DatasetListResponse> => {
  const satoken = localStorage.getItem('satoken')

  const response = await instance.get('/datasets', {
    params,
    headers: {
      satoken: 'LabelHub ' + satoken,
    },
  })

    console.log(response.data)

  return response.data
}

// 查询我的数据集列�?
export const GetMyDataset = async (params: GetDatasetParams = {}): Promise<DatasetListResponse> => {
  const satoken = localStorage.getItem('satoken')

  const response = await instance.get('/datasets/my', {
    params,
    headers: {
      satoken: 'LabelHub ' + satoken,
    },
  })

  return response.data
}

// 删除数据�?
export const DeleteDataset = async (id: ApiId): Promise<DatasetMutationResponse> => {
  const satoken = localStorage.getItem('satoken')

  try {
    const response = await instance.delete(`/datasets/${id}`, {
      headers: {
        satoken: 'LabelHub ' + satoken,
      },
    })

    return response.data
  } catch (error: unknown) {
    getApiErrorMessage(error)
    throw error
  }
}

// 查询数据集详�?
export const GetDatasetDetail = async (id: ApiId): Promise<DatasetDetailResponse> => {
  const satoken = localStorage.getItem('satoken')

  try {
    const response = await instance.get(`/datasets/${id}`, {
      headers: {
        satoken: 'LabelHub ' + satoken,
      },
    })

      console.log(response)
    return response.data
  } catch (error: unknown) {
    getApiErrorMessage(error)
    throw error
  }
}


// --------------------------------------------------------------------------------------------------------
// 分页查询任务列表
export const GetTasks = async (params: TaskListParams = {}): Promise<TaskListResponse> => {
  const satoken = localStorage.getItem('satoken')

  try {
    const response = await instance.get('/tasks', {
      params,
      headers: {
        satoken: 'LabelHub ' + satoken,
      },
    })

    console.log(response.data)
    return response.data
  } catch (error: unknown) {
    getApiErrorMessage(error)
    throw error
  }
}

// 查询我的任务数据�?
export const GetMyTasks = async (params: TaskListParams = {}): Promise<TaskListResponse> => {
  const satoken = localStorage.getItem('satoken')

  const response = await instance.get('/tasks/my', {
    params,
    headers: {
      satoken: 'LabelHub ' + satoken,
    },
  })
  console.log(response.data)
  return response.data
}

// 查询任务广场列表
export const GetTaskMarketplace = async (params: TaskListParams = {}): Promise<TaskListResponse> => {
  const satoken = localStorage.getItem('satoken')

  const response = await instance.get('/tasks/marketplace', {
    params,
    headers: {
      satoken: 'LabelHub ' + satoken,
    },
  })
  console.log(response.data)
  return response.data
}

// 查询任务广场标签
export const GetTaskMarketplaceTags = async (): Promise<TaskMarketplaceTagsResponse> => {
  const satoken = localStorage.getItem('satoken')

  const response = await instance.get('/tasks/marketplace/tags', {
    headers: {
      satoken: 'LabelHub ' + satoken,
    },
  })

  return response.data
}

// 查询任务广场详情
export const GetTaskMarketplaceDetail = async (id: ApiId): Promise<TaskDetailResponse> => {
  const satoken = localStorage.getItem('satoken')

  const response = await instance.get(`/tasks/${id}`, {
    headers: {
      satoken: 'LabelHub ' + satoken,
    },
  })
  console.log(response.data)
  return response.data
}

// 标注员领取任务数据项
export const ClaimTask = async (id: ApiId): Promise<TaskMutationResponse> => {
  const satoken = localStorage.getItem('satoken')

  const response = await instance.post(`/tasks/${id}/claim`, null, {
    headers: {
      satoken: 'LabelHub ' + satoken,
    },
  })

  return response.data
}

// 查询任务详情
export const GetTaskDetail = async (id: ApiId): Promise<TaskDetailResponse> => {
  const satoken = localStorage.getItem('satoken')

  const response = await instance.get(`/tasks/${id}`, {
    headers: {
      satoken: 'LabelHub ' + satoken,
    },
  })

  console.log(response.data)
  return response.data
}

// 删除任务
export const DeleteTask = async (id: ApiId): Promise<TaskMutationResponse> => {
  const satoken = localStorage.getItem('satoken')

  const response = await instance.delete(`/tasks/${id}`, {
    headers: {
      satoken: 'LabelHub ' + satoken,
    },
  })

  return response.data
}

// 创建任务草稿
export const CreateTask = async (data: CreateTaskPayload): Promise<TaskMutationResponse> => {
  const satoken = localStorage.getItem('satoken')

  try {
    const response = await instance.post('/tasks', data, {
      headers: {
        satoken: 'LabelHub ' + satoken,
      },
    })

    console.log(response.data)
    return response.data
  } catch (error: unknown) {
    getApiErrorMessage(error)
    throw error
  }
}

// 发布任务
export const PostTask = async (id: ApiId): Promise<TaskMutationResponse> => {
  const satoken = localStorage.getItem('satoken')

  try {
    const response = await instance.post(`/tasks/${id}/publish`, null, {
      headers: {
        satoken: 'LabelHub ' + satoken,
      },
    })

    return response.data
  } catch (error: unknown) {
    getApiErrorMessage(error)
    throw error
  }
}

// 暂停任务
export const PauseTask = async (id: ApiId): Promise<TaskMutationResponse> => {
  const satoken = localStorage.getItem('satoken')

  const response = await instance.post(`/tasks/${id}/pause`, null, {
    headers: {
      satoken: 'LabelHub ' + satoken,
    },
  })

  return response.data
}

// 恢复任务
export const ResumeTask = async (id: ApiId): Promise<TaskMutationResponse> => {
  const satoken = localStorage.getItem('satoken')

  const response = await instance.post(`/tasks/${id}/resume`, null, {
    headers: {
      satoken: 'LabelHub ' + satoken,
    },
  })

  return response.data
}

// 结束任务
export const EndTask = async (id: ApiId): Promise<TaskMutationResponse> => {
  const satoken = localStorage.getItem('satoken')

  const response = await instance.post(`/tasks/${id}/end`, null, {
    headers: {
      satoken: 'LabelHub ' + satoken,
    },
  })

  return response.data
}

// 更新任务详情
export const UpdateTask = async (
  id: ApiId,
  data: UpdateTaskPayload
): Promise<TaskMutationResponse> => {
  const satoken = localStorage.getItem('satoken')

  const response = await instance.put(`/tasks/${id}`, data, {
    headers: {
      satoken: 'LabelHub ' + satoken,
    },
  })

  return response.data
}

// 分页查询任务数据�?
export const GetTaskItemsDetail = async (
  id: ApiId,
  params: TaskItemsDetailParams = {}
): Promise<TaskItemsDetailResponse> => {
  const satoken = localStorage.getItem('satoken')

  try {
    const response = await instance.get(`/tasks/${id}/items`, {
      params,
      headers: {
        satoken: 'LabelHub ' + satoken,
      },
    })

    console.log(response.data)
    return response.data
  } catch (error: unknown) {
    getApiErrorMessage(error)
    throw error
  }
}







//---------------------------------------------------------------------------------------------------------------------------------
// 查询审核员列�?
export const GetReviewers = async (): Promise<ReviewersResponse> => {
  const satoken = localStorage.getItem('satoken')

  try {
    const response = await instance.get('/user/reviewers', {
      headers: {
        satoken: 'LabelHub ' + satoken,
      },
    })

    console.log(response.data)
    return response.data
  } catch (error: unknown) {
    getApiErrorMessage(error)
    throw error
  }
}

// 查询标注员列�?
export const GetLabelers = async (params: ApiQueryParams = {}): Promise<LabelersResponse> => {
  const satoken = localStorage.getItem('satoken')

  const response = await instance.get('/user/labelers', {
    params,
    headers: {
      satoken: 'LabelHub ' + satoken,
    },
  })

  return response.data
}

// 修改当前用户个人资料
export const UpdateUserProfile = async (payload: UpdateProfilePayload): Promise<UpdateProfileResponse> => {
  const satoken = localStorage.getItem('satoken')

  const response = await instance.put('/user/profile', payload, {
    headers: {
      satoken: 'LabelHub ' + satoken,
    },
  })

  return response.data
}

//---------------------------------------------------------------------------------------------------------------------------------
//标注员功�?

// 查询任务广场列表
export const GetTaskMarket = async (params: ApiQueryParams = {}): Promise<TaskListResponse> => {
  const satoken = localStorage.getItem('satoken')

  const response = await instance.get('/tasks/marketplace', {
    params,
    headers: {
      satoken: 'LabelHub ' + satoken,
    },
  })

  console.log(response.data)
  return response.data
}

// 查询任务广场标签
export const GetTaskMarketTags = async (): Promise<TaskMarketplaceTagsResponse> => {
  const satoken = localStorage.getItem('satoken')

  const response = await instance.get('/tasks/marketplace/tags', {
    headers: {
      satoken: 'LabelHub ' + satoken,
    },
  })

  console.log(response.data)
  return response.data
}

// 查询任务广场详情
export const GetTaskMarketDetail = async (id: ApiId): Promise<TaskDetailResponse> => {
  const satoken = localStorage.getItem('satoken')

  const response = await instance.get(`/tasks/marketplace/${id}`, {
    headers: {
      satoken: 'LabelHub ' + satoken,
    },
  })

  return response.data
}

// 领取任务数据�?
export const PostMarketTask = async (id: ApiId): Promise<TaskMutationResponse> => {
  const satoken = localStorage.getItem('satoken')

  try {
    const response = await instance.post(`/tasks/${id}/claim`, null, {
      headers: {
        satoken: 'LabelHub ' + satoken,
      },
    })

    return response.data
  } catch (error: unknown) {
    getApiErrorMessage(error)
    throw error
  }
}

// 查询标注员已领取的任务列�?
export const GetLabelerTasks = async (params: ApiQueryParams = {}): Promise<TaskListResponse> => {
  const satoken = localStorage.getItem('satoken')

  const response = await instance.get('/annotation/tasks', {
    params,
    headers: {
      satoken: 'LabelHub ' + satoken,
    },
  })
  console.log(response.data)
  return response.data
}

// 做题区域接口---------------------------------------------------------------------------------------------------
//UNASSIGNED  未分�?/ 未领�?
//CLAIMED     已领�?/ 待做
//SUBMITTED   已提�?/ 等待审核
//REJECTED    已驳�?/ 审核不通过，需要返�?
//REVIEWED    已审�?/ 审核通过，流程完�?

// 查询任务数据项列�?中间做题区域 以及左边显示区域
// 初始化标注工作台
export const InitWorkBench = async (id: ApiId): Promise<WorkbenchInitResponse> => {
  const satoken = localStorage.getItem('satoken')

  const response = await instance.get(`/annotation/workspace/${id}`, {
    headers: {
      satoken: 'LabelHub ' + satoken,
    },
  })

  console.log(response.data)
  return response.data
}

// 查询单题详情
export const GetWorkBenchTaskItemExcel = async (
  taskItemId: ApiId
): Promise<WorkbenchItemDetailResponse> => {
  const satoken = localStorage.getItem('satoken')

  const response = await instance.get(`/annotation/item/${taskItemId}`, {
    headers: {
      satoken: 'LabelHub ' + satoken,
    },
  })

  return response.data
}

// 保存标注草稿
export const SaveAnnotationDraft = async (
  taskItemId: ApiId,
  annotationData: Record<string, unknown>,
  fileList: ApiFileField[] = [],
): Promise<AnnotationMutationResponse> => {
  const satoken = localStorage.getItem('satoken')

  if (fileList.length) {
    const formData = new FormData()
    formData.append('annotation', JSON.stringify({ annotationData }))

    fileList.forEach((item) => {
      formData.append('files', item.file, item.file.name)
      formData.append('fileFields', item.field)
    })

    const response = await multipartInstance.post(
      `/annotation/item/${taskItemId}/draft`,
      formData,
      {
        headers: {
          satoken: 'LabelHub ' + satoken,
        },
      },
    )

    return response.data
  }

  const response = await instance.post(
    `/annotation/item/${taskItemId}/draft`,
    { annotationData },
    {
      headers: {
        satoken: 'LabelHub ' + satoken,
      },
    },
  )

  console.log(response.data)
  return response.data
}

// 提交标注结果
export const SubmitAnnotationItem = async (
  taskItemId: ApiId,
  annotationData: Record<string, unknown>,
  fileList: ApiFileField[] = [],
): Promise<AnnotationMutationResponse> => {
  const satoken = localStorage.getItem('satoken')

  if (fileList.length) {
    const formData = new FormData()
    formData.append('annotation', JSON.stringify({ annotationData }))

    fileList.forEach((item) => {
      formData.append('files', item.file, item.file.name)
      formData.append('fileFields', item.field)
    })

    const response = await multipartInstance.post(
      `/annotation/item/${taskItemId}/submit`,
      formData,
      {
        headers: {
          satoken: 'LabelHub ' + satoken,
        },
      },
    )

  console.log(response.data)
    return response.data
  }

  const response = await instance.post(
    `/annotation/item/${taskItemId}/submit`,
    { annotationData },
    {
      headers: {
        satoken: 'LabelHub ' + satoken,
      },
    },
  )

  return response.data
}



// 审核员界面接�?--------------------------------------------------------------------------------------------------
// 查询审核任务列表
export const GetReviewTasks = async (params: ApiQueryParams = {}): Promise<ReviewTasksResponse> => {
  const satoken = localStorage.getItem('satoken')

  try {
    const response = await instance.get('/reviews/tasks', {
      params,
      headers: {
        satoken: 'LabelHub ' + satoken,
      },
    })

      console.log(response.data)
    return response.data
  } catch (error: unknown) {
    getApiErrorMessage(error)
    throw error
  }
}

// 查询审核数据项详�?
export const GetReviewTaskDetail = async (
  taskItemId: ApiId
): Promise<ReviewTaskDetailResponse> => {
  const satoken = localStorage.getItem('satoken')

  try {
    const response = await instance.get(`/reviews/items/${taskItemId}`, {
      headers: {
        satoken: 'LabelHub ' + satoken,
      },
    })

    console.log(response.data)
    return response.data
  } catch (error: unknown) {
    getApiErrorMessage(error)
    throw error
  }
}

// 进入审核任务工作�?
export const ReviewTask = async (
  taskId: ApiId,
  params: ApiQueryParams = {}
): Promise<ReviewWorkspaceResponse> => {
  const satoken = localStorage.getItem('satoken')

  try {
    const response = await instance.get(`/reviews/tasks/${taskId}/workspace`, {
      params,
      headers: {
        satoken: 'LabelHub ' + satoken,
      },
    })

    console.log(response.data)
    return response.data
  } catch (error: unknown) {
    getApiErrorMessage(error)
    throw error
  }
}

// 审核通过
export const ReviewApprove = async (taskItemId: ApiId): Promise<ReviewActionResponse> => {
  const satoken = localStorage.getItem('satoken')

  try {
    const response = await instance.post(`/reviews/items/${taskItemId}/approve`, null, {
      headers: {
        satoken: 'LabelHub ' + satoken,
      },
    })

    return response.data
  } catch (error: unknown) {
    getApiErrorMessage(error)
    throw error
  }
}

// 审核驳回
export const ReviewReject = async (
  taskItemId: ApiId,
  data: ReviewActionPayload = {}
): Promise<ReviewActionResponse> => {
  const satoken = localStorage.getItem('satoken')

  const response = await instance.post(
    `/reviews/items/${taskItemId}/reject`,
    data,
    {
      headers: {
        satoken: 'LabelHub ' + satoken,
      },
    }
  )

  return response.data
}

// 审核修改
export const ReviewModify = async (
  taskItemId: ApiId,
  data: ReviewActionPayload = {}
): Promise<ReviewActionResponse> => {
  const satoken = localStorage.getItem('satoken')

  try {
    const response = await instance.post(
      `/reviews/items/${taskItemId}/modify`,
      data,
      {
        headers: {
          satoken: 'LabelHub ' + satoken,
        },
      }
    )

    return response.data
  } catch (error: unknown) {
    getApiErrorMessage(error)
    throw error
  }
}

//--------------------------------------------------------------------------------
// 查询不同角色视角下的任务详情

//需要登录。管理员、标注员、审核员共用同一个接口，根据当前用户身份返回不同字段。progress 统一返回任务全局进度�?

//访问规则�?

//PROJECT_ADMIN：可查看任意任务
//LABELER：可查看已发布任务，或自己参与过的任�?
//REVIEWER：仅可查看分配给自己的任�?
export const GetRespectTask = async (id: ApiId): Promise<TaskDetailResponse> => {
  const satoken = localStorage.getItem('satoken')

  try {
    const response = await instance.get(`/tasks/${id}`, {
      headers: {
        satoken: 'LabelHub ' + satoken,
      },
    })

    return response.data
  } catch (error: unknown) {
    getApiErrorMessage(error)
    throw error
  }
}


// 查询任务项流转时间线
export const GetTaskTimeline = async (
  taskItemId: ApiId
): Promise<TaskTimelineResponse> => {
  const satoken = localStorage.getItem('satoken')

  try {
    const response = await instance.get(`/tasks/items/${taskItemId}/timeline`, {
      headers: {
        satoken: 'LabelHub ' + satoken,
      },
    })

    console.log(response.data)
    return response.data
  } catch (error: unknown) {
    getApiErrorMessage(error)
    throw error
  }
}

//--------------------------------------------------------------------------------
//AI 预审功能
// 创建 AI 辅助标注建议
export const CreatAiSuggestion = async (
  taskItemId: ApiId
): Promise<AiSuggestionResponse> => {
  const satoken = localStorage.getItem('satoken')

  try {
    const response = await instance.post(
      `/annotation/item/${taskItemId}/ai-suggestion`,
      null,
      {
        headers: {
          satoken: 'LabelHub ' + satoken,
        },
      }
    )

    console.log(response.data)
    return response.data
  } catch (error: unknown) {
    getApiErrorMessage(error)
    throw error
  }
}

// 查询 AI 辅助标注建议
export const GetAiSuggestion = async (
  taskItemId: ApiId,
  suggestionId: string
): Promise<AiSuggestionResponse> => {
  const satoken = localStorage.getItem('satoken')

  try {
    const response = await instance.get(
      `/annotation/item/${taskItemId}/ai-suggestion/${suggestionId}`,
      {
        headers: {
          satoken: 'LabelHub ' + satoken,
        },
      }
    )

    console.log(response.data)
    return response.data
  } catch (error: unknown) {
    getApiErrorMessage(error)
    throw error
  }
}

//--------------------------------------------------------------------------------
//任务导出功能
// 预览任务导出字段
export const PreviewOutputTask = async (
  taskId: ApiId
): Promise<OutputPreviewResponse> => {
  const satoken = localStorage.getItem('satoken')

  try {
    const response = await instance.get(`/tasks/${taskId}/exports/preview-fields`, {
      headers: {
        satoken: 'LabelHub ' + satoken,
      },
    })

    return response.data
  } catch (error: unknown) {
    getApiErrorMessage(error)
    throw error
  }
}

// 创建任务导出
export const CreateOutputTask = async (
  taskId: ApiId,
  data: CreateOutputTaskPayload = {}
): Promise<OutputTaskResponse> => {
  const satoken = localStorage.getItem('satoken')

  try {
    const response = await instance.post(`/tasks/${taskId}/exports`, data, {
      headers: {
        satoken: 'LabelHub ' + satoken,
      },
    })

    return response.data
  } catch (error: unknown) {
    getApiErrorMessage(error)
    throw error
  }
}

// 查询任务导出历史
export const GetOutputTaskHistory = async (
  taskId: ApiId,
  params: ApiQueryParams = {}
): Promise<OutputTaskHistoryResponse> => {
  const satoken = localStorage.getItem('satoken')

  try {
    const response = await instance.get(`/tasks/${taskId}/exports`, {
      params,
      headers: {
        satoken: 'LabelHub ' + satoken,
      },
    })

    return response.data
  } catch (error: unknown) {
    getApiErrorMessage(error)
    throw error
  }
}

// 查询任务导出历史详情
export const GetOutpuTaskHistoryDetail = async (
  exportId: ApiId
): Promise<OutputTaskHistoryDetailResponse> => {
  const satoken = localStorage.getItem('satoken')

  try {
    const response = await instance.get(`/exports/${exportId}`, {
      headers: {
        satoken: 'LabelHub ' + satoken,
      },
    })

    return response.data
  } catch (error: unknown) {
    getApiErrorMessage(error)
    throw error
  }
}


//--------------------------------------------------------------------------------
//AI 预审审核列表界面 --位于审核员界�?
// 查询任务 AI 预审结果列表
export const GetAISuggestionList = async (
  taskId: ApiId,
  params: ApiQueryParams = {}
): Promise<AiAuditListResponse> => {
  const satoken = localStorage.getItem('satoken')

  try {
    const response = await instance.get(`/reviews/tasks/${taskId}/ai-audit-results`, {
      params,
      headers: {
        satoken: 'LabelHub ' + satoken,
      },
    })

    return response.data
  } catch (error: unknown) {
    getApiErrorMessage(error)
    throw error
  }
}

// 查询 AI 审核结果详情
export const GetAISuggestionListDetail = async (
  taskItemId: ApiId
): Promise<AiAuditDetailResponse> => {
  const satoken = localStorage.getItem('satoken')

  try {
    const response = await instance.get(`/reviews/items/${taskItemId}/ai-audit-result`, {
      headers: {
        satoken: 'LabelHub ' + satoken,
      },
    })

    console.log(response.data)
    return response.data
  } catch (error: unknown) {
    getApiErrorMessage(error)
    throw error
  }
}
