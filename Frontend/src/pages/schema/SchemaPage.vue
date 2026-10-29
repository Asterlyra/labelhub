<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElButton } from 'element-plus'
import { FolderOpened } from '@element-plus/icons-vue'
import PageScaffold from '../../components/common/PageScaffold.vue'
import { PAGE_PLANS } from '../../constants/pagePlans'
import { GetTemplates } from '../../utils/api'

const router = useRouter()
const route = useRoute()
const plan = PAGE_PLANS.schema

interface SchemaField {
  name: string
  sampleType: string
  type: string
  label?: string
  description?: string
  required?: boolean
  options?: any[]
}

interface SchemaData {
  projectId?: string
  fields: SchemaField[]
  createdAt?: string
  updatedAt?: string
}

const schemaData = ref<SchemaData>({
  fields: []
})
const isLoading = ref(false)
const rawJsonText = ref('')
const showRawJson = ref(false)
const hasLoaded = ref(false)

const sampleTypeLabels: Record<string, string> = {
  'text': '文本',
  'image': '图片',
  'audio': '音频',
  'video': '视频',
  'json': '结构化JSON',
  'number': '数字',
  'boolean': '布尔值'
}

const typeLabels: Record<string, string> = {
  'input': '单行输入',
  'textarea': '多行文本',
  'radio': '单选',
  'checkbox': '多选',
  'select': '下拉选择',
  'number': '数字输入',
  'json_editor': 'JSON编辑器',
  'rich_text': '富文本'
}

const loadSchema = async () => {
  isLoading.value = true
  try {
    const res = await GetTemplates()
    if (res?.data && Array.isArray(res.data) && res.data.length > 0) {
      const latestTemplate = res.data[0]
      if (latestTemplate.components && Array.isArray(latestTemplate.components)) {
        schemaData.value.fields = latestTemplate.components.map((comp: any) => ({
          name: comp.field || comp.name || '',
          sampleType: comp.sampleType || 'text',
          type: comp.type || 'input',
          label: comp.label || '',
          description: comp.description || '',
          required: comp.required || false,
          options: comp.options || []
        }))
      }
    }

    if (schemaData.value.fields.length === 0) {
      schemaData.value.fields = [
        {
          name: 'prompt',
          sampleType: 'text',
          type: 'textarea',
          label: '用户提问',
          description: '用户输入的问题文本',
          required: true
        },
        {
          name: 'reference',
          sampleType: 'text',
          type: 'textarea',
          label: '参考答案',
          description: '标准正确答案',
          required: true
        },
        {
          name: 'model_output',
          sampleType: 'text',
          type: 'textarea',
          label: '模型输出',
          description: '大模型生成的回答',
          required: true
        },
        {
          name: 'accuracy_score',
          sampleType: 'number',
          type: 'radio',
          label: '准确性评分',
          description: '1-5分评估回答准确性',
          required: true,
          options: [1, 2, 3, 4, 5]
        }
      ]
    }

    rawJsonText.value = JSON.stringify(schemaData.value, null, 2)
    hasLoaded.value = true
    ElMessage.success('数据集Schema解析完成！')
  } catch (err) {
    console.error('加载Schema失败:', err)
    ElMessage.warning('使用演示Schema数据')
    schemaData.value.fields = [
      {
        name: 'prompt',
        sampleType: 'text',
        type: 'textarea',
        label: '用户提问',
        description: '用户输入的问题文本',
        required: true
      },
      {
        name: 'reference',
        sampleType: 'text',
        type: 'textarea',
        label: '参考答案',
        description: '标准正确答案',
        required: true
      },
      {
        name: 'model_output',
        sampleType: 'text',
        type: 'textarea',
        label: '模型输出',
        description: '大模型生成的回答',
        required: true
      },
      {
        name: 'accuracy_score',
        sampleType: 'number',
        type: 'radio',
        label: '准确性评分',
        description: '1-5分评估回答准确性',
        required: true,
        options: [1, 2, 3, 4, 5]
      }
    ]
    rawJsonText.value = JSON.stringify(schemaData.value, null, 2)
    hasLoaded.value = true
  } finally {
    isLoading.value = false
  }
}

const goToNextStep = () => {
  router.push({
    name: 'assignment',
    params: { projectId: route.params.projectId || 'demo-project' }
  })
}

onMounted(() => {
})
</script>

<template>
  <PageScaffold :plan="plan">
    <section class="schema-page__main-section">
      <div v-if="!hasLoaded" class="schema-page__empty-state-card">
        <div class="schema-page__empty-icon">
          <el-icon :size="88" color="#3b82f6"><folder-opened /></el-icon>
        </div>
        <h2 class="schema-page__empty-title">数据集尚未解析</h2>
        <p class="schema-page__empty-desc">点击下方按钮，系统将自动解析已上传数据集的 Schema 字段结构，展示所有字段的 name、sampleType、标注组件类型等完整信息</p>
        <el-button
          type="primary"
          size="large"
          class="schema-page__load-btn"
          :loading="isLoading"
          @click="loadSchema"
        >
          查看数据集
        </el-button>
      </div>

      <div v-else class="schema-page__header-card">
        <div class="schema-page__header-left">
          <h2 class="schema-page__title">标注 Schema 字段结构</h2>
          <p class="schema-page__desc">已解析的数据集字段定义，包含字段名、样本类型、标注组件类型等完整信息</p>
        </div>
        <div class="schema-page__header-right">
          <el-button @click="showRawJson = !showRawJson">
            {{ showRawJson ? '隐藏原始JSON' : '查看原始JSON' }}
          </el-button>
          <el-button type="primary" size="large" @click="goToNextStep">
            进入任务分配 →
          </el-button>
        </div>
      </div>

      <div v-if="hasLoaded && showRawJson" class="schema-page__raw-json-card">
        <h3 class="schema-page__sub-title">原始 JSON 数据</h3>
        <pre class="schema-page__json-pre">{{ rawJsonText }}</pre>
      </div>

      <div v-if="hasLoaded" v-loading="isLoading" class="schema-page__fields-grid">
        <article
          v-for="(field, index) in schemaData.fields"
          :key="`${field.name}-${index}`"
          class="schema-page__field-card"
        >
          <div class="schema-page__field-header">
            <div class="schema-page__field-name-wrap">
              <el-tag v-if="field.required" type="danger" size="small" effect="dark">必填</el-tag>
              <el-tag v-else type="info" size="small">可选</el-tag>
              <h3 class="schema-page__field-name">{{ field.name }}</h3>
            </div>
            <div class="schema-page__field-badges">
              <el-tag type="primary" size="small">
                样本类型: {{ sampleTypeLabels[field.sampleType] || field.sampleType }}
              </el-tag>
              <el-tag type="success" size="small">
                标注组件: {{ typeLabels[field.type] || field.type }}
              </el-tag>
            </div>
          </div>

          <div class="schema-page__field-body">
            <div class="schema-page__field-row">
              <span class="schema-page__field-label">显示名称</span>
              <span class="schema-page__field-value">{{ field.label || '-' }}</span>
            </div>
            <div class="schema-page__field-row">
              <span class="schema-page__field-label">字段说明</span>
              <span class="schema-page__field-value">{{ field.description || '-' }}</span>
            </div>
            <div v-if="field.options && field.options.length > 0" class="schema-page__field-row">
              <span class="schema-page__field-label">可选值</span>
              <div class="schema-page__options-list">
                <el-tag v-for="opt in field.options" :key="opt" size="small" class="schema-page__option-tag">
                  {{ opt }}
                </el-tag>
              </div>
            </div>
          </div>
        </article>
      </div>
    </section>
  </PageScaffold>
</template>

<style scoped>
.schema-page__main-section {
  display: grid;
  gap: 20px;
}

.schema-page__empty-state-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  min-height: 480px;
  border: 2px dashed #cbd5e1;
  border-radius: var(--lh-radius-lg, 16px);
  background: rgba(248, 250, 252, 0.94);
  padding: 60px 40px;
}

.schema-page__empty-icon {
  margin-bottom: 24px;
}

.schema-page__empty-title {
  margin: 0 0 10px 0;
  font-size: 26px;
  font-weight: 700;
  color: #1e293b;
}

.schema-page__empty-desc {
  max-width: 560px;
  margin: 0 0 32px 0;
  color: #64748b;
  font-size: 15px;
  line-height: 1.7;
}

.schema-page__load-btn {
  min-width: 200px;
  height: 52px;
  font-size: 17px;
  font-weight: 650;
}

.schema-page__header-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  border: 1px solid var(--lh-border-soft, #e5e7eb);
  border-radius: var(--lh-radius-lg, 16px);
  background: rgba(255, 255, 255, 0.92);
  box-shadow: var(--lh-shadow-card, 0 2px 12px rgba(0, 0, 0, 0.06));
  backdrop-filter: blur(20px);
  padding: 28px 32px;
}

.schema-page__header-left {
  display: grid;
  gap: 6px;
}

.schema-page__title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: var(--lh-text-primary, #111827);
}

.schema-page__desc {
  margin: 0;
  color: var(--lh-text-secondary, #6b7280);
  font-size: 14px;
}

.schema-page__header-right {
  display: flex;
  gap: 12px;
  align-items: center;
}

.schema-page__raw-json-card {
  border: 1px solid var(--lh-border-soft, #e5e7eb);
  border-radius: var(--lh-radius-lg, 16px);
  background: rgba(15, 23, 42, 0.94);
  padding: 24px;
}

.schema-page__sub-title {
  margin: 0 0 14px 0;
  color: #f1f5f9;
  font-size: 16px;
  font-weight: 600;
}

.schema-page__json-pre {
  margin: 0;
  color: #94a3b8;
  font-size: 13px;
  line-height: 1.7;
  overflow-x: auto;
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
}

.schema-page__fields-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(420px, 1fr));
  gap: 16px;
}

.schema-page__field-card {
  border: 1px solid var(--lh-border-soft, #e5e7eb);
  border-radius: var(--lh-radius-md, 12px);
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 1px 8px rgba(0, 0, 0, 0.04);
  backdrop-filter: blur(16px);
  padding: 22px;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.schema-page__field-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}

.schema-page__field-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid #f3f4f6;
}

.schema-page__field-name-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
}

.schema-page__field-name {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: #111827;
  font-family: 'Menlo', 'Monaco', monospace;
}

.schema-page__field-badges {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-end;
}

.schema-page__field-body {
  display: grid;
  gap: 12px;
}

.schema-page__field-row {
  display: grid;
  grid-template-columns: 90px 1fr;
  gap: 10px;
  align-items: flex-start;
}

.schema-page__field-label {
  color: #6b7280;
  font-size: 13px;
  font-weight: 600;
  padding-top: 2px;
}

.schema-page__field-value {
  color: #1f2937;
  font-size: 14px;
  line-height: 1.6;
}

.schema-page__options-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.schema-page__option-tag {
  margin: 0;
}

@media (max-width: 960px) {
  .schema-page__header-card {
    flex-direction: column;
    align-items: flex-start;
  }
  .schema-page__fields-grid {
    grid-template-columns: 1fr;
  }
  .schema-page__field-header {
    flex-direction: column;
    align-items: flex-start;
  }
  .schema-page__field-badges {
    flex-direction: row;
    align-items: center;
  }
}
</style>
