<script setup>
import IconBase from './IconBase.vue'

const props = defineProps({
  stats: { type: Object, default: null },
  loading: { type: Boolean, default: false }
})

// Карточки одновременно служат навигацией по вкладкам реестра - отдельная строка вкладок больше не нужна.
const cards = [
  { key: 'totalTools', icon: 'layers', label: 'Всего инструментов', tone: 'info', sub: (s) => `+${s.newThisWeek} за неделю`, tab: 'ALL' },
  { key: 'accessCount', icon: 'spark', label: 'Access · новинки', tone: 'accent', sub: () => 'апробация', tab: 'ACCESS' },
  { key: 'usageCount', icon: 'trend', label: 'Usage · адаптированы', tone: 'success', sub: () => 'активно используются', tab: 'USAGE' },
  { key: 'standardCount', icon: 'star', label: 'Process Standard', tone: 'warn', sub: () => 'эталонные решения', tab: 'STANDARD' },
  { key: 'avgEfficiency', icon: 'gauge', label: 'Средняя эффективность', tone: 'danger', sub: () => 'по всем инструментам', suffix: '%', tab: null }
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
      <div class="stat-icon"><IconBase :name="card.icon" :size="17" /></div>
      <div class="stat-label">{{ card.label }}</div>
      <div v-if="loading || !stats" class="skeleton" style="height: 30px; width: 60%; margin-top: 6px;"></div>
      <template v-else>
        <div class="stat-value">{{ stats[card.key] }}{{ card.suffix || '' }}</div>
        <div class="stat-sub">{{ card.sub(stats) }}</div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.stats-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  margin-bottom: 24px;
}

.stat-card {
  padding: 16px 18px;
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
  width: 30px;
  height: 30px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 10px;
}

.tone-info .stat-icon { background: var(--info-soft); color: var(--info); }
.tone-accent .stat-icon { background: var(--accent-soft); color: var(--accent-dark); }
.tone-success .stat-icon { background: #e4f6ea; color: #1e8a4c; }
.tone-warn .stat-icon { background: var(--warn-soft); color: var(--warn); }
.tone-danger .stat-icon { background: var(--danger-soft); color: var(--danger); }

.stat-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: var(--text-primary);
  margin-top: 2px;
}

.stat-sub {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 2px;
}

@media (max-width: 1024px) {
  .stats-grid { grid-template-columns: repeat(3, 1fr); }
}
@media (max-width: 720px) {
  .stats-grid { grid-template-columns: 1fr 1fr; }
}
@media (max-width: 460px) {
  .stats-grid { grid-template-columns: 1fr; }
}
</style>
