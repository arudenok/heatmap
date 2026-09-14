<script setup>
import { ref, watch } from 'vue'
import api, { extractErrorMessage } from '../services/api'
import IconBase from './IconBase.vue'
import ToolDetailModal from './ToolDetailModal.vue'

const open = defineModel({ default: false })

const tools = ref([])
const loading = ref(true)
const error = ref('')
const busyId = ref(null)
const hover = ref({})

const detailOpen = ref(false)
const detailTool = ref(null)

const stageMeta = {
  ACCESS: { label: 'Access', class: 'stage-access' },
  USAGE: { label: 'Usage', class: 'stage-usage' },
  HABIT: { label: 'Habit', class: 'stage-habit' },
  STANDARD: { label: 'Process Standard', class: 'stage-standard' }
}

async function load() {
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

function close() {
  open.value = false
}

// Клик по названию открывает ту же карточку "Подробнее", что и в реестре -
// счётчик просмотров засчитывается так же, по факту открытия карточки.
async function openDetail(tool) {
  try {
    const { data } = await api.post(`/tools/${tool.id}/view`)
    Object.assign(tool, data)
  } catch {
    // счётчик просмотров не критичен - открываем карточку даже если запрос не прошёл
  } finally {
    detailTool.value = tool
    detailOpen.value = true
  }
}

// Удаление доступно только если инструмент можно редактировать (свой/админ) -
// сама кнопка в ToolDetailModal и так скрыта для остальных случаев.
async function onDeleteFromDetail(tool) {
  if (!confirm(`Удалить инструмент «${tool.name}»?`)) return
  try {
    await api.delete(`/tools/${tool.id}`)
    tools.value = tools.value.filter((t) => t.id !== tool.id)
    detailOpen.value = false
  } catch (e) {
    error.value = extractErrorMessage(e, 'Не удалось удалить инструмент')
  }
}

// Загружаем список заново при каждом открытии - это проще, чем прокидывать
// событие обновления из других частей приложения, а список короткий.
watch(open, (value) => {
  if (value) load()
})
</script>

<template>
  <div v-if="open" class="modal-backdrop" @click.self="close">
    <div class="modal panel">
      <div class="modal-header">
        <h3><IconBase name="download" :size="17" /> Мои инструменты</h3>
        <button class="icon-btn" type="button" @click="close"><IconBase name="x" :size="16" /></button>
      </div>

      <p class="modal-hint">Инструменты, которые вы скачивали. Здесь можно поставить или изменить оценку.</p>

      <div v-if="loading" class="skeleton" style="height: 48px;"></div>

      <p v-else-if="error" class="error-text">{{ error }}</p>

      <p v-else-if="!tools.length" class="empty-text">Вы пока ничего не скачивали.</p>

      <div v-else class="my-downloads-list">
        <div v-for="tool in tools" :key="tool.id" class="my-downloads-row">
          <button type="button" class="my-downloads-name" @click="openDetail(tool)">
            {{ tool.name }}
            <span class="stage-badge" :class="stageMeta[tool.stage]?.class">{{ stageMeta[tool.stage]?.label }}</span>
          </button>
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

    <ToolDetailModal v-model="detailOpen" :tool="detailTool" @delete="onDeleteFromDetail" />
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

.modal-hint {
  font-size: 13px;
  color: var(--text-secondary);
  margin: 0 0 16px;
}

.empty-text {
  font-size: 13.5px;
  color: var(--text-muted);
  margin: 0;
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
  border-bottom: 1px solid var(--border);
}
.my-downloads-row:last-child {
  border-bottom: none;
}

.my-downloads-name {
  font-weight: 600;
  font-size: 13.5px;
  display: flex;
  align-items: center;
  gap: 8px;
  background: none;
  border: none;
  padding: 4px 2px;
  margin: -4px -2px;
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  font-family: inherit;
  cursor: pointer;
  text-align: left;
}
.my-downloads-name:hover {
  background: var(--surface-muted);
  color: var(--accent-dark);
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
