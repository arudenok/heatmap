<script setup>
import { computed, onMounted, ref } from 'vue'
import api, { extractErrorMessage } from '../services/api'
import IconBase from './IconBase.vue'
import ToolNotesModal from './ToolNotesModal.vue'
import ToolDetailModal from './ToolDetailModal.vue'
import AddToolModal from './AddToolModal.vue'

const emit = defineEmits(['changed'])

const pending = ref([])
const loading = ref(true)
const error = ref('')
const busyId = ref(null)
// Фильтр "только с заметками" - чисто клиентский (см. тот же паттерн в бывшем AdminToolsPanel).
const notesOnly = ref(false)
const displayedPending = computed(() =>
  notesOnly.value ? pending.value.filter((t) => t.notesCount > 0) : pending.value
)
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

// Карточка "Подробнее" - та же модалка, что и в общем реестре (см. DashboardView/AdminToolsPanel).
// Клик по всей строке (кроме кнопок справа - см. @click.stop в шаблоне) открывает ToolDetailModal;
// в ней же доступны "Редактировать" и, для уже опубликованных инструментов, "Архивировать".
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

// Архивировать заявку, ожидающую модерации (PENDING), нельзя - кнопка в ToolDetailModal
// показывается только для уже опубликованных инструментов, так что этот обработчик сюда
// попадёт разве что при редком стечении обстоятельств. Оставляем для консистентности.
function onArchivedFromDetail(tool) {
  pending.value = pending.value.filter((t) => t.id !== tool.id)
  emit('changed', pending.value.length)
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

onMounted(() => {
  load()
  loadFilterOptions()
})
defineExpose({ refresh: load })
</script>

<template>
  <div class="panel admin-panel">
    <div class="admin-panel-header">
      <h3><IconBase name="inbox" :size="16" /> Заявки на модерации <span class="badge badge-warn">{{ displayedPending.length }}</span></h3>
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

    <p v-if="error" class="error-text">{{ error }}</p>

    <div v-if="loading" class="skeleton" style="height: 60px;"></div>

    <div v-else-if="!displayedPending.length" class="empty-state">
      <IconBase name="check" :size="24" /> {{ notesOnly ? 'Нет заявок с заметками' : 'Активных заявок нет' }}
    </div>

    <div v-else class="moderation-list">
      <div v-for="tool in displayedPending" :key="tool.id" class="moderation-row" @click="openDetail(tool)">
        <div class="moderation-info">
          <div class="moderation-title">{{ tool.name }}</div>
          <div class="moderation-desc">{{ tool.shortDescription || tool.description }}</div>
          <div class="moderation-tags">
            <span v-for="r in tool.roles" :key="'r-' + r" class="tag">{{ r }}</span>
            <span v-for="f in tool.framework" :key="'f-' + f" class="tag">{{ f }}</span>
            <span v-for="c in tool.constraints" :key="'c-' + c" class="tag">{{ c }}</span>
            <span class="tag">👤 {{ tool.ownerName }}</span>
          </div>
        </div>
        <div v-if="rejectingId !== tool.id" class="moderation-actions">
          <button class="btn btn-ghost btn-sm notes-action-btn" type="button" @click.stop="openNotes(tool)">
            <IconBase name="note" :size="13" /> Заметки
            <span v-if="tool.notesCount" class="badge badge-info">{{ tool.notesCount }}</span>
          </button>
          <button
            class="btn btn-outline btn-sm"
            type="button"
            :disabled="busyId === tool.id"
            @click.stop="startReject(tool)"
          >
            Отклонить
          </button>
          <button
            class="btn btn-primary btn-sm"
            type="button"
            :disabled="busyId === tool.id"
            @click.stop="approve(tool)"
          >
            Одобрить
          </button>
        </div>
        <div v-else class="reject-form" @click.stop>
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
    <ToolDetailModal
      v-model="detailOpen"
      :tool="detailTool"
      @edit="onEditFromDetail"
      @archived="onArchivedFromDetail"
    />
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
  margin-bottom: 16px;
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
  cursor: pointer;
  transition: border-color 0.12s ease, box-shadow 0.12s ease;
}
/* Клик по всей строке (кроме кнопок справа и формы отклонения - см. @click.stop) открывает
   ту же карточку "Подробнее", что и в общем реестре (см. openDetail/ToolDetailModal). */
.moderation-row:hover {
  border-color: var(--border-strong);
  box-shadow: 0 2px 10px rgba(20, 24, 38, 0.06);
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
