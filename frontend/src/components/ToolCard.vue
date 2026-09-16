<script setup>
import { ref } from 'vue'
import IconBase from './IconBase.vue'
import ToolNotesModal from './ToolNotesModal.vue'
import { useAuthStore } from '../stores/auth'

const props = defineProps({
  tool: { type: Object, required: true },
  showStatus: { type: Boolean, default: false }
})

defineEmits(['view'])

const auth = useAuthStore()

// Заметки администраторов прямо с карточки на главной - открываются поверх карточки,
// без перехода в модалку "Подробнее" (см. чип-счётчик заметок ниже с @click.stop).
const notesOpen = ref(false)

const stageMeta = {
  ACCESS: { label: 'Access', class: 'stage-access' },
  USAGE: { label: 'Usage', class: 'stage-usage' },
  HABIT: { label: 'Habit', class: 'stage-habit' },
  STANDARD: { label: 'Process Standard', class: 'stage-standard' }
}

const statusMeta = {
  PENDING: { label: 'На модерации', class: 'badge-warn' },
  PUBLISHED: { label: 'Опубликован', class: 'badge-accent' },
  REJECTED: { label: 'Отклонён', class: 'badge-danger' }
}

// На карточке показываем краткое описание (если автор его заполнил) - оно короче и
// читается быстрее, чем обрезанное полное. Полный текст всегда доступен по клику на
// карточку (модалка "Подробнее" - там же кнопка "Подробное описание"). Если краткое
// описание не заполнено - как и раньше, обрезаем полное до ~100 символов.
const DESCRIPTION_LIMIT = 100

function cardDescription(tool) {
  if (tool.shortDescription) return tool.shortDescription
  const text = tool.description || ''
  if (text.length <= DESCRIPTION_LIMIT) return text
  return `${text.slice(0, DESCRIPTION_LIMIT).trimEnd()}…`
}

function metricLabel(tool) {
  return tool.dau != null ? 'DAU' : 'скачиваний'
}

function metricValue(tool) {
  return tool.dau != null ? formatCompact(tool.dau) : formatCompact(tool.downloads)
}

function formatCompact(n) {
  if (n >= 1000) return `${(n / 1000).toFixed(n % 1000 === 0 ? 0 : 1)}k`
  return String(n)
}
</script>

<template>
  <div class="tool-card panel" @click="$emit('view', tool)">
    <div class="tool-left">
      <div class="tool-title">
        {{ tool.name }}
        <span class="stage-badge" :class="stageMeta[tool.stage]?.class">{{ stageMeta[tool.stage]?.label }}</span>
        <span v-if="tool.isTop" class="badge badge-warn"><IconBase name="star" :size="11" /> Топ</span>
        <span v-if="showStatus" class="badge" :class="statusMeta[tool.status]?.class">{{ statusMeta[tool.status]?.label }}</span>
      </div>
      <div class="tool-desc">
        <span class="tool-desc-text" :title="tool.description">{{ cardDescription(tool) }}</span>
        <span v-for="r in tool.roles" :key="r" class="tag">{{ r }}</span>
        <span v-if="tool.framework" class="tag">{{ tool.framework }}</span>
      </div>
      <div class="tool-meta">
        <span><IconBase name="user" :size="13" /> {{ tool.ownerName }}</span>
        <span class="views-chip" title="Просмотров"><IconBase name="eye" :size="13" /> {{ formatCompact(tool.views || 0) }}</span>
        <span v-if="tool.ratingsCount" class="rating-chip" title="Оценка пользователей">
          <IconBase name="star" :size="13" /> {{ tool.avgRating.toFixed(1) }}
        </span>
        <span
          v-if="auth.isAdmin"
          class="notes-chip"
          :class="{ 'notes-chip-active': tool.notesCount }"
          title="Заметки администраторов"
          @click.stop="notesOpen = true"
        >
          <IconBase name="note" :size="13" /> {{ tool.notesCount || 0 }}
        </span>
      </div>
    </div>

    <div class="tool-right">
      <div class="metric">
        <div class="metric-num">{{ metricValue(tool) }}</div>
        <div class="metric-lbl">{{ metricLabel(tool) }}</div>
      </div>
      <div class="metric">
        <div class="metric-num">{{ tool.efficiencyPct }}%</div>
        <div class="metric-lbl">эффективность</div>
      </div>
      <span class="open-arrow" tabindex="-1" aria-hidden="true">
        <IconBase name="arrowRight" :size="15" />
      </span>
    </div>
  </div>

  <!-- Вынесена за пределы .tool-card: иначе клики внутри модалки (поле ввода, кнопки
       заметок) всплывали бы до обработчика @click карточки и заодно открывали "Подробнее". -->
  <ToolNotesModal v-model="notesOpen" :tool="tool" />
</template>

<style scoped>
.tool-card {
  padding: 16px 20px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  transition: border-color 0.12s ease, box-shadow 0.12s ease;
  cursor: pointer;
}
.tool-card:hover {
  border-color: var(--border-strong);
  box-shadow: 0 2px 10px rgba(20, 24, 38, 0.06);
}

.tool-left {
  display: flex;
  flex-direction: column;
  gap: 7px;
  flex: 2 1 280px;
}

.tool-title {
  font-weight: 700;
  font-size: 15.5px;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
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

.tool-desc {
  font-size: 13px;
  color: var(--text-secondary);
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
}

.tool-meta {
  display: flex;
  gap: 16px;
  font-size: 12.5px;
  color: var(--text-muted);
}
.tool-meta span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.rating-chip {
  color: var(--warn, #d69b1a);
  font-weight: 600;
}

.notes-chip {
  cursor: pointer;
  border-radius: var(--radius-sm);
  padding: 2px 6px;
  margin: -2px -6px;
  transition: background 0.12s ease, color 0.12s ease;
}
.notes-chip:hover {
  background: var(--surface-muted);
  color: var(--text-primary);
}
.notes-chip-active {
  color: var(--info);
  font-weight: 600;
}

.tool-right {
  display: flex;
  align-items: center;
  gap: 20px;
  flex: 1 0 auto;
  justify-content: flex-end;
}

.metric {
  text-align: right;
}
.metric-num {
  font-weight: 700;
  font-size: 16px;
  color: var(--text-primary);
}
.metric-lbl {
  font-size: 10.5px;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.open-arrow {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 999px;
  border: 1px solid var(--border);
  color: var(--text-secondary);
  flex-shrink: 0;
  transition: border-color 0.12s ease, color 0.12s ease, background 0.12s ease;
}
.tool-card:hover .open-arrow {
  border-color: var(--accent-dark);
  color: var(--accent-dark);
  background: var(--accent-soft);
}

@media (max-width: 640px) {
  .tool-card { flex-direction: column; align-items: stretch; }
  /* .tool-left/.tool-right используют "flex: N M ЧИСЛОpx" как подсказку
     минимальной ШИРИНЫ для горизонтальной раскладки. Когда карточка
     становится колонкой, тот же base-размер трактуется как ВЫСОТА -
     блок с описанием принудительно растягивался на 280px в высоту,
     хотя реального контента там на ~90px, отсюда пустой промежуток
     перед строкой с цифрами. В колонке высота должна идти по контенту. */
  .tool-left { flex: none; }
  .tool-right { flex: none; justify-content: space-between; }
}
</style>
