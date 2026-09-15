<script setup>
import { ref, watch } from 'vue'
import api, { extractErrorMessage } from '../services/api'
import IconBase from './IconBase.vue'
import ToolDetailModal from './ToolDetailModal.vue'
import AddToolModal from './AddToolModal.vue'

const open = defineModel({ default: false })

// Вкладка "Скачанные" - то, что пользователь скачивал (можно оценить).
// Вкладка "Загруженные" - собственные заявки/инструменты пользователя, любого статуса.
const activeTab = ref('downloaded')
const loadedTabs = ref({ downloaded: false, uploaded: false })

const downloadedTools = ref([])
const uploadedTools = ref([])
const loading = ref(true)
const error = ref('')
const busyId = ref(null)
const hover = ref({})

const detailOpen = ref(false)
const detailTool = ref(null)

// Нужны для формы редактирования собственной заявки (AddToolModal).
const filterOptions = ref({ roles: [], frameworks: [], segments: [] })
const editModalOpen = ref(false)
const editingTool = ref(null)

const stageMeta = {
  ACCESS: { label: 'Access', class: 'stage-access' },
  USAGE: { label: 'Usage', class: 'stage-usage' },
  HABIT: { label: 'Habit', class: 'stage-habit' },
  STANDARD: { label: 'Process Standard', class: 'stage-standard' }
}

const statusMeta = {
  PENDING: { label: 'На модерации', class: 'badge-warn' },
  PUBLISHED: { label: 'Опубликован', class: 'badge-info' },
  REJECTED: { label: 'Отклонено', class: 'badge-danger' }
}

async function loadDownloaded() {
  const { data } = await api.get('/tools/downloaded')
  downloadedTools.value = data
}

async function loadUploaded() {
  const { data } = await api.get('/tools/mine')
  uploadedTools.value = data
}

async function loadFilterOptions() {
  try {
    const { data } = await api.get('/tools/filter-options')
    filterOptions.value = data
  } catch {
    // Список ролей/фреймворков/сегментов не критичен - молча оставляем пустым.
  }
}

async function loadTab(tab) {
  loading.value = true
  error.value = ''
  try {
    if (tab === 'downloaded') {
      await loadDownloaded()
    } else {
      await loadUploaded()
    }
    loadedTabs.value[tab] = true
  } catch (e) {
    error.value = extractErrorMessage(
      e,
      tab === 'downloaded' ? 'Не удалось загрузить скачанные инструменты' : 'Не удалось загрузить загруженные инструменты'
    )
  } finally {
    loading.value = false
  }
}

function switchTab(tab) {
  activeTab.value = tab
  error.value = ''
  if (!loadedTabs.value[tab]) loadTab(tab)
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

// Редактировать из вкладки "Загруженные" можно только заявку, ещё не прошедшую модерацию -
// это дублирует проверку на бэкенде (ToolService.update), но не даёт открыть форму впустую.
function openEditModal(tool) {
  editingTool.value = tool
  editModalOpen.value = true
}

async function onToolUpdated() {
  editingTool.value = null
  await loadUploaded()
}

async function deleteTool(tool, { fromDetail = false } = {}) {
  if (!confirm(`Удалить инструмент «${tool.name}»?`)) return
  try {
    await api.delete(`/tools/${tool.id}`)
    downloadedTools.value = downloadedTools.value.filter((t) => t.id !== tool.id)
    uploadedTools.value = uploadedTools.value.filter((t) => t.id !== tool.id)
    if (fromDetail) detailOpen.value = false
  } catch (e) {
    error.value = extractErrorMessage(e, 'Не удалось удалить инструмент')
  }
}

function onDeleteFromDetail(tool) {
  deleteTool(tool, { fromDetail: true })
}

// При каждом открытии модалки перезагружаем всё заново - список короткий,
// а это проще, чем прокидывать события обновления из других частей приложения.
watch(open, (value) => {
  if (!value) return
  activeTab.value = 'downloaded'
  loadedTabs.value = { downloaded: false, uploaded: false }
  loadTab('downloaded')
  loadFilterOptions()
})
</script>

<template>
  <div v-if="open" class="modal-backdrop" @click.self="close">
    <div class="modal panel">
      <div class="modal-header">
        <h3><IconBase name="download" :size="17" /> Мои инструменты</h3>
        <button class="icon-btn" type="button" @click="close"><IconBase name="x" :size="16" /></button>
      </div>

      <div class="tab-bar">
        <button
          type="button"
          class="tab-btn"
          :class="{ active: activeTab === 'downloaded' }"
          @click="switchTab('downloaded')"
        >
          Скачанные
        </button>
        <button
          type="button"
          class="tab-btn"
          :class="{ active: activeTab === 'uploaded' }"
          @click="switchTab('uploaded')"
        >
          Загруженные
        </button>
      </div>

      <p v-if="activeTab === 'downloaded'" class="modal-hint">Инструменты, которые вы скачивали. Здесь можно поставить или изменить оценку.</p>
      <p v-else class="modal-hint">Инструменты, добавленные вами - включая заявки на модерации и отклонённые.</p>

      <div v-if="loading" class="skeleton" style="height: 48px;"></div>

      <p v-else-if="error" class="error-text">{{ error }}</p>

      <template v-else-if="activeTab === 'downloaded'">
        <p v-if="!downloadedTools.length" class="empty-text">Вы пока ничего не скачивали.</p>

        <div v-else class="my-downloads-list">
          <div v-for="tool in downloadedTools" :key="tool.id" class="my-downloads-row">
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
      </template>

      <template v-else>
        <p v-if="!uploadedTools.length" class="empty-text">Вы пока не добавляли инструменты.</p>

        <div v-else class="my-downloads-list">
          <div v-for="tool in uploadedTools" :key="tool.id" class="my-downloads-row">
            <button type="button" class="my-downloads-name" @click="openDetail(tool)">
              {{ tool.name }}
              <span class="badge" :class="statusMeta[tool.status]?.class">{{ statusMeta[tool.status]?.label }}</span>
            </button>
            <div class="my-uploads-actions">
              <button
                v-if="tool.status === 'PENDING'"
                class="btn btn-ghost btn-sm"
                type="button"
                @click="openEditModal(tool)"
              >
                <IconBase name="edit" :size="13" /> Редактировать
              </button>
              <button class="btn btn-ghost btn-sm" type="button" @click="deleteTool(tool)">
                <IconBase name="trash" :size="13" /> Удалить
              </button>
            </div>
          </div>
        </div>
      </template>
    </div>

    <ToolDetailModal v-model="detailOpen" :tool="detailTool" @delete="onDeleteFromDetail" />
    <AddToolModal
      v-model="editModalOpen"
      :filter-options="filterOptions"
      :edit-tool="editingTool"
      @updated="onToolUpdated"
    />
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
  max-width: 680px;
  max-height: 88vh;
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

.tab-bar {
  display: flex;
  gap: 4px;
  margin: 14px 0 4px;
  padding: 3px;
  background: var(--surface-muted);
  border-radius: var(--radius-md);
  width: fit-content;
}

.tab-btn {
  background: none;
  border: none;
  padding: 7px 16px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  cursor: pointer;
}
.tab-btn:hover {
  color: var(--text-primary);
}
.tab-btn.active {
  background: var(--surface);
  color: var(--accent-dark);
  box-shadow: var(--shadow-sm, 0 1px 2px rgba(0, 0, 0, 0.06));
}

.modal-hint {
  font-size: 13px;
  color: var(--text-secondary);
  margin: 10px 0 16px;
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

.my-uploads-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
</style>
