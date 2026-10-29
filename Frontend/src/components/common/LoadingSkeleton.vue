<script setup lang="ts">
interface Props {
  rows?: number
}

const props = withDefaults(defineProps<Props>(), {
  rows: 3,
})
</script>

<template>
  <div class="loading-skeleton" aria-label="加载中">
    <span v-for="row in props.rows" :key="row" class="loading-skeleton__row" />
  </div>
</template>

<style scoped>
.loading-skeleton {
  display: grid;
  gap: 12px;
  width: 100%;
}

.loading-skeleton__row {
  display: block;
  height: 16px;
  overflow: hidden;
  border-radius: 999px;
  background: linear-gradient(90deg, #ececf0, #f8f8fb, #ececf0);
  background-size: 220% 100%;
  animation: shimmer 1.4s ease infinite;
}

.loading-skeleton__row:nth-child(2) {
  width: 72%;
}

.loading-skeleton__row:nth-child(3) {
  width: 48%;
}

@keyframes shimmer {
  from {
    background-position: 100% 0;
  }

  to {
    background-position: -100% 0;
  }
}
</style>
