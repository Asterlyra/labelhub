<script setup lang="ts">
interface Props {
  title: string
  description: string
}

const props = defineProps<Props>()
</script>

<template>
  <section class="empty-state" aria-live="polite">
    <div class="empty-state__mark">LH</div>
    <h2 class="empty-state__title">{{ props.title }}</h2>
    <p class="empty-state__description">{{ props.description }}</p>
  </section>
</template>

<style scoped>
.empty-state {
  display: grid;
  gap: 12px;
  justify-items: start;
  border: 1px dashed var(--lh-border-soft);
  border-radius: var(--lh-radius-md);
  background: rgba(255, 255, 255, 0.68);
  padding: 24px;
}

.empty-state__mark {
  display: inline-grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border-radius: 14px;
  background: var(--lh-accent-soft);
  color: var(--lh-accent);
  font-weight: 700;
}

.empty-state__title {
  margin: 0;
  font-size: 18px;
}

.empty-state__description {
  max-width: 620px;
  margin: 0;
  color: var(--lh-text-secondary);
  line-height: 1.7;
}
</style>
