<script setup lang="ts"></script>

<template>
  <main class="auth-layout">
    <RouterView />
  </main>
</template>

<style scoped>
.auth-layout {
  display: grid;
  min-height: 100vh;
  place-items: stretch;
}
</style>
