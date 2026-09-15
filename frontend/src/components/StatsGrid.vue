<script setup>
import IconBase from './IconBase.vue'

const props = defineProps({
  stats: { type: Object, default: null },
  counts: { type: Object, default: null },
  loading: { type: Boolean, default: false }
})

// Карточки одновременно служат навигацией по вкладкам реестра - отдельная строка вкладок больше не нужна.
// Данные по этапам зрелости (top/access/usage/habit/standard) приходят из /tools/counts,
// а среднее по эффективности и прирост за неделю - из /tools/stats.
const cards = [
  { key: 'total', icon: 'layers', label: 'Всего инструментов', tone: 'info', value: (s, c) => c?.total, sub: (s) => `+${s?.newThisWeek ?? 0} за неделю`, tab: 'ALL' },
  { key: 'top', icon: 'trophy', label: 'Топ решений банка', tone: 'gold', value: (s, c) => c?.top, sub: () => 'проверенные фавориты', tab: 'TOP' },
  { key: 'access', icon: 'spark', label: 'Access · новинки', tone: 'accent', value: (s, c) => c?.access, sub: () => 'апробация', tab: 'ACCESS' },
  { key: 'usage', icon: 'trend', label: 'Usage · адаптированы', tone: 'success', value: (s, c) => c?.usage, sub: () => 'активно используются', tab: 'USAGE' },
  { key: 'habit', icon: 'flame', label: 'Habit', tone: 'fire', value: (s, c) => c?.habit, sub: () => 'вошли в привычку', tab: 'HABIT' },
  { key: 'standard', icon: 'star', label: 'Process Standard', tone: 'warn', value: (s, c) => c?.standard, sub: () => 'эталонные решения', tab: 'STANDARD' },
  { key: 'avgEfficiency', icon: 'gauge', label: 'Средняя эффективность', tone: 'danger', value: (s) => s?.avgEfficiency, sub: () => 'по всем инструментам', suffix: '%', tab: null }
]

const activeTab = defineModel({ default: 'ALL' })

function onCardClick(card) {
  if (card.tab) activeTab.value = card.tab
}
</script>

<template>
  <div class="stats-grid">
    <div
      v-for="card in cards"
      :key="card.key"
      class="stat-card panel"
      :class="[`tone-${card.tone}`, { clickable: card.tab, active: card.tab && card.tab === activeTab }]"
      @click="onCardClick(card)"
    >
      <div class="stat-icon"><IconBase :name="card.icon" :size="14" /></div>
      <div class="stat-label">{{ card.label }}</div>
      <div v-if="loading || !stats || !counts" class="skeleton" style="height: 30px; width: 60%; margin-top: 6px;"></div>
      <template v-else>
        <div class="stat-value">{{ card.value(stats, counts) }}{{ card.suffix || '' }}</div>
        <div class="stat-sub">{{ card.sub(stats, counts) }}</div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.stats-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 10px;
  margin-bottom: 24px;
}

.stat-card {
  padding: 12px 13px;
  position: relative;
  transition: border-color 0.12s ease, box-shadow 0.12s ease, background 0.12s ease;
}

.stat-card.clickable {
  cursor: pointer;
}
.stat-card.clickable:hover {
  border-color: var(--border-strong);
  box-shadow: 0 2px 10px rgba(20, 24, 38, 0.06);
}
.stat-card.active {
  border-color: var(--accent);
  box-shadow: 0 0 0 1px var(--accent);
  background: var(--accent-soft);
}

.stat-icon {
  width: 24px;
  height: 24px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 7px;
}

.tone-info .stat-icon { background: var(--info-soft); color: var(--info); }
.tone-accent .stat-icon { background: var(--accent-soft); color: var(--accent-dark); }
.tone-success .stat-icon { background: #e4f6ea; color: #1e8a4c; }
.tone-warn .stat-icon { background: var(--warn-soft); color: var(--warn); }
.tone-danger .stat-icon { background: var(--danger-soft); color: var(--danger); }
.tone-gold .stat-icon { background: #fdf1d6; color: #92700c; }
.tone-fire .stat-icon { background: #fde3d3; color: #c2530f; }

.stat-label {
  font-size: 11.5px;
  font-weight: 500;
  color: var(--text-secondary);
  line-height: 1.25;
}

.stat-value {
  font-size: 19px;
  font-weight: 700;
  color: var(--text-primary);
  margin-top: 1px;
}

.stat-sub {
  font-size: 10.5px;
  color: var(--text-muted);
  margin-top: 1px;
}

@media (max-width: 1200px) {
  .stats-grid { grid-template-columns: repeat(4, 1fr); }
}
@media (max-width: 720px) {
  .stats-grid { grid-template-columns: repeat(3, 1fr); }
}
@media (max-width: 480px) {
  .stats-grid { grid-template-columns: 1fr 1fr; }
}
</style>
