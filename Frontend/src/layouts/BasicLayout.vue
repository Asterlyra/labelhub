<script setup lang="ts">
import { computed, shallowRef, type Component as VueComponent } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import {
  Collection,
  DataBoard,
  Document,
  Grid,
  List,
  Setting,
  User,
} from '@element-plus/icons-vue'
import RoleTag from '../components/common/RoleTag.vue'
import { NAV_GROUPS } from '../constants/nav'
import { useAuthStore } from '../stores/auth'

interface LayoutBreadcrumbItem {
  label: string
  path?: string
}

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const { activeRole, currentUser, homePath } = storeToRefs(authStore)
const isSidebarCollapsed = shallowRef(false)
const exactMatchNavPaths = new Set(['/workbench', '/workbench/review'])
const isImmersiveRoute = computed(() => route.meta.immersive === true)
const isSidebarCollapseLocked = computed(() => route.name === 'labeling-task' || route.name === 'review-task')
const shouldCollapseSidebar = computed(() => isSidebarCollapseLocked.value || isSidebarCollapsed.value)

const visibleNavGroups = computed(() =>
  NAV_GROUPS.map((group) => ({
    ...group,
    items: group.items.filter((item) => item.roles.includes(activeRole.value)),
  })).filter((group) => group.items.length > 0),
)

const visibleNavItems = computed(() => visibleNavGroups.value.flatMap((group) => group.items))
const navIconMap: Record<string, VueComponent> = {
  '我的首页': DataBoard,
  '任务管理': List,
  '模板搭建': Grid,
  数据集: Collection,
  '任务广场': List,
  '我的任务': Document,
  '审核列表': Document,
  个人资料: User,
  个人设置: Setting,
  团队资料: User,
}
const activeNavItem = computed(() =>
  [...visibleNavItems.value]
    .sort((previous, next) => next.path.length - previous.path.length)
    .find((item) => isNavItemActive(item.path)),
)
const currentPageTitle = computed(() => {
  if (route.name === 'owner-template-designer') {
    return route.params.templateId || route.query.id ? '更新模板' : '创建模板'
  }

  return typeof route.meta.title === 'string' ? route.meta.title : activeNavItem.value?.label ?? '工作台'
})
const breadcrumbItems = computed<LayoutBreadcrumbItem[]>(() => {
  const items: LayoutBreadcrumbItem[] = [
    { label: '当前身份' },
    { label: currentUser.value.name },
  ]

  if (!activeNavItem.value) {
    return [...items, { label: currentPageTitle.value }]
  }

  items.push({
    label: activeNavItem.value.label,
    path: route.path === activeNavItem.value.path ? undefined : activeNavItem.value.path,
  })

  if (currentPageTitle.value !== activeNavItem.value.label) {
    items.push({ label: currentPageTitle.value })
  }

  return items
})

const handleLogout = () => {
  localStorage.removeItem('satoken')
  console.log('satoken 已清除')
  authStore.signOut()
  router.push('/login')
}

function toggleSidebar() {
  if (isSidebarCollapseLocked.value) {
    return
  }

  isSidebarCollapsed.value = !isSidebarCollapsed.value
}

function isNavItemActive(path: string) {
  if (path === '/workbench/tasks' && route.path.startsWith('/workbench/labeling')) {
    return true
  }

  if (path === '/workbench/review/list' && route.name === 'review-task') {
    return true
  }

  if (exactMatchNavPaths.has(path)) {
    return route.path === path
  }

  return route.path === path || route.path.startsWith(`${path}/`)
}

function getNavIcon(label: string) {
  return navIconMap[label] ?? Grid
}
</script>

<template>
  <div
    class="basic-layout"
    :class="{
      'basic-layout--sidebar-collapsed': shouldCollapseSidebar,
      'basic-layout--sidebar-locked': isSidebarCollapseLocked,
      'basic-layout--immersive': isImmersiveRoute,
    }"
  >
    <aside v-if="!isImmersiveRoute" class="basic-layout__sidebar" aria-label="主导航">
      <div class="basic-layout__sidebar-header">
        <RouterLink class="basic-layout__brand" :to="homePath">
          <span class="basic-layout__brand-mark">
            <img src="../icon/niu_pg.gif" alt="" style="width: 80%;">
          </span>
          <span class="basic-layout__brand-copy">
            <img class="basic-layout__brand-logo" src="../icon/logo.png" alt="LabelHub">
          </span>
        </RouterLink>

        <button
          v-if="!isSidebarCollapseLocked"
          class="basic-layout__collapse-button"
          type="button"
          :aria-label="isSidebarCollapsed ? '展开侧栏' : '收起侧栏'"
          aria-controls="basic-layout-navigation"
          :aria-expanded="!isSidebarCollapsed"
          @click="toggleSidebar"
        >
          {{ isSidebarCollapsed ? '>' : '<' }}
        </button>
      </div>

      <nav id="basic-layout-navigation" class="basic-layout__nav">
        <section v-for="group in visibleNavGroups" :key="group.label" class="basic-layout__nav-group">
          <p v-if="group.label" style="">{{ group.label }}</p>
          <RouterLink
            v-for="item in group.items"
            :key="item.path"
            class="basic-layout__nav-link"
            :class="{ 'basic-layout__nav-link--active': isNavItemActive(item.path) }"
            :aria-current="isNavItemActive(item.path) ? 'page' : undefined"
            :title="shouldCollapseSidebar ? item.label : undefined"
            :to="item.path"
          >
            <el-icon class="basic-layout__nav-icon" aria-hidden="true">
              <component :is="getNavIcon(item.label)" />
            </el-icon>
            <span class="basic-layout__nav-text">{{ item.label }}</span>
          </RouterLink>
        </section>
      </nav>

      <footer class="basic-layout__sidebar-footer">
        <button class="basic-layout__logout-button" type="button" @click="handleLogout">
          <span class="basic-layout__logout-icon" aria-hidden="true"></span>
          <span class="basic-layout__logout-text">退出</span>
        </button>
      </footer>
    </aside>

    <div class="basic-layout__main">
      <header v-if="!isImmersiveRoute" class="basic-layout__topbar">
        <div class="basic-layout__identity">
          <el-breadcrumb class="basic-layout__breadcrumb" separator="/">
            <el-breadcrumb-item
              v-for="item in breadcrumbItems"
              :key="item.label"
              :to="item.path ? { path: item.path } : undefined"
            >
              {{ item.label }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="basic-layout__user-actions">
          <RoleTag :role="currentUser.role" />
        </div>
      </header>

      <main class="basic-layout__content">
        <RouterView v-slot="{ Component, route: viewRoute }">
          <Transition name="basic-route" mode="out-in">
            <div :key="viewRoute.fullPath" class="basic-layout__route-view">
              <component :is="Component" />
            </div>
          </Transition>
        </RouterView>
      </main>
    </div>
  </div>
</template>

<style scoped>

  @keyframes gifSoftMove {
  0% {
    transform: scale(1);
    opacity: 0.9;
  }

  50% {
    transform: scale(1.04);
    opacity: 1;
  }

  100% {
    transform: scale(1);
    opacity: 0.9;
  }
}
.basic-layout {
  --lh-sidebar-width: 195px;
  --lh-active-sidebar-width: var(--lh-sidebar-width);
  --lh-sidebar-duration: 0.52s;
  --lh-sidebar-ease: cubic-bezier(0.16, 1, 0.3, 1);
  --lh-sidebar-text-duration: 0.42s;

  display: block;
  min-height: 100vh;
  overflow: visible;
}

.basic-layout--sidebar-collapsed {
  --lh-active-sidebar-width: 104px;
}

.basic-layout--immersive {
  display: block;
}

.basic-layout__sidebar {
  position: fixed;
  z-index: 30;
  inset: 0 auto 0 0;
  display: grid;
  width: var(--lh-active-sidebar-width);
  height: 100vh;
  grid-template-rows: auto minmax(0, 1fr) auto;
  gap: 22px;
  border-right: 1px solid var(--lh-border-soft);
  background: rgba(255, 255, 255, 0.21);
  padding: 18px 14px;
  transition:
    width var(--lh-sidebar-duration) var(--lh-sidebar-ease),
    padding var(--lh-sidebar-duration) var(--lh-sidebar-ease),
    background 0.28s ease;
  backdrop-filter: blur(3px);
}

.basic-layout__sidebar-header {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 34px;
  align-items: center;
  gap: 8px;
  transition: grid-template-columns var(--lh-sidebar-duration) var(--lh-sidebar-ease);
}

.basic-layout__brand {
  display: flex;
  min-height: 52px;
  align-items: center;
  gap: 12px;
  min-width: 0;
  transition:
    gap var(--lh-sidebar-duration) var(--lh-sidebar-ease),
    justify-content var(--lh-sidebar-duration) var(--lh-sidebar-ease);
}

.basic-layout__brand-mark {
  display: grid;
  width: 44px;
  height: 30px;
  border-radius: 100px;
  place-items: center;
  border-radius: 16px;
  background: #c9c9c9;
  color: #ffffff;
  font-weight: 800;
}

.basic-layout__brand-copy {
  display: grid;
  min-width: 0;
  max-width: 160px;
  filter: blur(0);
  opacity: 1;
  transition:
    max-width var(--lh-sidebar-duration) var(--lh-sidebar-ease),
    opacity var(--lh-sidebar-text-duration) ease,
    filter var(--lh-sidebar-text-duration) ease,
    transform var(--lh-sidebar-duration) var(--lh-sidebar-ease);
  will-change: max-width, opacity, filter, transform;
}

.basic-layout__brand-logo {
  display: block;
  width: min(100%, 116px);
  height: auto;
  object-fit: contain;
}

.basic-layout__collapse-button {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border: 1px solid var(--lh-border-soft);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.78);
  color: var(--lh-text-secondary);
  cursor: pointer;
  font-size: 16px;
  font-weight: 850;
  line-height: 1;
  transition:
    border-color 0.24s ease,
    color 0.24s ease,
    transform 0.28s ease;
}

.basic-layout__collapse-button:hover {
  border-color: rgba(0, 113, 227, 0.24);
  color: var(--lh-accent);
  transform: translateY(-1px);
}

.basic-layout__nav,
.basic-layout__nav-group {
  display: grid;
  align-content: start;
}

.basic-layout__nav {
  gap: 18px;
  overflow: auto;
  padding-right: 4px;
  transition:
    gap var(--lh-sidebar-duration) var(--lh-sidebar-ease),
    padding var(--lh-sidebar-duration) var(--lh-sidebar-ease);
}

.basic-layout__nav-group {
  gap: 6px;
}

.basic-layout__nav-group p {
  overflow: hidden;
  max-width: 160px;
  margin: 0 0 5px 8px;
  color: var(--lh-text-tertiary);
  filter: blur(0);
  font-size: 11px;
  font-weight: 700;
  opacity: 1;
  transition:
    max-width var(--lh-sidebar-duration) var(--lh-sidebar-ease),
    opacity var(--lh-sidebar-text-duration) ease,
    filter var(--lh-sidebar-text-duration) ease,
    transform var(--lh-sidebar-duration) var(--lh-sidebar-ease);
  white-space: nowrap;
  will-change: max-width, opacity, filter, transform;
}

.basic-layout__nav-link {
  position: relative;
  display: flex;
  min-height: 34px;
  align-items: center;
  gap: 9px;
  border-radius: 0 8px 8px 0;
  color: var(--lh-text-secondary);
  font-size: 13px;
  font-weight: 700;
  padding: 0 10px 0 18px;
  transition:
    background 0.24s ease,
    color 0.24s ease,
    gap var(--lh-sidebar-duration) var(--lh-sidebar-ease),
    padding var(--lh-sidebar-duration) var(--lh-sidebar-ease),
    transform 0.28s ease;
}

.basic-layout__nav-icon {
  width: 18px;
  height: 18px;
  flex: 0 0 auto;
  color: currentColor;
  font-size: 17px;
  transition: transform 0.24s ease;
}

.basic-layout__nav-text {
  overflow: hidden;
  max-width: 156px;
  filter: blur(0);
  opacity: 1;
  text-overflow: ellipsis;
  transition:
    max-width var(--lh-sidebar-duration) var(--lh-sidebar-ease),
    opacity var(--lh-sidebar-text-duration) ease,
    filter var(--lh-sidebar-text-duration) ease,
    transform var(--lh-sidebar-duration) var(--lh-sidebar-ease);
  white-space: nowrap;
  will-change: max-width, opacity, filter, transform;
}

.basic-layout__nav-link::after {
  position: absolute;
  top: 7px;
  bottom: 7px;
  left: 0;
  width: 3px;
  border-radius: 999px;
  background: transparent;
  content: '';
  opacity: 0;
  transform: scaleY(0.45);
  transition:
    background 0.24s ease,
    opacity 0.28s ease,
    transform var(--lh-sidebar-duration) var(--lh-sidebar-ease);
}

.basic-layout__nav-link:hover,
.basic-layout__nav-link.router-link-active,
.basic-layout__nav-link--active {
  background: rgba(0, 113, 227, 0.08);
  color: var(--lh-accent);
  transform: translateY(2px);
}

.basic-layout__nav-link.router-link-active .basic-layout__nav-icon,
.basic-layout__nav-link--active .basic-layout__nav-icon {
  transform: scale(1.05);
}

.basic-layout__nav-link.router-link-active::after,
.basic-layout__nav-link--active::after {
  background: var(--lh-accent);
  opacity: 1;
  transform: scaleY(1);
}

.basic-layout--sidebar-collapsed .basic-layout__sidebar {
  padding-inline: 9px;
}

.basic-layout--sidebar-collapsed .basic-layout__sidebar-header {
  grid-template-columns: 44px 34px;
}

.basic-layout--sidebar-locked .basic-layout__sidebar-header {
  grid-template-columns: 44px;
  justify-content: center;
}

.basic-layout--sidebar-collapsed .basic-layout__brand {
  justify-content: center;
  gap: 0;
}

.basic-layout--sidebar-collapsed .basic-layout__brand-copy,
.basic-layout--sidebar-collapsed .basic-layout__nav-text,
.basic-layout--sidebar-collapsed .basic-layout__nav-group p {
  max-width: 0;
  opacity: 0;
  overflow: hidden;
  filter: blur(3px);
  transform: translateX(-10px) scale(0.98);
}

.basic-layout--sidebar-collapsed .basic-layout__collapse-button {
  justify-self: center;
}

.basic-layout--sidebar-collapsed .basic-layout__nav {
  gap: 12px;
  padding-right: 0;
}

.basic-layout--sidebar-collapsed .basic-layout__nav-group {
  gap: 8px;
}

.basic-layout--sidebar-collapsed .basic-layout__nav-link {
  justify-content: center;
  gap: 0;
  padding: 0;
}

.basic-layout--sidebar-collapsed .basic-layout__nav-icon {
  width: 20px;
  height: 20px;
  font-size: 19px;
}

.basic-layout--sidebar-collapsed .basic-layout__nav-link::after {
  left: -7px;
}

.basic-layout__sidebar-footer {
  display: grid;
  border-top: 1px solid var(--lh-border-soft);
  padding-top: 14px;
}

.basic-layout__logout-button {
  display: flex;
  min-height: 40px;
  align-items: center;
  justify-content: center;
  gap: 9px;
  border: 1px solid var(--lh-border-soft);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.76);
  color: var(--lh-text-primary);
  cursor: pointer;
  font-weight: 750;
  padding: 0 14px;
  transition:
    border-color 0.24s ease,
    background 0.24s ease,
    box-shadow 0.24s ease,
    color 0.24s ease,
    gap var(--lh-sidebar-duration) var(--lh-sidebar-ease),
    padding var(--lh-sidebar-duration) var(--lh-sidebar-ease),
    transform 0.28s ease;
}

.basic-layout__logout-button:hover {
  border-color: rgba(255, 59, 48, 0.24);
  background: rgba(255, 59, 48, 0.08);
  color: var(--lh-danger);
  box-shadow: 0 10px 22px rgba(255, 59, 48, 0.08);
  transform: translateY(-1px);
}

.basic-layout__logout-icon {
  width: 13px;
  height: 13px;
  flex: 0 0 auto;
  border-radius: 4px;
  background: currentColor;
  opacity: 0.72;
}

.basic-layout__logout-text {
  overflow: hidden;
  max-width: 64px;
  filter: blur(0);
  opacity: 1;
  text-overflow: ellipsis;
  transition:
    max-width var(--lh-sidebar-duration) var(--lh-sidebar-ease),
    opacity var(--lh-sidebar-text-duration) ease,
    filter var(--lh-sidebar-text-duration) ease,
    transform var(--lh-sidebar-duration) var(--lh-sidebar-ease);
  white-space: nowrap;
  will-change: max-width, opacity, filter, transform;
}

.basic-layout--sidebar-collapsed .basic-layout__logout-button {
  gap: 0;
  padding-inline: 0;
}

.basic-layout--sidebar-collapsed .basic-layout__logout-text {
  max-width: 0;
  opacity: 0;
  filter: blur(8px);
  transform: translateX(-10px) scale(0.98);
}

.basic-layout__main {
  display: grid;
  min-width: 0;
  min-height: 100vh;
  grid-template-rows: auto 1fr;
  margin-left: var(--lh-active-sidebar-width);
  background: var(--lh-bg-secondary);
  transition: margin-left var(--lh-sidebar-duration) var(--lh-sidebar-ease);
}

.basic-layout--immersive .basic-layout__main {
  min-height: 100vh;
  grid-template-rows: 1fr;
  margin-left: 0;
}

.basic-layout__topbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  min-height: 50px;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--lh-border-soft);
  background: rgba(255, 255, 255, 0.86);
  padding: 0 clamp(18px, 3vw, 36px);
  backdrop-filter: blur(3px);
}

.basic-layout__identity {
  min-width: 0;
}

.basic-layout__breadcrumb {
  max-width: min(760px, 56vw);
  line-height: 1.45;
}

.basic-layout__breadcrumb :deep(.el-breadcrumb__inner) {
  color: var(--lh-text-tertiary);
  font-size: 13px;
  font-weight: 700;
}

.basic-layout__breadcrumb :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  display: inline-block;
  overflow: hidden;
  max-width: min(520px, 38vw);
  color: var(--lh-text-primary);
  font-size: 15px;
  font-weight: 800;
  text-overflow: ellipsis;
  vertical-align: bottom;
  white-space: nowrap;
}

.basic-layout__breadcrumb :deep(.el-breadcrumb__separator) {
  color: rgba(116, 116, 128, 0.72);
  font-weight: 700;
}

.basic-layout__user-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.basic-layout__content {
  width: min(100%, 1520px);
  margin: 0 auto;
  overflow: visible;
  padding: clamp(20px, 3vw, 40px);
}

.basic-layout--immersive .basic-layout__content {
  width: 100%;
  max-width: none;
  padding: 0;
}

.basic-layout__route-view {
  padding: 0;
  min-width: 0;
  transform-origin: 50% 24px;
}

.basic-layout--immersive .basic-layout__route-view {
  min-height: 100vh;
}

.basic-route-enter-active {
  transition:
    opacity 0.28s ease,
    transform 0.32s cubic-bezier(0.2, 0.82, 0.2, 1),
    filter 0.32s ease;
}

.basic-route-leave-active {
  transition:
    opacity 0.18s ease,
    transform 0.22s cubic-bezier(0.4, 0, 0.2, 1),
    filter 0.22s ease;
}

.basic-route-enter-from {
  opacity: 0;
  filter: blur(8px);
  transform: translateY(14px) scale(0.992);
}

.basic-route-leave-to {
  opacity: 0;
  filter: blur(3px);
  transform: translateY(-8px) scale(0.996);
}

@media (max-width: 900px) {
  .basic-layout {
    display: block;
  }

  .basic-layout__sidebar {
    position: static;
    width: auto;
    height: auto;
  }

  .basic-layout__main {
    margin-left: 0;
  }

  .basic-layout--sidebar-collapsed .basic-layout__sidebar-header {
    grid-template-columns: minmax(0, 1fr) 34px;
  }

  .basic-layout--sidebar-collapsed .basic-layout__brand {
    justify-content: flex-start;
    gap: 12px;
  }

  .basic-layout--sidebar-collapsed .basic-layout__nav-text,
  .basic-layout--sidebar-collapsed .basic-layout__nav-group p,
  .basic-layout--sidebar-collapsed .basic-layout__logout-text {
    max-width: none;
    opacity: 1;
    filter: blur(0);
    overflow: visible;
    transform: none;
  }

  .basic-layout--sidebar-collapsed .basic-layout__collapse-button {
    justify-self: auto;
  }

  .basic-layout--sidebar-collapsed .basic-layout__nav-link {
    justify-content: flex-start;
    gap: 9px;
    padding: 0 10px 0 18px;
  }

  .basic-layout--sidebar-collapsed .basic-layout__logout-button {
    gap: 9px;
    padding-inline: 14px;
  }

  .basic-layout--sidebar-locked .basic-layout__sidebar {
    width: var(--lh-active-sidebar-width);
  }

  .basic-layout--sidebar-locked .basic-layout__sidebar-header {
    grid-template-columns: 44px;
    justify-content: center;
  }

  .basic-layout--sidebar-locked .basic-layout__brand {
    justify-content: center;
    gap: 0;
  }

  .basic-layout--sidebar-locked .basic-layout__brand-copy,
  .basic-layout--sidebar-locked .basic-layout__nav-text,
  .basic-layout--sidebar-locked .basic-layout__nav-group p,
  .basic-layout--sidebar-locked .basic-layout__logout-text {
    overflow: hidden;
    max-width: 0;
    opacity: 0;
    filter: blur(3px);
    transform: translateX(-10px) scale(0.98);
  }

  .basic-layout--sidebar-locked .basic-layout__nav-link {
    justify-content: center;
    gap: 0;
    padding: 0;
  }

  .basic-layout--sidebar-locked .basic-layout__logout-button {
    gap: 0;
    padding-inline: 0;
  }

  .basic-layout__topbar {
    position: static;
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
    padding-block: 16px;
    filter: none;
  }

  .basic-layout__breadcrumb,
  .basic-layout__breadcrumb :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
    max-width: calc(100vw - 36px);
    white-space: normal;
  }
}
</style>
