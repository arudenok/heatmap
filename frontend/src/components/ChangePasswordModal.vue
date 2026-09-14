<script setup>
import { reactive, ref, watch } from 'vue'
import { useAuthStore } from '../stores/auth'
import { extractErrorMessage } from '../services/api'
import IconBase from './IconBase.vue'

const auth = useAuthStore()
const open = defineModel({ default: false })

const form = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const loading = ref(false)
const error = ref('')
const success = ref('')

watch(open, (value) => {
  if (!value) return
  form.currentPassword = ''
  form.newPassword = ''
  form.confirmPassword = ''
  error.value = ''
  success.value = ''
})

function close() {
  open.value = false
}

function validate() {
  if (!form.currentPassword.trim()) {
    error.value = 'Введите текущий пароль'
    return false
  }
  if (form.newPassword.length < 6) {
    error.value = 'Новый пароль должен быть не короче 6 символов'
    return false
  }
  if (form.newPassword === form.currentPassword) {
    error.value = 'Новый пароль должен отличаться от текущего'
    return false
  }
  if (form.newPassword !== form.confirmPassword) {
    error.value = 'Новый пароль и подтверждение не совпадают'
    return false
  }
  return true
}

async function onSubmit() {
  error.value = ''
  success.value = ''
  if (!validate()) return

  loading.value = true
  try {
    await auth.updateProfile({
      currentPassword: form.currentPassword,
      newPassword: form.newPassword
    })
    success.value = 'Пароль изменён'
    form.currentPassword = ''
    form.newPassword = ''
    form.confirmPassword = ''
  } catch (e) {
    error.value = extractErrorMessage(e, 'Не удалось изменить пароль')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div v-if="open" class="modal-backdrop" @click.self="close">
    <div class="modal panel">
      <div class="modal-header">
        <h3><IconBase name="edit" :size="17" /> Смена пароля</h3>
        <button class="icon-btn" type="button" @click="close"><IconBase name="x" :size="16" /></button>
      </div>

      <form class="modal-form" @submit.prevent="onSubmit">
        <div class="field">
          <label for="pwd-current">Текущий пароль</label>
          <input
            id="pwd-current"
            v-model="form.currentPassword"
            class="input"
            type="password"
            autocomplete="current-password"
            required
          />
        </div>

        <div class="field">
          <label for="pwd-new">Новый пароль</label>
          <input
            id="pwd-new"
            v-model="form.newPassword"
            class="input"
            type="password"
            autocomplete="new-password"
            minlength="6"
            required
          />
        </div>

        <div class="field">
          <label for="pwd-confirm">Повторите новый пароль</label>
          <input
            id="pwd-confirm"
            v-model="form.confirmPassword"
            class="input"
            type="password"
            autocomplete="new-password"
            minlength="6"
            required
          />
        </div>

        <p v-if="error" class="error-text">{{ error }}</p>
        <p v-if="success" class="success-text">{{ success }}</p>

        <div class="modal-actions">
          <button type="button" class="btn btn-outline" @click="close">Закрыть</button>
          <button type="submit" class="btn btn-primary" :disabled="loading">
            {{ loading ? 'Сохраняем…' : 'Сменить пароль' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped>
.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(20, 24, 38, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  z-index: 60;
}

.modal {
  width: 100%;
  max-width: 420px;
  max-height: 90vh;
  overflow-y: auto;
  padding: 24px 26px;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.modal-header h3 {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 17px;
  margin: 0;
}

.icon-btn {
  background: none;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  padding: 6px;
  border-radius: var(--radius-sm);
}
.icon-btn:hover {
  background: var(--surface-muted);
  color: var(--text-primary);
}

.modal-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.success-text {
  font-size: 13px;
  color: var(--accent-dark);
  margin: 0;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 6px;
}
</style>
