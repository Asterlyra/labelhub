<script setup lang="ts">
import { computed } from 'vue'
import EmptyState from './EmptyState.vue'
import ErrorState from './ErrorState.vue'
import RoleTag from './RoleTag.vue'
import type { PagePlan } from '../../types/page'

interface Props {
  plan: PagePlan
}

const props = defineProps<Props>()

const contextItems = computed(() => [
  { label: '当前用户', value: props.plan.roles.join(' / ') },
  { label: '当前任务', value: props.plan.currentTask },
  { label: '任务状态', value: props.plan.status },
  { label: '下一步', value: props.plan.nextAction },
  { label: '异常处理', value: props.plan.exceptionHandling },
])

const secondaryActions = computed(() => props.plan.secondaryActions ?? [])
</script>

<template>
  <section class="page-scaffold">
    <header class="page-scaffold__hero">
      <div class="page-scaffold__copy">
        <p class="page-scaffold__eyebrow">{{ props.plan.eyebrow }} · {{ props.plan.path }}</p>
        <h1 class="page-scaffold__title">{{ props.plan.title }}</h1>
        <p class="page-scaffold__description">{{ props.plan.objective }}</p>
      </div>

      <div class="page-scaffold__actions" aria-label="页面跳转">
        <RouterLink
          v-if="props.plan.primaryAction"
          class="page-scaffold__button page-scaffold__button--primary"
          :to="props.plan.primaryAction.path"
        >
          {{ props.plan.primaryAction.label }}
        </RouterLink>
        <RouterLink
          v-for="action in secondaryActions"
          :key="action.path"
          class="page-scaffold__button page-scaffold__button--secondary"
          :to="action.path"
        >
          {{ action.label }}
        </RouterLink>
      </div>
    </header>

    <div class="page-scaffold__context">
      <article v-for="item in contextItems" :key="item.label" class="page-scaffold__context-card">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </article>
    </div>

    <slot />

    <div class="page-scaffold__grid">
      <article class="page-scaffold__card">
        <h2>使用角色</h2>
        <div class="page-scaffold__roles">
          <RoleTag v-for="role in props.plan.roles" :key="role" :role="role" />
        </div>
      </article>

      <article class="page-scaffold__card page-scaffold__card--ai">
        <h2>核心功能</h2>
        <ul>
          <li v-for="feature in props.plan.coreFeatures" :key="feature">{{ feature }}</li>
        </ul>
      </article>

      <article class="page-scaffold__card">
        <h2>主要组件</h2>
        <ul>
          <li v-for="component in props.plan.mainComponents" :key="component">{{ component }}</li>
        </ul>
      </article>

      <article class="page-scaffold__card">
        <h2>关键交互</h2>
        <ul>
          <li v-for="interaction in props.plan.interactions" :key="interaction">
            {{ interaction }}
          </li>
        </ul>
      </article>

      <article class="page-scaffold__card page-scaffold__card--wide">
        <h2>跳转关系</h2>
        <div class="page-scaffold__routes">
          <span v-for="route in props.plan.routeRelations" :key="route">{{ route }}</span>
        </div>
      </article>

      <EmptyState class="page-scaffold__card" title="空状态" :description="props.plan.emptyState" />
      <ErrorState class="page-scaffold__card" title="错误状态" :description="props.plan.errorState" />
    </div>
  </section>
</template>

<style scoped>
.page-scaffold {
  display: grid;
  gap: 24px;
}

.page-scaffold__hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 24px;
  align-items: end;
  border: 1px solid var(--lh-border-soft);
  border-radius: var(--lh-radius-lg);
  background: var(--lh-bg-elevated);
  box-shadow: var(--lh-shadow-soft);
  padding: clamp(24px, 5vw, 44px);
  backdrop-filter: blur(24px);
}

.page-scaffold__copy {
  display: grid;
  gap: 12px;
}

.page-scaffold__eyebrow {
  margin: 0;
  color: var(--lh-accent);
  font-size: 13px;
  font-weight: 700;
}

.page-scaffold__title {
  max-width: 780px;
  margin: 0;
  font-size: clamp(32px, 5vw, 52px);
  font-weight: 700;
  line-height: 1.08;
  letter-spacing: 0;
}

.page-scaffold__description {
  max-width: 720px;
  margin: 0;
  color: var(--lh-text-secondary);
  font-size: 17px;
  line-height: 1.65;
}

.page-scaffold__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: flex-end;
}

.page-scaffold__button {
  display: inline-flex;
  min-height: 44px;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  font-weight: 650;
  padding: 0 18px;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease,
    background 0.2s ease;
}

.page-scaffold__button:hover {
  transform: translateY(-1px);
}

.page-scaffold__button--primary {
  background: var(--lh-accent);
  color: #ffffff;
  box-shadow: 0 12px 28px rgba(0, 113, 227, 0.22);
}

.page-scaffold__button--secondary {
  border: 1px solid var(--lh-border-soft);
  background: rgba(255, 255, 255, 0.72);
  color: var(--lh-text-primary);
}

.page-scaffold__context {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.page-scaffold__context-card,
.page-scaffold__card {
  border: 1px solid var(--lh-border-soft);
  border-radius: var(--lh-radius-md);
  background: rgba(255, 255, 255, 0.82);
  box-shadow: var(--lh-shadow-card);
  backdrop-filter: blur(20px);
}

.page-scaffold__context-card {
  display: grid;
  gap: 8px;
  min-height: 118px;
  padding: 18px;
}

.page-scaffold__context-card span {
  color: var(--lh-text-tertiary);
  font-size: 12px;
  font-weight: 650;
}

.page-scaffold__context-card strong {
  color: var(--lh-text-primary);
  font-size: 15px;
  line-height: 1.5;
}

.page-scaffold__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.page-scaffold__card {
  padding: 22px;
}

.page-scaffold__card--wide {
  grid-column: 1 / -1;
}

.page-scaffold__card--ai {
  border-color: rgba(142, 92, 247, 0.18);
  background:
    linear-gradient(135deg, rgba(142, 92, 247, 0.12), rgba(255, 255, 255, 0.88)),
    rgba(255, 255, 255, 0.88);
}

.page-scaffold__card h2 {
  margin: 0 0 14px;
  font-size: 17px;
}

.page-scaffold__card ul {
  display: grid;
  gap: 10px;
  margin: 0;
  padding-left: 18px;
  color: var(--lh-text-secondary);
  line-height: 1.65;
}

.page-scaffold__roles,
.page-scaffold__routes {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.page-scaffold__routes span {
  min-height: 34px;
  border-radius: 999px;
  background: var(--lh-bg-secondary);
  color: var(--lh-text-secondary);
  font-size: 13px;
  font-weight: 600;
  padding: 8px 12px;
}

@media (max-width: 1080px) {
  .page-scaffold__hero,
  .page-scaffold__context,
  .page-scaffold__grid {
    grid-template-columns: 1fr;
  }

  .page-scaffold__actions {
    justify-content: flex-start;
  }
}
</style>
