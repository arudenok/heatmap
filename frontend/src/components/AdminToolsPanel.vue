<script setup>
import { computed, onMounted, ref } from 'vue'
import api, { extractErrorMessage } from '../services/api'
import IconBase from './IconBase.vue'
import AddToolModal from './AddToolModal.vue'
import ToolNotesModal from './ToolNotesModal.vue'

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
      <div v-for="tool in displayedTools" :key="tool.id" class="tools-row">
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
        <div class="tools-actions">
          <label class="stage-label">Этап:</label>
          <select
            class="input stage-select"
            :value="tool.stage"
            :disabled="busyId === tool.id"
            @change="onStageChange(tool, $event.target.value)"
          >
            <option v-for="opt in STAGE_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </select>
          <button class="btn btn-ghost btn-sm" type="button" @click="openEditModal(tool)">
            <IconBase name="edit" :size="13" /> Редактировать
          </button>
          <button class="btn btn-ghost btn-sm notes-action-btn" type="button" @click="openNotes(tool)">
            <IconBase name="note" :size="13" /> Заметки
            <span v-if="tool.notesCount" class="badge badge-info">{{ tool.notesCount }}</span>
          </button>
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
</style>
