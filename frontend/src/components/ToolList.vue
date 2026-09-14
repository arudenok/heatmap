<script setup>
import ToolCard from './ToolCard.vue'
import IconBase from './IconBase.vue'

defineProps({
  title: { type: String, required: true },
  tools: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  showStatus: { type: Boolean, default: false }
})

defineEmits(['view'])

const sort = defineModel('sort', { default: 'EFFICIENCY' })

const sortOptions = [
  { value: 'EFFICIENCY', label: 'По эффективности' },
  { value: 'CREATED_AT', label: 'По дате добавления' },
  { value: 'UPDATED_AT', label: 'По дате обновления' },
  { value: 'VIEWS', label: 'По просмотрам' }
]
</script>

<template>
  <div class="tool-list-section">
    <div class="list-header">
      <h2>{{ title }}</h2>
      <div class="sort-control">
        <IconBase name="sort" :size="11" />
        <span class="sort-label">Сортировка:</span>
        <select v-model="sort" class="sort-select">
          <option v-for="opt in sortOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
        </select>
      </div>
    </div>

    <div v-if="loading" class="cards-grid">
      <div v-for="n in 3" :key="n" class="skeleton" style="height: 82px;"></div>
    </div>

    <div v-else-if="!tools.length" class="empty-state panel">
      <IconBase name="inbox" :size="28" />
      <p>Инструменты не найдены. Попробуйте изменить фильтры.</p>
    </div>

    <div v-else class="cards-grid">
      <ToolCard
        v-for="tool in tools"
        :key="tool.id"
        :tool="tool"
        :show-status="showStatus"
        @view="$emit('view', $event)"
      />
    </div>
  </div>
</template>

<style scoped>
.tool-list-section {
  margin-bottom: 8px;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
  gap: 12px;
  flex-wrap: wrap;
}

.list-header h2 {
  font-size: 17px;
  font-weight: 700;
  margin: 0;
}

.sort-control {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-muted);
}

.sort-label {
  font-size: 13px;
  white-space: nowrap;
}

.sort-select {
  appearance: none;
  -webkit-appearance: none;
  -moz-appearance: none;
  font-size: 13px;
  font-family: inherit;
  color: var(--text-secondary);
  background-color: var(--surface);
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='24' height='24' viewBox='0 0 24 24' fill='none' stroke='%23868aa0' stroke-width='2.4' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M6 9l6 6 6-6'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 7px center;
  background-size: 12px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: 4px 26px 4px 8px;
  cursor: pointer;
  outline: none;
  transition: border-color 0.12s ease;
}
.sort-select:hover {
  border-color: var(--border-strong);
}
.sort-select:focus {
  border-color: var(--accent);
}

.cards-grid {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  color: var(--text-muted);
  padding: 40px 20px;
}
</style>
