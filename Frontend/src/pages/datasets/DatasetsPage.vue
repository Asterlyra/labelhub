<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageScaffold from '../../components/common/PageScaffold.vue'
import { PAGE_PLANS } from '../../constants/pagePlans'
import { PostDataset, GetDataset } from '../../utils/api'
import type { UploadFile, UploadProps } from 'element-plus'
import loadingAnimation from '../../icon/loading.gif'

const router = useRouter()
const plan = PAGE_PLANS.datasets

const datasetName = ref('')
const datasetDescription = ref('')
const selectedFile = ref<File | null>(null)
const fileList = ref<UploadFile[]>([])
const isUploading = ref(false)

const loadingText = '正在加载中.....'
const loadingTextChars = computed(() => loadingText.split(''))

const handleFileChange: UploadProps['onChange'] = (uploadFile) => {
  if (uploadFile.raw) {
    selectedFile.value = uploadFile.raw
  }
}

const handleFileRemove: UploadProps['onRemove'] = () => {
  selectedFile.value = null
}

const triggerUpload = async () => {
  if (!datasetName.value.trim()) {
    ElMessage.warning('请输入数据集名称')
    return
  }
  if (!selectedFile.value) {
    ElMessage.warning('请选择要上传的数据集文件')
    return
  }

  isUploading.value = true

  try {
    const response = await PostDataset(
      datasetName.value.trim(),
      datasetDescription.value.trim(),
      selectedFile.value
    )

    const responseCode = response.code

    if (Number(responseCode) === 200 || response.success || String(responseCode) === 'OK') {
      isUploading.value = false
      ElMessage.success({
        message: '数据集上传成功',
        duration: 2500,
        showClose: true,
      })

      await router.push({ name: 'owner-datasets' })
    } else {
      ElMessage.error(response.message || '上传失败，请重试')
      isUploading.value = false
    }
  } catch (error: any) {
    console.error('上传数据集失败:', error)
    ElMessage.error(error?.response?.data?.message || error.message || '上传失败，请稍后重试')
    isUploading.value = false
  }
}

let datasets = ref<any[]>([])
const loadDatasets = async () => {
  try {
    const res = await GetDataset()
    if (res?.data) {
      datasets.value = Array.isArray(res.data) ? res.data : []
    }
  } catch {
    datasets.value = []
  }
}

onMounted(() => {
  loadDatasets()
})
</script>

<template>
  <PageScaffold :plan="plan">
    <section v-if="!isUploading" class="dataset-page__upload-section">
      <div class="dataset-page__upload-card">
        <h2 class="dataset-page__upload-title">上传数据集</h2>
        <p class="dataset-page__upload-desc">支持 CSV、JSONL、Excel 等格式的标注数据集文件</p>

        <el-form label-position="top" class="dataset-page__form">
          <el-form-item label="数据集名称" required>
            <el-input
              v-model="datasetName"
              placeholder="请输入数据集名称"
              clearable
              size="large"
            />
          </el-form-item>

          <el-form-item label="数据集描述">
            <el-input
              v-model="datasetDescription"
              type="textarea"
              :rows="3"
              placeholder="请输入数据集描述（可选）"
            />
          </el-form-item>

          <el-form-item label="选择文件" required>
            <el-upload
              v-model:file-list="fileList"
              :auto-upload="false"
              :limit="1"
              :on-change="handleFileChange"
              :on-remove="handleFileRemove"
              drag
              class="dataset-page__uploader"
            >
              <el-icon class="el-icon--upload"><upload-filled /></el-icon>
              <div class="el-upload__text">
                将文件拖到此处，或<em>点击上传</em>
              </div>
              <template #tip>
                <div class="el-upload__tip">
                  单个文件大小不超过 500MB
                </div>
              </template>
            </el-upload>
          </el-form-item>

          <el-form-item>
            <el-button
              type="primary"
              size="large"
              class="dataset-page__submit-btn"
              @click="triggerUpload"
            >
              上传数据集
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <div v-if="datasets.length > 0" class="dataset-page__list-card">
        <h2 class="dataset-page__list-title">已上传数据集</h2>
        <el-table :data="datasets" stripe style="width: 100%">
          <el-table-column prop="name" label="数据集名称" />
          <el-table-column prop="description" label="描述" show-overflow-tooltip />
          <el-table-column prop="createdAt" label="上传时间" width="180" />
          <el-table-column prop="status" label="状态" width="120">
            <template #default>
              <el-tag type="success" size="small">已就绪</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <section v-else class="dataset-page__loading-backdrop" role="presentation">
      <section class="dataset-page__loading-dialog" aria-label="正在加载中">
        <img class="dataset-page__loading-image" :src="loadingAnimation" alt="">
      <p class="dataset-page__loading-text" aria-live="polite">
        <span
          v-for="(char, index) in loadingTextChars"
          :key="`${char}-${index}`"
          :style="{ animationDelay: `${index * 0.08}s` }"
        >
          {{ char }}
        </span>
      </p>
      </section>
    </section>
  </PageScaffold>
</template>

<style scoped>
.dataset-page__upload-section {
  display: grid;
  gap: 24px;
}

.dataset-page__upload-card,
.dataset-page__list-card {
  border: 1px solid var(--lh-border-soft, #e5e7eb);
  border-radius: var(--lh-radius-lg, 16px);
  background: rgba(255, 255, 255, 0.92);
  box-shadow: var(--lh-shadow-card, 0 2px 12px rgba(0, 0, 0, 0.06));
  backdrop-filter: blur(20px);
  padding: 36px;
}

.dataset-page__upload-title,
.dataset-page__list-title {
  margin: 0 0 8px 0;
  font-size: 24px;
  font-weight: 700;
  color: var(--lh-text-primary, #111827);
}

.dataset-page__upload-desc {
  margin: 0 0 28px 0;
  color: var(--lh-text-secondary, #6b7280);
  font-size: 15px;
}

.dataset-page__form {
  max-width: 640px;
}

.dataset-page__uploader {
  width: 100%;
}

.dataset-page__submit-btn {
  min-width: 180px;
  height: 48px;
  font-weight: 650;
}

.dataset-page__loading-backdrop {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: grid;
  place-items: center;
  background: rgba(20, 32, 54, 0.2);
  padding: 24px;
  backdrop-filter: blur(2px);
}

.dataset-page__loading-dialog {
  display: grid;
  width: min(100%, 260px);
  justify-items: center;
  gap: 12px;
  border: 1px solid rgba(255, 255, 255, 0.68);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 24px 70px rgba(20, 32, 54, 0.18);
  padding: 26px 28px 24px;
}

.dataset-page__loading-image {
  display: block;
  width: 86px;
  height: 86px;
  object-fit: contain;
}

.dataset-page__loading-text {
  display: inline-flex;
  min-height: 26px;
  align-items: center;
  justify-content: center;
  gap: 1px;
  margin: 0;
  color: var(--lh-text-primary);
  font-size: 16px;
  font-weight: 850;
  letter-spacing: 0;
  white-space: nowrap;
}

.dataset-page__loading-text span {
  display: inline-block;
  animation: dataset-loading-text-bounce 0.72s cubic-bezier(0.2, 0.82, 0.2, 1) infinite;
}

@keyframes dataset-loading-text-bounce {
  0%,
  72%,
  100% {
    transform: translateY(0);
  }

  34% {
    transform: translateY(-7px);
  }
}
</style>
