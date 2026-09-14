<script setup>
import IconBase from './IconBase.vue'

defineProps({
  blocks: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false }
})

const valueClass = {
  DEFAULT: '',
  BLUE: 'value-blue',
  GREEN: 'value-green',
  GOLD: 'value-gold'
}
</script>

<template>
  <div class="impact-section panel">
    <h2><IconBase name="gauge" :size="18" /> Влияние на метрики <span class="impact-sub">· этапы Habit и Process Standard</span></h2>

    <div v-if="loading" class="impact-grid">
      <div class="skeleton" style="height: 220px;"></div>
      <div class="skeleton" style="height: 220px;"></div>
    </div>

    <div v-else class="impact-grid">
      <div v-for="block in blocks" :key="block.code" class="impact-block">
        <h3>
          <span>{{ block.icon }} {{ block.title }}</span>
          <span class="badge" :class="block.badgeStatus === 'ACHIEVED' ? 'badge-accent' : 'badge-danger'">
            {{ block.badgeText }}
          </span>
        </h3>

        <div
          v-for="row in block.rows"
          :key="row.id"
          class="impact-row"
          :class="{ 'impact-row-footer': row.isFooter }"
        >
          <span class="row-label">{{ row.label }}</span>
          <span class="row-value" :class="valueClass[row.colorVariant]">{{ row.value }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.impact-section {
  margin-top: 36px;
  padding: 26px 28px;
}

h2 {
  font-size: 18px;
  font-weight: 700;
  margin: 0 0 20px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.impact-sub {
  font-size: 13px;
  font-weight: 400;
  color: var(--text-muted);
}

.impact-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 28px;
}

.impact-block h3 {
  font-size: 14px;
  font-weight: 700;
  margin: 0 0 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.impact-row {
  display: flex;
  justify-content: space-between;
  padding: 7px 0;
  font-size: 13.5px;
  border-bottom: 1px solid var(--surface-muted);
}

.row-label {
  color: var(--text-secondary);
}

.row-value {
  font-weight: 700;
  color: var(--text-primary);
}
.value-blue { color: var(--info); }
.value-green { color: #1e8a4c; }
.value-gold { color: var(--warn); }

.impact-row-footer {
  border-bottom: none;
  margin-top: 4px;
  padding-top: 10px;
  border-top: 1px dashed var(--border);
}

@media (max-width: 900px) {
  .impact-grid { grid-template-columns: 1fr; }
}
</style>
