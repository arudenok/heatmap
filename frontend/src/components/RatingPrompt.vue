<script setup>
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import api, { extractErrorMessage } from '../services/api'
import IconBase from './IconBase.vue'

const props = defineProps({
  tool: { type: Object, default: null }
})

const emit = defineEmits(['close', 'rated'])

const auth = useAuthStore()
const router = useRouter()

const hoverValue = ref(0)
const submitting = ref(false)
const error = ref('')
const done = ref(false)
const needsAuth = ref(false)

watch(
  () => props.tool,
  () => {
    hoverValue.value = 0
    error.value = ''
    done.value = false
    needsAuth.value = false
  }
)

function close() {
  emit('close')
}

function goToLogin() {
  close()
  router.push({ name: 'login', query: { redirect: '/' } })
}

async function rate(value) {
  if (submitting.value || done.value) return
  error.value = ''

  // Оценивать могут только вошедшие в аккаунт - вместо молчаливого редиректа
  // явно объясняем гостю, что нужно войти, и даём кнопку для этого.
  if (!auth.isAuthenticated) {
    needsAuth.value = true
    return
  }

  submitting.value = true
  try {
    await api.post(`/tools/${props.tool.id}/rating`, { rating: value })
    done.value = true
    emit('rated', value)
    setTimeout(close, 1400)
  } catch (e) {
    error.value = extractErrorMessage(e, 'Не удалось сохранить оценку')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div v-if="tool" class="rating-prompt panel">
    <button class="icon-btn" type="button" @click="close"><IconBase name="x" :size="15" /></button>

    <template v-if="needsAuth">
      <div class="rating-prompt-title">Оценка доступна только после входа</div>
      <div class="rating-prompt-tool">Войдите в аккаунт, чтобы оценить «{{ tool.name }}»</div>
      <div class="rating-prompt-actions">
        <button type="button" class="btn btn-primary btn-sm" @click="goToLogin">Войти</button>
        <button type="button" class="link-btn" @click="close">Может позже</button>
      </div>
    </template>
    <template v-else-if="!done">
      <div class="rating-prompt-title">Пожалуйста, оцените инструмент</div>
      <div class="rating-prompt-tool">{{ tool.name }}</div>
      <div class="stars" @mouseleave="hoverValue = 0">
        <button
          v-for="n in 5"
          :key="n"
          type="button"
          class="star-btn"
          :class="{ filled: n <= hoverValue }"
          :disabled="submitting"
          @mouseenter="hoverValue = n"
          @click="rate(n)"
        >
          <IconBase name="star" :size="22" />
        </button>
      </div>
      <p v-if="error" class="error-text">{{ error }}</p>
      <button type="button" class="link-btn" @click="close">Может позже</button>
    </template>
    <template v-else>
      <div class="rating-prompt-thanks"><IconBase name="check" :size="16" /> Спасибо за оценку!</div>
    </template>
  </div>
</template>

<style scoped>
.rating-prompt {
  position: fixed;
  right: 24px;
  bottom: 24px;
  width: 100%;
  max-width: 300px;
  padding: 18px 20px;
  z-index: 55;
  box-shadow: 0 8px 28px rgba(20, 24, 38, 0.16);
}

.icon-btn {
  position: absolute;
  top: 10px;
  right: 10px;
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

.rating-prompt-title {
  font-weight: 700;
  font-size: 14.5px;
  padding-right: 20px;
  margin-bottom: 4px;
}

.rating-prompt-tool {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 14px;
}

.stars {
  display: flex;
  gap: 4px;
  margin-bottom: 10px;
}

.star-btn {
  background: none;
  border: none;
  padding: 2px;
  cursor: pointer;
  color: var(--border-strong);
  transition: color 0.1s ease, transform 0.1s ease;
}
.star-btn:hover {
  transform: scale(1.1);
}
.star-btn.filled {
  color: var(--warn, #d69b1a);
}

.link-btn {
  background: none;
  border: none;
  padding: 0;
  font-size: 12.5px;
  color: var(--text-muted);
  cursor: pointer;
}
.link-btn:hover {
  text-decoration: underline;
}

.rating-prompt-actions {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 4px;
}

.rating-prompt-thanks {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--accent-dark);
  padding-right: 16px;
}

@media (max-width: 480px) {
  .rating-prompt {
    right: 16px;
    left: 16px;
    bottom: 16px;
    max-width: none;
  }
}
</style>
