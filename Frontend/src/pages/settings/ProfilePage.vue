<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, onMounted, reactive, shallowRef } from 'vue'
import { storeToRefs } from 'pinia'
import RoleTag from '../../components/common/RoleTag.vue'
import { useAuthStore } from '../../stores/auth'
import { GetUserInfo, UpdateUserProfile, getApiErrorMessage } from '../../utils/api'

const authStore = useAuthStore()
const { activeUsername, currentUser, homePath } = storeToRefs(authStore)

const isLoading = shallowRef(false)
const isSaving = shallowRef(false)
const errorMessage = shallowRef('')

const profileForm = reactive({
  nickname: '',
  email: '',
  phone: '',
})

const identityItems = computed(() => [
  { label: '账号', value: activeUsername.value },
  { label: '默认入口', value: homePath.value },
  { label: '用户 ID', value: currentUser.value.id },
])

function readString(value: unknown, fallback = '') {
  if (value === undefined || value === null) {
    return fallback
  }

  const text = String(value).trim()

  return text || fallback
}

function getPayload(response: any) {
  return response?.data ?? response ?? {}
}

function assertApiSuccess(response: any) {
  const code = response?.code

  if (code !== undefined && Number(code) !== 200) {
    throw new Error(response?.message ?? response?.msg ?? '请求失败')
  }
}

function fillProfileForm(userInfo: Record<string, unknown>) {
  profileForm.nickname = readString(userInfo.nickname ?? userInfo.name, currentUser.value.name)
  profileForm.email = readString(userInfo.email)
  profileForm.phone = readString(userInfo.phone)
}

function validateProfileForm() {
  if (profileForm.nickname && (profileForm.nickname.length < 3 || profileForm.nickname.length > 50)) {
    return '昵称长度需要为 3-50 个字符'
  }

  if (profileForm.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(profileForm.email)) {
    return '邮箱格式不正确'
  }

  if (profileForm.phone && !/^1[3-9]\d{9}$/.test(profileForm.phone)) {
    return '手机号格式不正确'
  }

  return ''
}

async function loadProfile() {
  const satoken = localStorage.getItem('satoken')

  fillProfileForm(authStore.userInfo as Record<string, unknown>)

  if (!satoken) {
    return
  }

  isLoading.value = true
  errorMessage.value = ''

  try {
    const response = await GetUserInfo(satoken)
    assertApiSuccess(response)
    const userInfo = getPayload(response)?.data ?? getPayload(response)

    if (userInfo && typeof userInfo === 'object') {
      authStore.userInfo = {
        ...authStore.userInfo,
        ...(userInfo as Record<string, unknown>),
      }
      authStore.persistAuthState()
      fillProfileForm(userInfo as Record<string, unknown>)
    }
  } catch (error: any) {
    errorMessage.value = getApiErrorMessage(error, '个人资料加载失败')
  } finally {
    isLoading.value = false
  }
}

async function handleSaveProfile() {
  const validationMessage = validateProfileForm()

  if (validationMessage) {
    errorMessage.value = validationMessage
    return
  }

  isSaving.value = true
  errorMessage.value = ''

  try {
    const payload = {
      nickname: profileForm.nickname.trim() || undefined,
      email: profileForm.email.trim(),
      phone: profileForm.phone.trim(),
    }
    const response = await UpdateUserProfile(payload)

    assertApiSuccess(response)
    authStore.userInfo = {
      ...authStore.userInfo,
      ...payload,
    }
    authStore.persistAuthState()
    ElMessage.success('个人资料已保存')
  } catch (error: any) {
    errorMessage.value = getApiErrorMessage(error, '个人资料保存失败')
  } finally {
    isSaving.value = false
  }
}

onMounted(() => {
  loadProfile()
})
</script>

<template>
  <section class="profile-page">
    <header class="profile-page__header">
      <div>
        <p class="profile-page__eyebrow">Profile</p>
        <h1>个人资料</h1>
        <p>维护当前账号的基础资料，标注员可在这里确认登录身份和默认工作台入口。</p>
      </div>

      <div class="profile-page__role">
        <RoleTag :role="currentUser.role" />
        <strong>{{ currentUser.name }}</strong>
      </div>
    </header>

    <section class="profile-page__identity" aria-label="当前身份">
      <article v-for="item in identityItems" :key="item.label">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </article>
    </section>

    <p v-if="errorMessage" class="profile-page__error" role="alert">{{ errorMessage }}</p>

    <section class="profile-page__form-panel" aria-label="资料表单">
      <el-skeleton v-if="isLoading" animated :rows="5" />

      <el-form v-else class="profile-page__form" label-position="top" @submit.prevent>
        <el-form-item label="昵称">
          <el-input v-model.trim="profileForm.nickname" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model.trim="profileForm.email" placeholder="name@example.com" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model.trim="profileForm.phone" placeholder="请输入手机号" />
        </el-form-item>

        <div class="profile-page__actions">
          <el-button type="primary" :loading="isSaving" @click="handleSaveProfile">保存资料</el-button>
          <RouterLink class="profile-page__home-link" :to="homePath">返回首页</RouterLink>
        </div>
      </el-form>
    </section>
  </section>
</template>

<style scoped>
.profile-page {
  display: grid;
  gap: 18px;
}

.profile-page__header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 20px;
}

.profile-page__eyebrow {
  margin: 0 0 8px;
  color: var(--lh-accent);
  font-size: 13px;
  font-weight: 800;
}

.profile-page__header h1 {
  margin: 0;
  color: var(--lh-text-primary);
  font-size: clamp(32px, 5vw, 48px);
  line-height: 1.08;
}

.profile-page__header p {
  max-width: 720px;
  margin: 12px 0 0;
  color: var(--lh-text-secondary);
  line-height: 1.7;
}

.profile-page__role,
.profile-page__identity article,
.profile-page__form-panel {
  border: 1px solid var(--lh-border-soft);
  border-radius: var(--lh-radius-md);
  background: rgba(255, 255, 255, 0.84);
  box-shadow: var(--lh-shadow-card);
  backdrop-filter: blur(20px);
}

.profile-page__role {
  display: grid;
  gap: 10px;
  min-width: 220px;
  padding: 18px;
}

.profile-page__role strong {
  color: var(--lh-text-primary);
  font-size: 20px;
}

.profile-page__identity {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.profile-page__identity article {
  display: grid;
  gap: 8px;
  min-height: 98px;
  padding: 18px;
}

.profile-page__identity span {
  color: var(--lh-text-tertiary);
  font-size: 12px;
  font-weight: 750;
}

.profile-page__identity strong {
  overflow: hidden;
  color: var(--lh-text-primary);
  font-size: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-page__error {
  margin: 0;
  border: 1px solid rgba(255, 59, 48, 0.24);
  border-radius: 8px;
  background: rgba(255, 59, 48, 0.08);
  color: #9f1f18;
  font-weight: 700;
  padding: 12px 14px;
}

.profile-page__form-panel {
  padding: clamp(18px, 3vw, 26px);
}

.profile-page__form {
  max-width: 680px;
}

.profile-page__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  padding-top: 6px;
}

.profile-page__home-link {
  display: inline-flex;
  min-height: 32px;
  align-items: center;
  color: var(--lh-accent);
  font-weight: 750;
}

@media (max-width: 820px) {
  .profile-page__header {
    align-items: stretch;
    flex-direction: column;
  }

  .profile-page__identity {
    grid-template-columns: 1fr;
  }
}
</style>
