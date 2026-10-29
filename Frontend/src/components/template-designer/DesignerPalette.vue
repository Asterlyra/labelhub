<script setup lang="ts">
import type { DesignerComponentMeta } from '../../types/template'

defineProps<{
  components: DesignerComponentMeta[]
  compact?: boolean
}>()

const emit = defineEmits<{
  dragStart: [component: DesignerComponentMeta]
}>()

function handleDragStart(event: DragEvent, component: DesignerComponentMeta) {
  if (!event.dataTransfer) {
    return
  }

  event.dataTransfer.effectAllowed = 'copy'
  event.dataTransfer.setData('application/x-labelhub-component', component.type)
  emit('dragStart', component)
}
</script>

<template>
  <aside class="designer-palette" :class="{ 'designer-palette--compact': compact }" aria-label="组件区">
    <header class="designer-palette__header">
      <strong>组件区</strong>
      <span>拖拽组件</span>
    </header>

    <div class="designer-palette__search" aria-hidden="true">搜索组件</div>

    <section class="designer-palette__group">
      <p>基础组件</p>
      <button
        v-for="component in components"
        :key="component.type"
        class="designer-palette__item"
        :class="{ 'designer-palette__item--llm': component.type === 'llmInteraction' }"
        type="button"
        draggable="true"
        :aria-label="component.label"
        :title="compact ? component.label : undefined"
        @dragstart="handleDragStart($event, component)"
      >
        <span class="designer-palette__icon">{{ component.type === 'llmInteraction' ? 'LLM' : component.label.slice(0, 1) }}</span>
        <span class="designer-palette__copy">
          <strong>{{ component.label }}</strong>
          <small>{{ component.description }}</small>
        </span>
      </button>
    </section>
  </aside>
</template>

<style scoped>
.designer-palette {
  position: sticky;
  top: 96px;
  display: grid;
  align-self: start;
  align-content: start;
  gap: 16px;
  min-height: calc(100vh - 180px);
  max-height: calc(100dvh - 116px);
  overflow: auto;
  border: 1px solid var(--lh-border-soft);
  border-radius: var(--lh-radius-md);
  background: rgba(255, 255, 255, 0.86);
  box-shadow: var(--lh-shadow-card);
  padding: 16px;
  backdrop-filter: blur(22px);
}

.designer-palette__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.designer-palette__header strong {
  color: var(--lh-text-primary);
  font-size: 16px;
}

.designer-palette__header span {
  color: var(--lh-accent);
  font-size: 12px;
  font-weight: 750;
}

.designer-palette__search {
  display: flex;
  min-height: 38px;
  align-items: center;
  border: 1px solid var(--lh-border-soft);
  border-radius: 10px;
  background: var(--lh-bg-muted);
  color: var(--lh-text-tertiary);
  font-size: 13px;
  padding: 0 12px;
}

.designer-palette__group {
  display: grid;
  gap: 8px;
}

.designer-palette__group p {
  margin: 0 0 2px;
  color: var(--lh-text-secondary);
  font-size: 13px;
  font-weight: 800;
}

.designer-palette__item {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  width: 100%;
  min-height: 58px;
  border: 1px solid var(--lh-border-soft);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.82);
  color: var(--lh-text-primary);
  cursor: grab;
  padding: 8px 10px;
  text-align: left;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}

.designer-palette__item:hover {
  border-color: rgba(0, 113, 227, 0.26);
  box-shadow: 0 10px 24px rgba(20, 32, 54, 0.08);
  transform: translateY(-1px);
}

.designer-palette__item:active {
  cursor: grabbing;
  transform: translateY(0);
}

.designer-palette__item--llm {
  border-color: rgba(138, 114, 182, 0.3);
  background: #eee5fa;
  color: #8a72b6;
}

.designer-palette__item--llm:hover {
  border-color: rgba(138, 114, 182, 0.56);
  box-shadow: 0 10px 24px rgba(138, 114, 182, 0.14);
}

.designer-palette__icon {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border-radius: 8px;
  background: var(--lh-accent-soft);
  color: var(--lh-accent);
  font-size: 14px;
  font-weight: 850;
}

.designer-palette__item--llm .designer-palette__icon {
  background: rgba(255, 255, 255, 0.58);
  color: #8a72b6;
  font-size: 11px;
}

.designer-palette__item strong,
.designer-palette__item small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.designer-palette__item strong {
  font-size: 14px;
  font-weight: 800;
}

.designer-palette__item small {
  margin-top: 3px;
  color: var(--lh-text-tertiary);
  font-size: 12px;
  font-weight: 650;
}

.designer-palette__item--llm strong,
.designer-palette__item--llm small {
  color: #8a72b6;
}

.designer-palette--compact {
  gap: 12px;
  justify-items: center;
  overflow-x: hidden;
  padding: 12px 10px;
}

.designer-palette--compact .designer-palette__header {
  justify-content: center;
}

.designer-palette--compact .designer-palette__header strong {
  writing-mode: vertical-rl;
  letter-spacing: 0;
}

.designer-palette--compact .designer-palette__header span,
.designer-palette--compact .designer-palette__search,
.designer-palette--compact .designer-palette__group p,
.designer-palette--compact .designer-palette__copy {
  display: none;
}

.designer-palette--compact .designer-palette__group {
  width: 100%;
  justify-items: center;
}

.designer-palette--compact .designer-palette__item {
  min-height: 50px;
  grid-template-columns: 1fr;
  justify-items: center;
  padding: 8px;
}

.designer-palette--compact .designer-palette__icon {
  width: 36px;
  height: 36px;
}
</style>
