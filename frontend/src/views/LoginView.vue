<script setup>
import { ref } from 'vue'
import { useRouter, useRoute, RouterLink } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { extractErrorMessage } from '../services/api'
import IconBase from '../components/IconBase.vue'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const usernameOrEmail = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

async function onSubmit() {
  error.value = ''
  loading.value = true
  try {
    await auth.login(usernameOrEmail.value.trim(), password.value)
    router.replace(route.query.redirect?.toString() || { name: 'dashboard' })
  } catch (e) {
    error.value = extractErrorMessage(e, 'Не удалось войти. Проверьте логин и пароль')
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

      <h1>Вход в систему</h1>
      <p class="auth-hint">Используйте учётную запись, выданную администратором, или зарегистрируйтесь.</p>

      <form class="auth-form" @submit.prevent="onSubmit">
        <div class="field">
          <label for="login">Имя пользователя или логин Сигма</label>
          <input id="login" v-model="usernameOrEmail" class="input" type="text" autocomplete="username" required />
        </div>
        <div class="field">
          <label for="password">Пароль</label>
          <input id="password" v-model="password" class="input" type="password" autocomplete="current-password" required />
        </div>

        <p v-if="error" class="error-text">{{ error }}</p>

        <button class="btn btn-primary auth-submit" type="submit" :disabled="loading">
          {{ loading ? 'Входим…' : 'Войти' }}
        </button>
      </form>

      <p class="auth-switch">
        Нет учётной записи?
        <RouterLink to="/register">Зарегистрироваться</RouterLink>
      </p>

      <div class="auth-demo">
        <div class="auth-demo-title">Демо-доступы</div>
        <div class="auth-demo-row"><span>Администратор</span><code>admin / admin123</code></div>
        <div class="auth-demo-row"><span>Пользователь</span><code>ivanov / user123</code></div>
      </div>
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
  max-width: 420px;
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
  gap: 16px;
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

.auth-demo {
  margin-top: 22px;
  padding: 14px 16px;
  background: var(--surface-muted);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
}

.auth-demo-title {
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.4px;
  color: var(--text-muted);
  margin-bottom: 8px;
}

.auth-demo-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: var(--text-secondary);
  padding: 3px 0;
}

.auth-demo-row code {
  color: var(--text-primary);
  font-family: 'SFMono-Regular', Consolas, monospace;
}
</style>
