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
const search = ref('')
const filterOptions = ref({ roles: [], frameworks: [], constraints: [] })
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
    // Список ролей/фреймворков/подсказок для "Ограничения" не критичен для отображения - молча оставляем пустым.
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
// "Редактировать" (canManage у админа = true для любого инструмента, см. ToolService.toResponse)
// и, для уже опубликованных инструментов, "Архивировать".
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

// Архивирование теперь выполняется прямо из ToolDetailModal (кнопка "Архивировать") - сама
// модалка уже вызвала API и закрылась, здесь только убираем инструмент из списка (он
// больше не PUBLISHED, tab=ALL его не вернёт).
function onArchivedFromDetail(tool) {
  tools.value = tools.value.filter((t) => t.id !== tool.id)
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

    <p class="modal-hint">Этап зрелости, статус и метрики влияния меняются через "Редактировать" в карточке инструмента (открывается кликом по строке).</p>

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
            <span v-for="f in tool.framework" :key="f" class="tag">{{ f }}</span>
            <span class="tag">👤 {{ tool.ownerName }}</span>
          </div>
        </div>
        <div class="tools-actions">
          <button class="btn btn-ghost btn-sm notes-action-btn" type="button" @click.stop="openNotes(tool)">
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
    <ToolDetailModal
      v-model="detailOpen"
      :tool="detailTool"
      @edit="onEditFromDetail"
      @archived="onArchivedFromDetail"
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
