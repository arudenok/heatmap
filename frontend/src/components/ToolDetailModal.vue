<script setup>
import { ref, watch } from 'vue'
import api, { extractErrorMessage } from '../services/api'
import { useAuthStore } from '../stores/auth'
import IconBase from './IconBase.vue'
import ToolNotesModal from './ToolNotesModal.vue'

const props = defineProps({
  tool: { type: Object, default: null }
})

const open = defineModel({ default: false })
const emit = defineEmits(['archived', 'downloaded', 'edit'])
const auth = useAuthStore()

// Заметки администраторов - отдельная модалка поверх этой же карточки инструмента,
// открывается только администраторам (см. кнопку "Заметки" справа над разделителем).
const notesOpen = ref(false)

// Краткое описание показывается по умолчанию, полное - по клику "Подробное описание"
// (см. detail-desc-full в шаблоне). Если краткое описание не заполнено - показываем сразу
// полное и кнопку-переключатель не выводим (скрывать нечего).
const fullDescOpen = ref(false)

// Архивирование - доступно только администратору и только для опубликованного инструмента
// (см. ToolService.archive). Раньше здесь была кнопка "Удалить" - теперь для скрытия
// инструмента из общего реестра используется архивация; прямое удаление осталось в других
// местах (кнопка "Отозвать" в виджете на главной, "Удалить" во вкладке "Загруженные").
const archiving = ref(false)
const archiveReasonText = ref('')
const archiveBusy = ref(false)
const archiveError = ref('')

const stageMeta = {
  ACCESS: { label: 'Access', class: 'stage-access' },
  USAGE: { label: 'Usage', class: 'stage-usage' },
  HABIT: { label: 'Habit', class: 'stage-habit' },
  STANDARD: { label: 'Process Standard', class: 'stage-standard' }
}

function close() {
  open.value = false
}

// Сбрасываем форму архивации при каждом закрытии модалки - иначе при повторном открытии
// для другого инструмента могла остаться раскрытой форма с прошлым комментарием.
watch(open, (value) => {
  if (!value) {
    archiving.value = false
    archiveReasonText.value = ''
    archiveError.value = ''
    fullDescOpen.value = false
  }
})

// Правка своего инструмента доступна из общего реестра (не только из виджета "Мои инструменты
// на модерации" - тот больше не показывает опубликованные). Правка от автора (не администратора)
// уже опубликованного или отклонённого инструмента отправит его на повторную модерацию (см. ToolService.update).
function onEdit() {
  emit('edit', props.tool)
}

function startArchive() {
  archiving.value = true
  archiveReasonText.value = ''
  archiveError.value = ''
}

function cancelArchive() {
  archiving.value = false
  archiveReasonText.value = ''
  archiveError.value = ''
}

async function confirmArchive() {
  archiveBusy.value = true
  archiveError.value = ''
  try {
    await api.post(`/admin/tools/${props.tool.id}/archive`, { reason: archiveReasonText.value.trim() || null })
    archiving.value = false
    emit('archived', props.tool)
    close()
  } catch (e) {
    archiveError.value = extractErrorMessage(e, 'Не удалось архивировать инструмент')
  } finally {
    archiveBusy.value = false
  }
}

// Скачивание засчитывается по клику: увеличиваем счётчик, открываем ссылку на источник
// и предлагаем пользователю оценить инструмент по 5-балльной шкале.
//
// Важно: window.open() нужно вызывать СИНХРОННО в обработчике клика, до await -
// иначе браузер считает это не прямым ответом на действие пользователя и блокирует
// всплывающее окно как попап. Поэтому сразу открываем пустую вкладку, а после
// ответа сервера просто переводим её на нужный адрес.
async function onDownload() {
  let popup = null
  if (props.tool.sourceLabel) {
    popup = window.open('about:blank', '_blank', 'noopener')
  }
  try {
    const { data } = await api.post(`/tools/${props.tool.id}/download`)
    Object.assign(props.tool, data)
  } catch {
    // счётчик скачиваний не критичен - всё равно открываем ссылку на источник
  } finally {
    if (props.tool.sourceLabel) {
      if (popup) {
        popup.location.href = props.tool.sourceLabel
      } else {
        // popup заблокирован ещё на этапе about:blank (редкий случай) - пробуем открыть напрямую
        window.open(props.tool.sourceLabel, '_blank', 'noopener')
      }
    }
    emit('downloaded', props.tool)
  }
}

function formatDate(value) {
  if (!value) return '—'
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
  <div v-if="open && tool && !notesOpen" class="modal-backdrop" @click.self="close">
    <div class="modal panel">
      <div class="modal-header">
        <h3>
          {{ tool.name }}
          <span class="stage-badge" :class="stageMeta[tool.stage]?.class">{{ stageMeta[tool.stage]?.label }}</span>
        </h3>
        <button class="icon-btn" type="button" @click="close"><IconBase name="x" :size="16" /></button>
      </div>

      <!-- Краткое описание - основной текст на карточке; полное всегда доступно по клику
           (если краткое не заполнено - показываем сразу полное, скрывать нечего). -->
      <p class="detail-desc">{{ tool.shortDescription || tool.description }}</p>
      <button
        v-if="tool.shortDescription && tool.shortDescription !== tool.description"
        type="button"
        class="full-desc-toggle"
        @click="fullDescOpen = !fullDescOpen"
      >
        {{ fullDescOpen ? 'Скрыть подробное описание' : 'Подробное описание' }}
        <IconBase :name="fullDescOpen ? 'chevronUp' : 'chevronDown'" :size="12" />
      </button>
      <p v-if="fullDescOpen" class="detail-desc detail-desc-full">{{ tool.description }}</p>

      <div class="detail-tags">
        <span v-for="r in tool.roles" :key="'r-' + r" class="tag">{{ r }}</span>
        <span v-if="tool.framework" class="tag">{{ tool.framework }}</span>
        <span v-if="tool.constraints" class="tag">{{ tool.constraints }}</span>
        <span class="tag">{{ tool.sourceLabel }}</span>
      </div>

      <div v-if="auth.isAdmin" class="detail-notes-row">
        <button type="button" class="btn btn-notes-highlight" @click="notesOpen = true">
          <IconBase name="note" :size="14" /> Заметки
          <span v-if="tool.notesCount" class="badge badge-info">{{ tool.notesCount }}</span>
        </button>
      </div>

      <div class="detail-grid">
        <div class="detail-item">
          <div class="detail-label">Автор</div>
          <div class="detail-value"><IconBase name="user" :size="13" /> {{ tool.ownerName }}</div>
        </div>
        <div class="detail-item">
          <div class="detail-label">Просмотров</div>
          <div class="detail-value"><IconBase name="eye" :size="13" /> {{ tool.views ?? 0 }}</div>
        </div>
        <div class="detail-item">
          <div class="detail-label">{{ tool.dau != null ? 'DAU' : 'Скачиваний' }}</div>
          <div class="detail-value">{{ tool.dau ?? tool.downloads }}</div>
        </div>
        <div class="detail-item">
          <div class="detail-label">Эффективность</div>
          <div class="detail-value">{{ tool.efficiencyPct }}%</div>
        </div>
        <div class="detail-item">
          <div class="detail-label">Оценка пользователей</div>
          <div class="detail-value">
            <IconBase name="star" :size="13" />
            {{ tool.ratingsCount ? tool.avgRating.toFixed(1) : '—' }}
            <span v-if="tool.ratingsCount" class="detail-value-sub">({{ tool.ratingsCount }})</span>
          </div>
        </div>
        <div class="detail-item">
          <div class="detail-label">Добавлен</div>
          <div class="detail-value">{{ formatDate(tool.createdAt) }}</div>
        </div>
        <div class="detail-item">
          <div class="detail-label">Обновлён</div>
          <div class="detail-value">{{ formatDate(tool.updatedAt) }}</div>
        </div>
      </div>

      <div v-if="!archiving" class="modal-actions">
        <div class="modal-actions-left">
          <button
            v-if="tool.canManage"
            type="button"
            class="btn btn-ghost"
            @click="onEdit"
          >
            <IconBase name="edit" :size="14" /> Редактировать
          </button>
          <button
            v-if="auth.isAdmin && tool.status === 'PUBLISHED'"
            type="button"
            class="btn btn-outline"
            @click="startArchive"
          >
            <IconBase name="archive" :size="14" /> Архивировать
          </button>
        </div>
        <div class="modal-actions-right">
          <button type="button" class="btn btn-primary" @click="onDownload">
            <IconBase name="download" :size="14" /> Скачать
          </button>
        </div>
      </div>

      <div v-else class="archive-form">
        <textarea
          v-model="archiveReasonText"
          class="input archive-textarea"
          rows="2"
          placeholder="Комментарий для автора (необязательно) - почему инструмент архивирован"
        ></textarea>
        <p v-if="archiveError" class="error-text">{{ archiveError }}</p>
        <div class="archive-form-actions">
          <button class="btn btn-ghost btn-sm" type="button" :disabled="archiveBusy" @click="cancelArchive">
            Отмена
          </button>
          <button class="btn btn-danger btn-sm" type="button" :disabled="archiveBusy" @click="confirmArchive">
            Подтвердить архивацию
          </button>
        </div>
      </div>
    </div>
  </div>

  <ToolNotesModal v-model="notesOpen" :tool="tool" />
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
  max-width: 560px;
  max-height: 90vh;
  overflow-y: auto;
  padding: 24px 26px;
}

.modal-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.modal-header h3 {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  font-size: 17px;
  margin: 0;
}

.stage-badge {
  font-size: 11px;
  font-weight: 700;
  padding: 2px 11px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  background: var(--surface-muted);
  color: var(--text-secondary);
}
.stage-access { background: var(--info-soft); color: var(--info); border-color: transparent; }
.stage-usage { background: var(--accent-soft); color: var(--accent-dark); border-color: transparent; }
.stage-habit { background: var(--warn-soft); color: var(--warn); border-color: transparent; }
.stage-standard { background: #efe8fb; color: #6c3fc9; border-color: transparent; }

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

.detail-desc {
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.5;
  margin: 0 0 14px;
}

.full-desc-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin: -8px 0 12px;
  padding: 0;
  border: none;
  background: none;
  color: var(--accent-dark);
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
}
.full-desc-toggle:hover {
  text-decoration: underline;
}

.detail-desc-full {
  margin-top: -6px;
}

.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

/* Кнопка "Заметки" вынесена сюда, отдельной строкой справа прямо над разделителем
   (border-top у .detail-grid) - её переместили из общего ряда действий внизу модалки
   и выделили, чтобы она была заметнее. */
.detail-notes-row {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 14px;
}

.btn-notes-highlight {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: var(--radius-sm);
  border: 1px solid transparent;
  background: var(--accent-soft);
  color: var(--accent-dark);
  font-weight: 700;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.12s ease, color 0.12s ease;
}
.btn-notes-highlight:hover {
  background: var(--accent);
  color: #ffffff;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--border);
}

.detail-label {
  font-size: 11px;
  font-weight: 700;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.3px;
  margin-bottom: 4px;
}

.detail-value {
  font-size: 13.5px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
}

.detail-value-sub {
  font-weight: 400;
  color: var(--text-muted);
  font-size: 12px;
}

/* flex-wrap обязателен и вне мобильного брейкпоинта: слева до двух кнопок
   (Редактировать/Архивировать) на узких экранах могли продавить "Скачать" справа
   за пределы модалки (у .modal нет overflow-x, поэтому лишнее не обрезалось,
   а вылезало за рамку). */
.modal-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 20px;
  flex-wrap: wrap;
}

.modal-actions-left {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.modal-actions-right {
  display: flex;
  gap: 10px;
  margin-left: auto;
}

/* Форма архивации (комментарий + подтверждение) заменяет собой .modal-actions на время
   заполнения - тот же паттерн, что и в админ-панелях со списками инструментов. */
.archive-form {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 20px;
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

@media (max-width: 480px) {
  .detail-grid { grid-template-columns: 1fr; }
  .modal-actions-left { width: 100%; }
  .modal-actions-left .btn { flex: 1; }
  .modal-actions-right { margin-left: 0; width: 100%; }
  .modal-actions-right .btn { flex: 1; }
}
</style>
