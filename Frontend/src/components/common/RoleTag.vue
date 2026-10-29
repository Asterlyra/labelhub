<script setup lang="ts">
import { computed } from 'vue'
import { getRoleDefinition } from '../../constants/roles'
import type { UserRole } from '../../types/user'

interface Props {
  role: UserRole
  compact?: boolean
}

const props = defineProps<Props>()

const roleMeta = computed(() => getRoleDefinition(props.role))
</script>

<template>
  <span
    class="role-tag"
    :class="{ 'role-tag--compact': props.compact }"
    :style="{ '--role-accent': roleMeta.accent }"
  >
    {{ roleMeta.label }}
  </span>
</template>

<style scoped>
.role-tag {
  display: inline-flex;
  min-height: 28px;
  align-items: center;
  justify-content: center;
  border: 1px solid color-mix(in srgb, var(--role-accent) 22%, transparent);
  border-radius: 999px;
  background: color-mix(in srgb, var(--role-accent) 12%, white);
  color: color-mix(in srgb, var(--role-accent) 72%, #1d1d1f);
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
  padding: 0 12px;
}

.role-tag--compact {
  min-height: 24px;
  padding: 0 10px;
  font-size: 12px;
}
</style>
