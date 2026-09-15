<script setup>
import { computed, ref, watch } from 'vue'
import api, { extractErrorMessage } from '../services/api'
import IconBase from './IconBase.vue'

const props = defineProps({
  tool: { type: Object, default: null }
})

const open = defineModel({ default: false })

const notes = ref([])
const loading = ref(false)
const error = ref('')

const newText = ref('')
const posting = ref(false)

// Заметка, которая сейчас редактируется (null - ни одна) - и её черновик текста отдельно
// от новой заметки, чтобы не путать оба поля ввода.
const editingId = ref(null)
const editText = ref('')
const editBusy = ref(false)

const canPost = computed(() => newText.value.trim().length > 0 && !posting.value)

async function load() {
  if (!props.tool) return
  loading.value = true
  error.value = ''
  try {
    const { data } = await api.get(`/admin/tools/${props.tool.id}/notes`)
    notes.value = data
    // tool - тот же реактивный объект, что лежит в списке у родителя (карточка/строка таблицы),
    // поэтому обновление счётчика здесь сразу отражается на бейдже кнопки "Заметки" снаружи.
    props.tool.notesCount = data.length
  } catch (e) {
    error.value = extractErrorMessage(e, 'Не удалось загрузить заметки')
  } finally {
    loading.value = false
  }
}

watch(
  () => [open.value, props.tool?.id],
  ([isOpen]) => {
    if (isOpen) {
      newText.value = ''
      editingId.value = null
      load()
    }
  }
)

function close() {
  open.value = false
}

async function addNote() {
  if (!canPost.value || !props.tool) return
  posting.value = true
  error.value = ''
  try {
    const { data } = await api.post(`/admin/tools/${props.tool.id}/notes`, { text: newText.value.trim() })
    notes.value.push(data)
    props.tool.notesCount = notes.value.length
    newText.value = ''
  } catch (e) {
    error.value = extractErrorMessage(e, 'Не удалось добавить заметку')
  } finally {
    posting.value = false
  }
}

function startEdit(note) {
  editingId.value = note.id
  editText.value = note.text
}

function cancelEdit() {
  editingId.value = null
  editText.value = ''
}

async function saveEdit(note) {
  const text = editText.value.trim()
  if (!text) return
  editBusy.value = true
  try {
    const { data } = await api.patch(`/admin/tools/notes/${note.id}`, { text })
    Object.assign(note, data)
    editingId.value = null
  } catch (e) {
    error.value = extractErrorMessage(e, 'Не удалось сохранить заметку')
  } finally {
    editBusy.value = false
  }
}

async function removeNote(note) {
  if (!confirm('Удалить эту заметку?')) return
  try {
    await api.delete(`/admin/tools/notes/${note.id}`)
    notes.value = notes.value.filter((n) => n.id !== note.id)
    props.tool.notesCount = notes.value.length
  } catch (e) {
    error.value = extractErrorMessage(e, 'Не удалось удалить заметку')
  }
}

function formatDate(value) {
  if (!value) return ''
  return new Date(value).toLocaleString('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}
</script>

<template>
  <div v-if="open && tool" class="modal-backdrop" @click.self="close">
    <div class="modal panel">
      <div class="modal-header">
        <h3><IconBase name="note" :size="17" /> Заметки · {{ tool.name }}</h3>
        <button class="icon-btn" type="button" @click="close"><IconBase name="x" :size="16" /></button>
      </div>
      <p class="modal-hint">
        Видны только администраторам. Каждый добавляет свои заметки - чужие не затираются,
        редактировать или удалить можно только собственную.
      </p>

      <p v-if="error" class="error-text">{{ error }}</p>

      <div v-if="loading" class="skeleton" style="height: 60px;"></div>

      <div v-else class="notes-list">
        <p v-if="!notes.length" class="empty-text">Заметок пока нет</p>
        <div v-for="note in notes" :key="note.id" class="note-row">
          <div class="note-head">
            <span class="note-author"><IconBase name="user" :size="12" /> {{ note.authorName }}</span>
            <span class="note-date">{{ formatDate(note.updatedAt) }}<span v-if="note.updatedAt !== note.createdAt"> (изменено)</span></span>
          </div>

          <template v-if="editingId === note.id">
            <textarea v-model="editText" class="input note-textarea" rows="2"></textarea>
            <div class="note-edit-actions">
              <button class="btn btn-ghost btn-sm" type="button" :disabled="editBusy" @click="cancelEdit">Отмена</button>
              <button class="btn btn-primary btn-sm" type="button" :disabled="editBusy || !editText.trim()" @click="saveEdit(note)">
                Сохранить
              </button>
            </div>
          </template>
          <template v-else>
            <p class="note-text">{{ note.text }}</p>
            <div v-if="note.canManage" class="note-actions">
              <button class="btn-icon-sm" type="button" title="Редактировать" @click="startEdit(note)">
                <IconBase name="edit" :size="12" />
              </button>
              <button class="btn-icon-sm btn-icon-danger" type="button" title="Удалить" @click="removeNote(note)">
                <IconBase name="trash" :size="12" />
              </button>
            </div>
          </template>
        </div>
      </div>

      <div class="note-form">
        <textarea
          v-model="newText"
          class="input note-textarea"
          rows="2"
          placeholder="Написать заметку для других администраторов…"
          @keydown.enter.meta="addNote"
          @keydown.enter.ctrl="addNote"
        ></textarea>
        <div class="note-form-actions">
          <button class="btn btn-primary btn-sm" type="button" :disabled="!canPost" @click="addNote">
            {{ posting ? 'Отправляем…' : 'Добавить заметку' }}
          </button>
        </div>
      </div>
    </div>
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
  max-width: 520px;
  max-height: 90vh;
  overflow-y: auto;
  padding: 24px 26px;
  display: flex;
  flex-direction: column;
}

.modal-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
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
  flex-shrink: 0;
}
.icon-btn:hover {
  background: var(--surface-muted);
  color: var(--text-primary);
}

.modal-hint {
  font-size: 12.5px;
  color: var(--text-secondary);
  margin: 0 0 16px;
}

.empty-text {
  font-size: 13.5px;
  color: var(--text-muted);
  padding: 10px 0;
}

.notes-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
  max-height: 320px;
  overflow-y: auto;
}

.note-row {
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface-muted);
}

.note-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 6px;
  font-size: 11.5px;
  color: var(--text-muted);
}

.note-author {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-weight: 600;
  color: var(--text-secondary);
}

.note-text {
  font-size: 13.5px;
  color: var(--text-primary);
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}

.note-actions {
  display: flex;
  gap: 4px;
  margin-top: 6px;
}

.btn-icon-sm {
  background: none;
  border: 1px solid var(--border);
  color: var(--text-muted);
  cursor: pointer;
  padding: 4px 6px;
  border-radius: var(--radius-sm);
  display: inline-flex;
}
.btn-icon-sm:hover {
  background: var(--surface);
  color: var(--text-primary);
}
.btn-icon-danger:hover {
  color: var(--danger);
  border-color: var(--danger-soft);
}

.note-edit-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}

.note-form {
  border-top: 1px solid var(--border);
  padding-top: 14px;
}

.note-textarea {
  resize: vertical;
  min-height: 44px;
}

.note-form-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}
</style>
