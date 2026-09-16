<script setup>
import { computed, onMounted, ref } from 'vue'
import api, { extractErrorMessage } from '../services/api'
import IconBase from './IconBase.vue'
import ToolNotesModal from './ToolNotesModal.vue'
import ToolDetailModal from './ToolDetailModal.vue'
import AddToolModal from './AddToolModal.vue'

const archived = ref([])
const loading = ref(true)
const error = ref('')
const busyId = ref(null)
// Фильтр "только с заметками" - чисто клиентский (см. тот же паттерн в бывшем AdminToolsPanel).
const notesOnly = ref(false)
const displayedArchived = computed(() =>
  notesOnly.value ? archived.value.filter((t) => t.notesCount > 0) : archived.value
)

// Заметки администраторов - отдельная модалка, открывается для конкретного инструмента
// (заметки остаются доступны и после архивации - см. ToolNoteService, статус не проверяется).
const notesOpen = ref(false)
const notesTool = ref(null)

function openNotes(tool) {
  notesTool.value = tool
  notesOpen.value = true
}

// Карточка "Подробнее" - та же модалка, что и в общем реестре (см. DashboardView/AdminToolsPanel).
// Кнопка "Архивировать" внутри неё для уже архивированных инструментов не показывается
// (см. ToolDetailModal - только для PUBLISHED), так что здесь она открывается только
// на просмотр/редактирование.
const filterOptions = ref({ roles: [], frameworks: [], constraints: [] })
const detailOpen = ref(false)
const detailTool = ref(null)
const editModalOpen = ref(false)
const editingTool = ref(null)

async function loadFilterOptions() {
  try {
    const { data } = await api.get('/tools/filter-options')
    filterOptions.value = data
  } catch {
    // Список ролей/фреймворков/подсказок для "Ограничения" не критичен для формы редактирования - молча оставляем пустым.
  }
}

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
  editingTool.value = tool
  editModalOpen.value = true
}

async function onToolUpdated() {
  editingTool.value = null
  await load()
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await api.get('/admin/tools/archived')
    archived.value = data
  } catch (e) {
    error.value = extractErrorMessage(e, 'Не удалось загрузить архив')
  } finally {
    loading.value = false
  }
}

// Восстановление всегда возвращает инструмент в "Опубликован" (архивировать можно только
// уже опубликованный - см. ToolService.archive), поэтому отдельного выбора статуса не нужно.
async function restore(tool) {
  busyId.value = tool.id
  try {
    await api.post(`/admin/tools/${tool.id}/restore`)
    archived.value = archived.value.filter((t) => t.id !== tool.id)
  } catch (e) {
    error.value = extractErrorMessage(e, 'Не удалось восстановить инструмент')
  } finally {
    busyId.value = null
  }
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
      <h3><IconBase name="archive" :size="16" /> Архив <span class="badge badge-info">{{ displayedArchived.length }}</span></h3>
      <div class="admin-panel-actions">
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

    <p class="modal-hint">
      Инструменты, скрытые из общего реестра администратором. Автор получает уведомление об архивации
      (с комментарием, если администратор его указал). Восстановление публикует инструмент снова.
    </p>

    <p v-if="error" class="error-text">{{ error }}</p>

    <div v-if="loading" class="skeleton" style="height: 60px;"></div>

    <div v-else-if="!displayedArchived.length" class="empty-state">
      <IconBase name="archive" :size="24" /> {{ notesOnly ? 'Нет архивных инструментов с заметками' : 'В архиве пусто' }}
    </div>

    <div v-else class="archive-list">
      <div v-for="tool in displayedArchived" :key="tool.id" class="archive-row" @click="openDetail(tool)">
        <div class="archive-info">
          <div class="archive-title">{{ tool.name }}</div>
          <div class="archive-tags">
            <span v-for="r in tool.roles" :key="'r-' + r" class="tag">{{ r }}</span>
            <span v-if="tool.framework" class="tag">{{ tool.framework }}</span>
            <span class="tag">👤 {{ tool.ownerName }}</span>
          </div>
        </div>
        <div class="archive-actions">
          <button class="btn btn-ghost btn-sm notes-action-btn" type="button" @click.stop="openNotes(tool)">
            <IconBase name="note" :size="13" /> Заметки
            <span v-if="tool.notesCount" class="badge badge-info">{{ tool.notesCount }}</span>
          </button>
          <button
            class="btn btn-primary btn-sm"
            type="button"
            :disabled="busyId === tool.id"
            @click.stop="restore(tool)"
          >
            <IconBase name="refresh" :size="13" /> Восстановить
          </button>
        </div>
      </div>
    </div>

    <ToolNotesModal v-model="notesOpen" :tool="notesTool" />
    <ToolDetailModal v-model="detailOpen" :tool="detailTool" @edit="onEditFromDetail" />
    <AddToolModal
      v-model="editModalOpen"
      :filter-options="filterOptions"
      :edit-tool="editingTool"
      @updated="onToolUpdated"
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

.admin-panel-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.admin-panel-header h3 {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15.5px;
  margin: 0;
}

.modal-hint {
  font-size: 13px;
  color: var(--text-secondary);
  margin: 0 0 16px;
}

.archive-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.archive-row {
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
/* Клик по всей строке (кроме кнопок справа - см. @click.stop) открывает ту же карточку
   "Подробнее", что и в общем реестре (см. openDetail/ToolDetailModal). */
.archive-row:hover {
  border-color: var(--border-strong);
  box-shadow: 0 2px 10px rgba(20, 24, 38, 0.06);
}

.archive-title {
  font-weight: 700;
  font-size: 14.5px;
}

.archive-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 8px;
}

.archive-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  flex-shrink: 0;
}

/* Та же логика, что и в AdminToolsPanel/AdminModerationPanel - фиксируем минимальную
   ширину кнопки "Заметки", чтобы бейдж со счётчиком не сдвигал соседние кнопки. */
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
