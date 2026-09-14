<script setup>
import { RouterLink } from 'vue-router'
</script>

<template>
  <div class="not-found">
    <h1>404</h1>
    <p>Страница не найдена</p>
    <RouterLink class="btn btn-primary" to="/">На главную</RouterLink>
  </div>
</template>

<style scoped>
.not-found {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: var(--text-secondary);
}
.not-found h1 {
  font-size: 48px;
  margin: 0;
  color: var(--text-primary);
}
</style>
