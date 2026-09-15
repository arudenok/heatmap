<script setup>
import { ref } from 'vue'
import { useRouter, RouterLink } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { extractErrorMessage } from '../services/api'
import IconBase from '../components/IconBase.vue'

const auth = useAuthStore()
const router = useRouter()

const form = ref({
  fullName: '',
  username: '',
  password: '',
  confirmPassword: ''
})
const loading = ref(false)
const error = ref('')

function validate() {
  if (form.value.password.length < 6) {
    error.value = 'Пароль должен быть не короче 6 символов'
    return false
  }
  if (form.value.password !== form.value.confirmPassword) {
    error.value = 'Пароль и подтверждение не совпадают'
    return false
  }
  return true
}

async function onSubmit() {
  error.value = ''
  if (!validate()) return

  loading.value = true
  try {
    // confirmPassword нужен только для проверки на клиенте - бэкенду не передаём.
    const { confirmPassword, ...payload } = form.value
    await auth.register(payload)
    router.replace({ name: 'dashboard' })
  } catch (e) {
    error.value = extractErrorMessage(e, 'Не удалось зарегистрироваться')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-screen">
    <div class="auth-card panel">
      <RouterLink to="/" class="auth-brand">
        <span class="auth-brand-icon"><IconBase name="layers" :size="22" /></span>
        <div>
          <div class="auth-brand-title">HeatMap</div>
          <div class="auth-brand-sub">Реестр AI-инструментов PDLC</div>
        </div>
      </RouterLink>

      <h1>Регистрация</h1>
      <p class="auth-hint">Новые учётные записи получают роль «Пользователь» — можно добавлять инструменты на модерацию.</p>

      <form class="auth-form" @submit.prevent="onSubmit">
        <div class="field">
          <label for="fullName">ФИО</label>
          <input id="fullName" v-model="form.fullName" class="input" type="text" required />
        </div>
        <div class="field">
          <label for="username">Логин Сигма</label>
          <input
            id="username"
            v-model="form.username"
            class="input"
            type="text"
            inputmode="numeric"
            pattern="[0-9]+"
            title="Только цифры"
            placeholder="Только цифры"
            required
          />
        </div>
        <div class="field">
          <label for="password">Пароль</label>
          <input
            id="password"
            v-model="form.password"
            class="input"
            type="password"
            autocomplete="new-password"
            minlength="6"
            required
          />
        </div>
        <div class="field">
          <label for="confirmPassword">Повторите пароль</label>
          <input
            id="confirmPassword"
            v-model="form.confirmPassword"
            class="input"
            type="password"
            autocomplete="new-password"
            minlength="6"
            required
          />
        </div>

        <p v-if="error" class="error-text">{{ error }}</p>

        <button class="btn btn-primary auth-submit" type="submit" :disabled="loading">
          {{ loading ? 'Регистрируем…' : 'Создать аккаунт' }}
        </button>
      </form>

      <p class="auth-switch">
        Уже есть аккаунт?
        <RouterLink to="/login">Войти</RouterLink>
      </p>
    </div>
  </div>
</template>

<style scoped>
.auth-screen {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background:
    radial-gradient(circle at 15% 10%, rgba(15, 110, 95, 0.08), transparent 40%),
    radial-gradient(circle at 85% 90%, rgba(47, 94, 207, 0.08), transparent 40%),
    var(--bg);
}

.auth-card {
  width: 100%;
  max-width: 440px;
  padding: 36px 34px;
}

.auth-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 26px;
  text-decoration: none;
  color: inherit;
  cursor: pointer;
  width: fit-content;
}

.auth-brand-icon {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  background: var(--accent-soft);
  color: var(--accent-dark);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.auth-brand-title {
  font-size: 16px;
  font-weight: 800;
  letter-spacing: -0.2px;
}

.auth-brand-sub {
  font-size: 12px;
  color: var(--text-muted);
}

h1 {
  font-size: 22px;
  margin: 0 0 6px;
}

.auth-hint {
  font-size: 13px;
  color: var(--text-secondary);
  margin: 0 0 22px;
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.auth-submit {
  width: 100%;
  padding: 11px;
  margin-top: 4px;
}

.auth-switch {
  text-align: center;
  font-size: 13px;
  color: var(--text-secondary);
  margin-top: 20px;
}

.auth-switch a {
  color: var(--accent-dark);
  font-weight: 600;
  text-decoration: none;
}
.auth-switch a:hover {
  text-decoration: underline;
}
</style>
