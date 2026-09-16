<script setup>
import { onMounted, ref } from 'vue'
import api, { extractErrorMessage } from '../services/api'
import IconBase from './IconBase.vue'
import ToolNotesModal from './ToolNotesModal.vue'

const archived = ref([])
const loading = ref(true)
const error = ref('')
const busyId = ref(null)

// Заметки администраторов - отдельная модалка, открывается для конкретного инструмента
// (заметки остаются доступны и после архивации - см. ToolNoteService, статус не проверяется).
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

onMounted(load)
defineExpose({ refresh: load })
</script>

<template>
  <div class="panel admin-panel">
    <div class="admin-panel-header">
      <h3><IconBase name="archive" :size="16" /> Архив <span class="badge badge-info">{{ archived.length }}</span></h3>
      <button class="btn btn-ghost btn-sm" type="button" @click="load"><IconBase name="refresh" :size="13" /> Обновить</button>
    </div>

    <p class="modal-hint">
      Инструменты, скрытые из общего реестра администратором. Автор получает уведомление об архивации
      (с комментарием, если администратор его указал). Восстановление публикует инструмент снова.
    </p>

    <p v-if="error" class="error-text">{{ error }}</p>

    <div v-if="loading" class="skeleton" style="height: 60px;"></div>

    <div v-else-if="!archived.length" class="empty-state">
      <IconBase name="archive" :size="24" /> В архиве пусто
    </div>

    <div v-else class="archive-list">
      <div v-for="tool in archived" :key="tool.id" class="archive-row">
        <div class="archive-info">
          <div class="archive-title">{{ tool.name }}</div>
          <div class="archive-tags">
            <span v-for="r in tool.roles" :key="'r-' + r" class="tag">{{ r }}</span>
            <span class="tag">{{ tool.framework }}</span>
            <span class="tag">👤 {{ tool.ownerName }}</span>
          </div>
        </div>
        <div class="archive-actions">
          <button class="btn btn-ghost btn-sm notes-action-btn" type="button" @click="openNotes(tool)">
            <IconBase name="note" :size="13" /> Заметки
            <span v-if="tool.notesCount" class="badge badge-info">{{ tool.notesCount }}</span>
          </button>
          <button
            class="btn btn-primary btn-sm"
            type="button"
            :disabled="busyId === tool.id"
            @click="restore(tool)"
          >
            <IconBase name="refresh" :size="13" /> Восстановить
          </button>
        </div>
      </div>
    </div>

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
  margin-bottom: 10px;
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
