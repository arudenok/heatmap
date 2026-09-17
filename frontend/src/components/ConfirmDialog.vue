<script setup>
import { onBeforeUnmount, onMounted } from 'vue'
import { useConfirm } from '../composables/useConfirm'
import IconBase from './IconBase.vue'

// Единственный экземпляр на всё приложение (см. App.vue) - заменяет window.confirm(),
// у которого нативный вид браузера, выбивающийся из тёмной темы (см. useConfirm.js).
const { confirmState, settle } = useConfirm()

function onKeydown(event) {
  if (!confirmState.open) return
  if (event.key === 'Escape') settle(false)
  if (event.key === 'Enter') settle(true)
}

onMounted(() => document.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => document.removeEventListener('keydown', onKeydown))
</script>

<template>
  <div v-if="confirmState.open" class="confirm-backdrop" @click.self="settle(false)">
    <div class="confirm-modal panel">
      <div class="confirm-header">
        <span class="confirm-icon" :class="{ 'confirm-icon-danger': confirmState.danger }">
          <IconBase v-if="confirmState.icon" :name="confirmState.icon" :size="18" />
        </span>
        <h3>{{ confirmState.title }}</h3>
      </div>
      <p class="confirm-message">{{ confirmState.message }}</p>
      <div class="confirm-actions">
        <button type="button" class="btn btn-outline btn-sm" @click="settle(false)">
          {{ confirmState.cancelLabel }}
        </button>
        <button
          type="button"
          class="btn btn-sm"
          :class="confirmState.danger ? 'btn-danger' : 'btn-primary'"
          @click="settle(true)"
        >
          {{ confirmState.confirmLabel }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.confirm-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(20, 24, 38, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  /* Выше любой другой модалки (макс. 60 - см. ChangePasswordModal) - подтверждение может
     всплыть поверх уже открытого окна (например, удаление из "Мои инструменты"). */
  z-index: 70;
}

.confirm-modal {
  width: 100%;
  max-width: 400px;
  padding: 22px 24px;
}

.confirm-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.confirm-header h3 {
  font-size: 16px;
  margin: 0;
}

.confirm-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  flex-shrink: 0;
  background: var(--surface-muted);
  color: var(--text-secondary);
}
.confirm-icon:empty {
  display: none;
}
.confirm-icon-danger {
  background: var(--danger-soft, rgba(214, 69, 69, 0.1));
  color: var(--danger, #d64545);
}

.confirm-message {
  font-size: 13.5px;
  line-height: 1.5;
  color: var(--text-secondary);
  margin: 0 0 20px;
  white-space: pre-line;
}

.confirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
