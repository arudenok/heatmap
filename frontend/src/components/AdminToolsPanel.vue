<script setup>
import { computed, onMounted, ref } from 'vue'
import api, { extractErrorMessage } from '../services/api'
import IconBase from './IconBase.vue'
import AddToolModal from './AddToolModal.vue'
import ToolNotesModal from './ToolNotesModal.vue'
import ToolDetailModal from './ToolDetailModal.vue'

const tools = ref([])
const loading = ref(true)
const error = ref('')
const busyId = ref(null)
const search = ref('')
const filterOptions = ref({ roles: [], frameworks: [], segments: [] })
// Фильтр "только с заметками" - чисто клиентский, не требует отдельного запроса
// (notesCount уже приходит администратору вместе с остальными полями инструмента).
const notesOnly = ref(false)
const displayedTools = computed(() =>
  notesOnly.value ? tools.value.filter((t) => t.notesCount > 0) : tools.value
)

// Модалка редактирования (переиспользуем AddToolModal в режиме editTool) - админ
// может отредактировать любой инструмент, независимо от его статуса и автора.
const modalOpen = ref(false)
const editingTool = ref(null)

// Заметки администраторов - отдельная модалка, открывается для конкретного инструмента.
const notesOpen = ref(false)
const notesTool = ref(null)

function openNotes(tool) {
  notesTool.value = tool
  notesOpen.value = true
}

// Архивирование - как отклонение заявки (см. AdminModerationPanel), только комментарий
// необязателен: администратор может архивировать инструмент и без пояснения (ArchiveToolRequest).
const archivingId = ref(null)
const archiveReasonText = ref('')

function startArchive(tool) {
  archivingId.value = tool.id
  archiveReasonText.value = ''
}

function cancelArchive() {
  archivingId.value = null
  archiveReasonText.value = ''
}

async function confirmArchive(tool) {
  busyId.value = tool.id
  try {
    await api.post(`/admin/tools/${tool.id}/archive`, { reason: archiveReasonText.value.trim() || null })
    // Архивированный инструмент больше не PUBLISHED - список (tab=ALL) его и не вернёт,
    // поэтому проще сразу убрать строку локально, чем перезапрашивать весь список.
    tools.value = tools.value.filter((t) => t.id !== tool.id)
    archivingId.value = null
  } catch (e) {
    error.value = extractErrorMessage(e, 'Не удалось архивировать инструмент')
  } finally {
    busyId.value = null
  }
}

const STAGE_OPTIONS = [
  { value: 'ACCESS', label: 'Access' },
  { value: 'USAGE', label: 'Usage' },
  { value: 'HABIT', label: 'Habit' },
  { value: 'STANDARD', label: 'Process Standard' }
]

async function load() {
  loading.value = true
  error.value = ''
  try {
    // tab=ALL возвращает все опубликованные инструменты без фильтра по этапу.
    const { data } = await api.get('/tools', {
      params: { tab: 'ALL', search: search.value || undefined, sort: 'CREATED_AT' }
    })
    tools.value = data
  } catch (e) {
    error.value = extractErrorMessage(e, 'Не удалось загрузить список инструментов')
  } finally {
    loading.value = false
  }
}

async function loadFilterOptions() {
  try {
    const { data } = await api.get('/tools/filter-options')
    filterOptions.value = data
  } catch {
    // Список ролей/фреймворков/сегментов не критичен для отображения - молча оставляем пустым.
  }
}

function openEditModal(tool) {
  editingTool.value = tool
  modalOpen.value = true
}

async function onToolUpdated() {
  editingTool.value = null
  await load()
}

// Карточка "Подробнее" - та же модалка, что и в общем реестре (см. DashboardView/MyDownloadsModal).
// Клик по всей строке (кроме селекта этапа и кнопок справа - см. @click.stop в шаблоне)
// засчитывает просмотр и открывает ToolDetailModal; администратору в ней доступны
// "Редактировать" и "Удалить" (canManage у админа = true для любого инструмента, см. ToolService.toResponse).
const detailOpen = ref(false)
const detailTool = ref(null)

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

function onEditFromDetail(tool) {
  detailOpen.value = false
  openEditModal(tool)
}

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

async function onStageChange(tool, newStage) {
  if (newStage === tool.stage) return
  busyId.value = tool.id
  const prevStage = tool.stage
  try {
    const { data } = await api.patch(`/tools/${tool.id}`, { stage: newStage })
    Object.assign(tool, data)
  } catch (e) {
    tool.stage = prevStage
    error.value = extractErrorMessage(e, 'Не удалось изменить этап инструмента')
  } finally {
    busyId.value = null
  }
}

let debounceHandle = null
function onSearchInput() {
  clearTimeout(debounceHandle)
  debounceHandle = setTimeout(load, 250)
}

onMounted(() => {
  load()
  loadFilterOptions()
})
defineExpose({ refresh: load })
</script>

<template>
  <div class="panel admin-panel">
    <div class="admin-panel-header">
      <h3><IconBase name="layers" :size="16" /> Инструменты <span class="badge badge-info">{{ displayedTools.length }}</span></h3>
      <div class="admin-panel-actions">
        <input
          v-model="search"
          class="input search-input"
          type="text"
          placeholder="Поиск по названию…"
          @input="onSearchInput"
        />
        <button
          type="button"
          class="btn btn-sm"
          :class="notesOnly ? 'btn-primary' : 'btn-ghost'"
          @click="notesOnly = !notesOnly"
        >
          <IconBase name="note" :size="13" /> Только с заметками
        </button>
        <button class="btn btn-ghost btn-sm" type="button" @click="load"><IconBase name="refresh" :size="13" /> Обновить</button>
      </div>
    </div>

    <p class="modal-hint">Здесь можно вручную изменить этап зрелости (Access → Usage → Habit → Process Standard) для любого опубликованного инструмента.</p>

    <p v-if="error" class="error-text">{{ error }}</p>

    <div v-if="loading" class="skeleton" style="height: 60px;"></div>

    <div v-else-if="!displayedTools.length" class="empty-state">
      <IconBase name="inbox" :size="24" /> Инструменты не найдены
    </div>

    <div v-else class="tools-list">
      <div
        v-for="tool in displayedTools"
        :key="tool.id"
        class="tools-row"
        @click="openDetail(tool)"
      >
        <div class="tools-info">
          <div class="tools-title">
            {{ tool.name }}
            <span v-if="tool.isTop" class="badge badge-warn"><IconBase name="star" :size="11" /> Топ</span>
          </div>
          <div class="tools-tags">
            <span v-for="r in tool.roles" :key="r" class="tag">{{ r }}</span>
            <span class="tag">{{ tool.framework }}</span>
            <span class="tag">👤 {{ tool.ownerName }}</span>
          </div>
        </div>
        <div v-if="archivingId !== tool.id" class="tools-actions">
          <label class="stage-label">Этап:</label>
          <select
            class="input stage-select"
            :value="tool.stage"
            :disabled="busyId === tool.id"
            @click.stop
            @change="onStageChange(tool, $event.target.value)"
          >
            <option v-for="opt in STAGE_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </select>
          <button class="btn btn-ghost btn-sm notes-action-btn" type="button" @click.stop="openNotes(tool)">
            <IconBase name="note" :size="13" /> Заметки
            <span v-if="tool.notesCount" class="badge badge-info">{{ tool.notesCount }}</span>
          </button>
          <button
            class="btn btn-outline btn-sm"
            type="button"
            :disabled="busyId === tool.id"
            @click.stop="startArchive(tool)"
          >
            <IconBase name="archive" :size="13" /> Архивировать
          </button>
        </div>
        <div v-else class="archive-form" @click.stop>
          <textarea
            v-model="archiveReasonText"
            class="input archive-textarea"
            rows="2"
            placeholder="Комментарий для автора (необязательно) - почему инструмент архивирован"
          ></textarea>
          <div class="archive-form-actions">
            <button class="btn btn-ghost btn-sm" type="button" :disabled="busyId === tool.id" @click="cancelArchive">
              Отмена
            </button>
            <button
              class="btn btn-danger btn-sm"
              type="button"
              :disabled="busyId === tool.id"
              @click="confirmArchive(tool)"
            >
              Подтвердить архивацию
            </button>
          </div>
        </div>
      </div>
    </div>

    <AddToolModal
      v-model="modalOpen"
      :filter-options="filterOptions"
      :edit-tool="editingTool"
      @updated="onToolUpdated"
    />
    <ToolNotesModal v-model="notesOpen" :tool="notesTool" />
    <ToolDetailModal
      v-model="detailOpen"
      :tool="detailTool"
      @edit="onEditFromDetail"
      @delete="onDeleteFromDetail"
    />
  </div>
</template>

<style scoped>
.admin-panel {
  padding: 20px 22px;
}

.admin-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.admin-panel-header h3 {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15.5px;
  margin: 0;
}

.admin-panel-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.search-input {
  width: 220px;
  flex: 1 1 160px;
}

/* .input (поиск) и .btn-sm (кнопка) имеют разную высоту по умолчанию -
   в одной строке кнопка выглядела заметно мельче поля поиска. */
.admin-panel-actions .btn {
  height: 39px;
}

.modal-hint {
  font-size: 13px;
  color: var(--text-secondary);
  margin: 0 0 16px;
}

.tools-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.tools-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  flex-wrap: wrap;
  cursor: pointer;
  transition: border-color 0.12s ease, box-shadow 0.12s ease;
}
/* Вся строка (кроме селекта этапа и кнопок справа - см. @click.stop на них) кликабельна и
   открывает ту же карточку "Подробнее", что и в общем реестре (см. openDetail/ToolDetailModal
   и аналогичный паттерн в ToolCard.vue). "Редактировать" теперь доступен только из модалки. */
.tools-row:hover {
  border-color: var(--border-strong);
  box-shadow: 0 2px 10px rgba(20, 24, 38, 0.06);
}

.tools-title {
  font-weight: 700;
  font-size: 14.5px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.tools-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 8px;
}

.tools-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  flex-shrink: 0;
}

.stage-label {
  font-size: 12.5px;
  color: var(--text-muted);
  font-weight: 600;
}

.stage-select {
  /* Раньше была "width: auto" - ширина селекта зависела от текста текущего этапа
     ("Access" короче, чем "Process Standard"), а весь блок с кнопками справа от
     него прижат к правому краю строки (space-between в .tools-row). Из-за этого
     у каждой строки "Этап:"/селект/кнопки начинались с разного X - список выглядел
     "рваным". Фиксированная ширина устраняет эту зависимость. */
  width: 160px;
}

/* Кнопка "Заметки" тоже меняла ширину в зависимости от того, есть ли бейдж со
   счётчиком - по той же причине (прижатый к правому краю блок) это тоже сдвигало
   всё, что стоит левее. Фиксируем минимальную ширину под самый широкий вариант
   (с бейджем), контент кнопки центрируется внутри неё как обычно. */
.notes-action-btn {
  min-width: 148px;
}

.empty-state {
  display: flex;
  align-items: center;
  gap: 8px;
  justify-content: center;
  padding: 30px;
  color: var(--text-muted);
  font-size: 14px;
}

.archive-form {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex-basis: 100%;
}

.archive-textarea {
  resize: vertical;
  min-height: 44px;
}

.archive-form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
