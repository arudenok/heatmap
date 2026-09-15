<script setup>
import { ref } from 'vue'
import api from '../services/api'
import { useAuthStore } from '../stores/auth'
import IconBase from './IconBase.vue'
import ToolNotesModal from './ToolNotesModal.vue'

const props = defineProps({
  tool: { type: Object, default: null }
})

const open = defineModel({ default: false })
const emit = defineEmits(['delete', 'downloaded'])
const auth = useAuthStore()

// Заметки администраторов - отдельная модалка поверх этой же карточки инструмента,
// открывается только администраторам (см. кнопку "Заметки" в modal-actions).
const notesOpen = ref(false)

const stageMeta = {
  ACCESS: { label: 'Access', class: 'stage-access' },
  USAGE: { label: 'Usage', class: 'stage-usage' },
  HABIT: { label: 'Habit', class: 'stage-habit' },
  STANDARD: { label: 'Process Standard', class: 'stage-standard' }
}

function close() {
  open.value = false
}

function onDelete() {
  emit('delete', props.tool)
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

      <p class="detail-desc">{{ tool.description }}</p>

      <div class="detail-tags">
        <span v-for="r in tool.roles" :key="'r-' + r" class="tag">{{ r }}</span>
        <span class="tag">{{ tool.framework }}</span>
        <span v-for="s in tool.segments" :key="'s-' + s" class="tag">{{ s }}</span>
        <span class="tag">{{ tool.sourceLabel }}</span>
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

      <div class="modal-actions">
        <div class="modal-actions-left">
          <button
            v-if="tool.canManage"
            type="button"
            class="btn btn-danger-ghost"
            @click="onDelete"
          >
            <IconBase name="trash" :size="14" /> Удалить
          </button>
          <button
            v-if="auth.isAdmin"
            type="button"
            class="btn btn-ghost"
            @click="notesOpen = true"
          >
            <IconBase name="note" :size="14" /> Заметки
            <span v-if="tool.notesCount" class="badge badge-info">{{ tool.notesCount }}</span>
          </button>
        </div>
        <div class="modal-actions-right">
          <button type="button" class="btn btn-primary" @click="onDownload">
            <IconBase name="download" :size="14" /> Скачать
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

.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 18px;
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

.modal-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 20px;
}

.modal-actions-left {
  display: flex;
  gap: 10px;
}

.modal-actions-right {
  display: flex;
  gap: 10px;
  margin-left: auto;
}

@media (max-width: 480px) {
  .detail-grid { grid-template-columns: 1fr; }
  .modal-actions { flex-wrap: wrap; }
  .modal-actions-left { width: 100%; }
  .modal-actions-left .btn { flex: 1; }
  .modal-actions-right { margin-left: 0; width: 100%; }
  .modal-actions-right .btn { flex: 1; }
}
</style>
