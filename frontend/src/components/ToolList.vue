<script setup>
import ToolCard from './ToolCard.vue'
import IconBase from './IconBase.vue'
import SelectDropdown from './SelectDropdown.vue'

defineProps({
  title: { type: String, required: true },
  tools: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  showStatus: { type: Boolean, default: false },
  // Фильтр "только с заметками" - виден только администратору (см. DashboardView),
  // сами заметки - внутренняя переписка администраторов по конкретному инструменту.
  showNotesFilter: { type: Boolean, default: false }
})

defineEmits(['view'])

const sort = defineModel('sort', { default: 'EFFICIENCY' })
const notesOnly = defineModel('notesOnly', { default: false })

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
      <div class="list-header-actions">
        <button
          v-if="showNotesFilter"
          type="button"
          class="btn btn-sm"
          :class="notesOnly ? 'btn-primary' : 'btn-ghost'"
          @click="notesOnly = !notesOnly"
        >
          <IconBase name="note" :size="13" /> Только с заметками
        </button>
        <div class="sort-control">
          <IconBase name="sort" :size="11" />
          <span class="sort-label">Сортировка:</span>
          <SelectDropdown v-model="sort" :options="sortOptions" compact />
        </div>
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

.list-header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
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
