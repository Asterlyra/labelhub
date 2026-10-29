<template>
  <RouterView v-slot="{ Component, route: viewRoute }">
    <Transition name="app-shell" mode="out-in">
      <div :key="viewRoute.matched[0]?.path ?? viewRoute.fullPath" class="app-shell-view">
        <component :is="Component" />
      </div>
    </Transition>
  </RouterView>
</template>

<style scoped>
.app-shell-view {
  min-height: 100vh;
}

.app-shell-enter-active {
  transition:
    opacity 0.42s ease,
    transform 0.52s cubic-bezier(0.16, 1, 0.3, 1),
    filter 0.42s ease;
}

.app-shell-leave-active {
  transition:
    opacity 0.2s ease,
    transform 0.24s ease,
    filter 0.2s ease;
}

.app-shell-enter-from {
  opacity: 0;
  filter: blur(10px);
  transform: translateX(34px) scale(0.985);
}

.app-shell-leave-to {
  opacity: 0;
  filter: blur(6px);
  transform: translateX(-16px) scale(0.994);
}

@media (prefers-reduced-motion: reduce) {
  .app-shell-enter-active,
  .app-shell-leave-active {
    transition: none;
  }
}
</style>
