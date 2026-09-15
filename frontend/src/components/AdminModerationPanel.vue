<script setup>
import { onMounted, ref } from 'vue'
import api, { extractErrorMessage } from '../services/api'
import IconBase from './IconBase.vue'
import ToolNotesModal from './ToolNotesModal.vue'

const emit = defineEmits(['changed'])

const pending = ref([])
const loading = ref(true)
const error = ref('')
const busyId = ref(null)
// id заявки, для которой сейчас открыта форма ввода причины отклонения (null - ни для одной).
const rejectingId = ref(null)
const reasonText = ref('')
const reasonError = ref('')

// Заметки администраторов - отдельная модалка, открывается для конкретной заявки.
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
    const { data } = await api.get('/admin/tools/pending')
    pending.value = data
  } catch (e) {
    error.value = extractErrorMessage(e, 'Не удалось загрузить заявки на модерации')
  } finally {
    loading.value = false
  }
  // Сообщаем наверх (счётчик на вкладке "Модерация" в AdminView) актуальное количество.
  emit('changed', pending.value.length)
}

async function approve(tool) {
  busyId.value = tool.id
  try {
    await api.post(`/admin/tools/${tool.id}/approve`)
    pending.value = pending.value.filter((t) => t.id !== tool.id)
    emit('changed', pending.value.length)
  } catch (e) {
    error.value = extractErrorMessage(e, 'Не удалось обработать заявку')
  } finally {
    busyId.value = null
  }
}

// Отклонение требует причины - вместо мгновенного вызова API сначала раскрываем
// текстовое поле под заявкой, чтобы администратор мог её ввести.
function startReject(tool) {
  rejectingId.value = tool.id
  reasonText.value = ''
  reasonError.value = ''
}

function cancelReject() {
  rejectingId.value = null
  reasonText.value = ''
  reasonError.value = ''
}

async function confirmReject(tool) {
  const reason = reasonText.value.trim()
  if (!reason) {
    reasonError.value = 'Укажите причину отклонения'
    return
  }
  busyId.value = tool.id
  try {
    await api.post(`/admin/tools/${tool.id}/reject`, { reason })
    pending.value = pending.value.filter((t) => t.id !== tool.id)
    emit('changed', pending.value.length)
    rejectingId.value = null
  } catch (e) {
    error.value = extractErrorMessage(e, 'Не удалось обработать заявку')
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
      <h3><IconBase name="inbox" :size="16" /> Заявки на модерации <span class="badge badge-warn">{{ pending.length }}</span></h3>
      <button class="btn btn-ghost btn-sm" type="button" @click="load"><IconBase name="refresh" :size="13" /> Обновить</button>
    </div>

    <p v-if="error" class="error-text">{{ error }}</p>

    <div v-if="loading" class="skeleton" style="height: 60px;"></div>

    <div v-else-if="!pending.length" class="empty-state">
      <IconBase name="check" :size="24" /> Активных заявок нет
    </div>

    <div v-else class="moderation-list">
      <div v-for="tool in pending" :key="tool.id" class="moderation-row">
        <div class="moderation-info">
          <div class="moderation-title">{{ tool.name }}</div>
          <div class="moderation-desc">{{ tool.description }}</div>
          <div class="moderation-tags">
            <span v-for="r in tool.roles" :key="'r-' + r" class="tag">{{ r }}</span>
            <span class="tag">{{ tool.framework }}</span>
            <span v-for="s in tool.segments" :key="'s-' + s" class="tag">{{ s }}</span>
            <span class="tag">👤 {{ tool.ownerName }}</span>
          </div>
        </div>
        <div v-if="rejectingId !== tool.id" class="moderation-actions">
          <button class="btn btn-ghost btn-sm notes-action-btn" type="button" @click="openNotes(tool)">
            <IconBase name="note" :size="13" /> Заметки
            <span v-if="tool.notesCount" class="badge badge-info">{{ tool.notesCount }}</span>
          </button>
          <button
            class="btn btn-outline btn-sm"
            type="button"
            :disabled="busyId === tool.id"
            @click="startReject(tool)"
          >
            Отклонить
          </button>
          <button
            class="btn btn-primary btn-sm"
            type="button"
            :disabled="busyId === tool.id"
            @click="approve(tool)"
          >
            Одобрить
          </button>
        </div>
        <div v-else class="reject-form">
          <textarea
            v-model="reasonText"
            class="input reject-textarea"
            rows="2"
            placeholder="Причина отклонения - её увидит автор заявки"
            @input="reasonError = ''"
          ></textarea>
          <p v-if="reasonError" class="error-text">{{ reasonError }}</p>
          <div class="reject-form-actions">
            <button class="btn btn-ghost btn-sm" type="button" :disabled="busyId === tool.id" @click="cancelReject">
              Отмена
            </button>
            <button
              class="btn btn-danger btn-sm"
              type="button"
              :disabled="busyId === tool.id"
              @click="confirmReject(tool)"
            >
              Подтвердить отклонение
            </button>
          </div>
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
  margin-bottom: 16px;
}

.admin-panel-header h3 {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15.5px;
  margin: 0;
}

.moderation-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.moderation-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  flex-wrap: wrap;
}

.moderation-title {
  font-weight: 700;
  font-size: 14.5px;
}

.moderation-desc {
  font-size: 13px;
  color: var(--text-secondary);
  margin-top: 2px;
}

.moderation-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 8px;
}

.moderation-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

/* Ширина кнопки "Заметки" менялась в зависимости от наличия бейджа-счётчика, а блок
   кнопок прижат к правому краю строки - из-за этого "Отклонить"/"Одобрить" сдвигались
   влево-вправо от строки к строке. Фиксируем минимальную ширину под самый широкий
   вариант (с бейджем). */
.notes-action-btn {
  min-width: 148px;
}

.reject-form {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex-basis: 100%;
}

.reject-textarea {
  resize: vertical;
  min-height: 44px;
}

.reject-form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
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
