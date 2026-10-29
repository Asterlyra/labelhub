<script setup lang="ts">
interface Props {
  title: string
  description: string
}

const props = defineProps<Props>()
</script>

<template>
  <section class="error-state" role="status">
    <strong class="error-state__title">{{ props.title }}</strong>
    <span class="error-state__description">{{ props.description }}</span>
  </section>
</template>

<style scoped>
.error-state {
  display: grid;
  gap: 8px;
  border: 1px solid rgba(255, 59, 48, 0.18);
  border-radius: var(--lh-radius-md);
  background: rgba(255, 59, 48, 0.07);
  padding: 18px 20px;
}

.error-state__title {
  color: #b42318;
}

.error-state__description {
  color: var(--lh-text-secondary);
  line-height: 1.6;
}
</style>
