<script setup lang="ts">
import { computed } from 'vue'
import { getTaskStatusDefinition } from '../../constants/taskStatus'
import type { TaskStatus } from '../../types/task'

interface Props {
  status: TaskStatus
}

const props = defineProps<Props>()

const statusMeta = computed(() => getTaskStatusDefinition(props.status))

const tone = computed(() => {
  if (['APPROVED', 'COMPLETED'].includes(props.status)) return 'success'
  if (['REJECTED', 'REWORKING'].includes(props.status)) return 'danger'
  if (['DRAFT', 'AI_REVIEWED', 'HUMAN_REVIEWING'].includes(props.status)) return 'warning'
  return 'info'
})
</script>

<template>
  <span class="task-status" :class="`task-status--${tone}`">
    {{ statusMeta?.label ?? props.status }}
  </span>
</template>

<style scoped>
.task-status {
  display: inline-flex;
  min-height: 28px;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 650;
  padding: 0 12px;
}

.task-status--success {
  background: rgba(52, 199, 89, 0.12);
  color: #178a38;
}

.task-status--warning {
  background: rgba(255, 159, 10, 0.14);
  color: #9a5b00;
}

.task-status--danger {
  background: rgba(255, 59, 48, 0.12);
  color: #b42318;
}

.task-status--info {
  background: rgba(10, 132, 255, 0.12);
  color: #0057b8;
}
</style>
