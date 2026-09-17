<script setup>
import { RouterView } from 'vue-router'
import ConfirmDialog from './components/ConfirmDialog.vue'
</script>

<template>
  <RouterView />
  <!-- Единственный на всё приложение диалог подтверждения (см. useConfirm.js) - заменяет
       нативный window.confirm() с его системным видом браузера во всех местах, где раньше
       использовался (удаление инструмента/пользователя/заметки, отзыв заявки с модерации). -->
  <ConfirmDialog />
</template>
