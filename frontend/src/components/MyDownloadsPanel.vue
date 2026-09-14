<script setup>
import { ref, onMounted } from 'vue'
import { useAuthStore } from '../stores/auth'
import api, { extractErrorMessage } from '../services/api'
import IconBase from './IconBase.vue'

const auth = useAuthStore()
const tools = ref([])
const loading = ref(true)
const error = ref('')
const busyId = ref(null)
const hover = ref({})

const stageMeta = {
  ACCESS: { label: 'Access', class: 'stage-access' },
  USAGE: { label: 'Usage', class: 'stage-usage' },
  HABIT: { label: 'Habit', class: 'stage-habit' },
  STANDARD: { label: 'Process Standard', class: 'stage-standard' }
}

async function load() {
  if (!auth.isAuthenticated) {
    tools.value = []
    return
  }
  loading.value = true
  error.value = ''
  try {
    const { data } = await api.get('/tools/downloaded')
    tools.value = data
  } catch (e) {
    error.value = extractErrorMessage(e, 'Не удалось загрузить скачанные инструменты')
  } finally {
    loading.value = false
  }
}

async function rate(tool, value) {
  if (busyId.value) return
  busyId.value = tool.id
  try {
    const { data } = await api.post(`/tools/${tool.id}/rating`, { rating: value })
    Object.assign(tool, data)
  } catch (e) {
    error.value = extractErrorMessage(e, 'Не удалось сохранить оценку')
  } finally {
    busyId.value = null
  }
}

defineExpose({ load })
onMounted(load)
</script>

<template>
  <div v-if="auth.isAuthenticated && (loading || tools.length)" class="my-downloads panel">
    <div class="my-downloads-title"><IconBase name="download" :size="15" /> Мои инструменты</div>

    <div v-if="loading" class="skeleton" style="height: 48px;"></div>

    <p v-else-if="error" class="error-text">{{ error }}</p>

    <div v-else class="my-downloads-list">
      <div v-for="tool in tools" :key="tool.id" class="my-downloads-row">
        <div class="my-downloads-name">
          {{ tool.name }}
          <span class="stage-badge" :class="stageMeta[tool.stage]?.class">{{ stageMeta[tool.stage]?.label }}</span>
        </div>
        <div class="my-downloads-stars" @mouseleave="hover[tool.id] = 0">
          <button
            v-for="n in 5"
            :key="n"
            type="button"
            class="star-btn"
            :class="{ filled: n <= (hover[tool.id] || tool.myRating || 0) }"
            :disabled="busyId === tool.id"
            :title="`Оценить на ${n}`"
            @mouseenter="hover[tool.id] = n"
            @click="rate(tool, n)"
          >
            <IconBase name="star" :size="16" />
          </button>
          <span class="my-downloads-rating-note">
            {{ tool.myRating ? `ваша оценка: ${tool.myRating}` : 'оцените инструмент' }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.my-downloads {
  padding: 14px 18px;
  margin-bottom: 20px;
}

.my-downloads-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12.5px;
  font-weight: 700;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.3px;
  margin-bottom: 10px;
}

.my-downloads-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.my-downloads-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 6px 0;
}

.my-downloads-name {
  font-weight: 600;
  font-size: 13.5px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.stage-badge {
  font-size: 10.5px;
  font-weight: 700;
  padding: 1px 9px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  background: var(--surface-muted);
  color: var(--text-secondary);
}
.stage-access { background: var(--info-soft); color: var(--info); border-color: transparent; }
.stage-usage { background: var(--accent-soft); color: var(--accent-dark); border-color: transparent; }
.stage-habit { background: var(--warn-soft); color: var(--warn); border-color: transparent; }
.stage-standard { background: #efe8fb; color: #6c3fc9; border-color: transparent; }

.my-downloads-stars {
  display: flex;
  align-items: center;
  gap: 2px;
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
  transform: scale(1.12);
}
.star-btn.filled {
  color: var(--warn, #d69b1a);
}
.star-btn:disabled {
  cursor: default;
}

.my-downloads-rating-note {
  font-size: 12px;
  color: var(--text-muted);
  margin-left: 8px;
  white-space: nowrap;
}
</style>
