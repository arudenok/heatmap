<script setup>
import { reactive, ref, watch } from 'vue'
import { useAuthStore } from '../stores/auth'
import { extractErrorMessage } from '../services/api'
import IconBase from './IconBase.vue'
import ChangePasswordModal from './ChangePasswordModal.vue'

const auth = useAuthStore()
const open = defineModel({ default: false })
const passwordModalOpen = ref(false)

const form = reactive({
  fullName: '',
  email: ''
})

const loading = ref(false)
const error = ref('')
const success = ref('')

watch(open, (value) => {
  if (!value) return
  form.fullName = auth.user?.fullName || ''
  form.email = auth.user?.email || ''
  error.value = ''
  success.value = ''
})

function close() {
  open.value = false
}

function openPasswordModal() {
  passwordModalOpen.value = true
}

async function onSubmit() {
  error.value = ''
  success.value = ''
  loading.value = true
  try {
    await auth.updateProfile({
      fullName: form.fullName.trim(),
      email: form.email.trim()
    })
    success.value = 'Профиль обновлён'
  } catch (e) {
    error.value = extractErrorMessage(e, 'Не удалось обновить профиль')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div v-if="open" class="modal-backdrop" @click.self="close">
    <div class="modal panel">
      <div class="modal-header">
        <h3><IconBase name="edit" :size="17" /> Профиль</h3>
        <button class="icon-btn" type="button" @click="close"><IconBase name="x" :size="16" /></button>
      </div>

      <form class="modal-form" @submit.prevent="onSubmit">
        <div class="field">
          <label for="profile-name">ФИО</label>
          <input id="profile-name" v-model="form.fullName" class="input" type="text" required maxlength="255" />
        </div>

        <div class="field">
          <label for="profile-email">Логин Сигма</label>
          <input
            id="profile-email"
            v-model="form.email"
            class="input"
            type="text"
            inputmode="numeric"
            pattern="[0-9]+"
            title="Только цифры"
            placeholder="Только цифры"
            required
            maxlength="255"
          />
        </div>

        <button type="button" class="link-btn" @click="openPasswordModal">
          <IconBase name="edit" :size="13" /> Сменить пароль
        </button>

        <p v-if="error" class="error-text">{{ error }}</p>
        <p v-if="success" class="success-text">{{ success }}</p>

        <div class="modal-actions">
          <button type="button" class="btn btn-outline" @click="close">Закрыть</button>
          <button type="submit" class="btn btn-primary" :disabled="loading">
            {{ loading ? 'Сохраняем…' : 'Сохранить' }}
          </button>
        </div>
      </form>
    </div>
  </div>

  <ChangePasswordModal v-model="passwordModalOpen" />
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
  z-index: 50;
}

.modal {
  width: 100%;
  max-width: 480px;
  max-height: 90vh;
  overflow-y: auto;
  padding: 24px 26px;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
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
  margin-top: 14px;
}

.link-btn {
  align-self: flex-start;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: none;
  border: none;
  padding: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--accent-dark);
  cursor: pointer;
}
.link-btn:hover {
  text-decoration: underline;
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
