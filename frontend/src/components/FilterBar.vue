<script setup>
import IconBase from './IconBase.vue'
import MultiSelectDropdown from './MultiSelectDropdown.vue'

const props = defineProps({
  filterOptions: { type: Object, default: () => ({ roles: [], frameworks: [], segments: [] }) },
  resultCount: { type: Number, default: 0 }
})

const emit = defineEmits(['reset'])

const role = defineModel('role', { default: () => [] })
const framework = defineModel('framework', { default: '' })
const segment = defineModel('segment', { default: () => [] })
const search = defineModel('search', { default: '' })
</script>

<template>
  <div class="filter-bar panel">
    <div class="filter-group">
      <label>Роль</label>
      <MultiSelectDropdown v-model="role" :options="filterOptions.roles" all-label="Все роли" />
    </div>

    <div class="filter-group">
      <label>Агентские фреймворки</label>
      <select v-model="framework" class="input">
        <option value="">Все</option>
        <option v-for="opt in filterOptions.frameworks" :key="opt" :value="opt">{{ opt }}</option>
      </select>
    </div>

    <div class="filter-group">
      <label>Сегмент</label>
      <MultiSelectDropdown v-model="segment" :options="filterOptions.segments" all-label="Для всех" />
    </div>

    <div class="filter-group filter-search">
      <label>Поиск</label>
      <input v-model="search" class="input" type="text" placeholder="Название, описание, автор…" />
    </div>

    <div class="filter-actions">
      <button class="btn btn-outline btn-sm" type="button" @click="emit('reset')">
        <IconBase name="refresh" :size="14" /> Сбросить
      </button>
      <span class="filter-count">{{ resultCount }} инструмент(а/ов)</span>
    </div>
  </div>
</template>

<style scoped>
.filter-bar {
  padding: 14px 18px;
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 14px 22px;
  margin-bottom: 20px;
}

.filter-group {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 150px;
}

.filter-search {
  flex: 1 1 200px;
}

.filter-group label {
  font-size: 11px;
  font-weight: 700;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.filter-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: auto;
}

/* У остальных полей в этой строке высота задаётся классом .input (~39px за
   счёт padding 9px). У кнопки "Сбросить" своя высота из .btn-sm (padding 6px) -
   в одной визуальной строке с выпадающими списками она выглядела заметно
   мельче. Выравниваем по высоте с полями фильтров. */
.filter-actions .btn {
  height: 39px;
}

.filter-count {
  font-size: 13px;
  color: var(--text-muted);
  white-space: nowrap;
}

@media (max-width: 900px) {
  .filter-bar { flex-direction: column; align-items: stretch; }
  /* В горизонтальной раскладке flex:1 1 200px растягивает поле поиска вширь,
     но в колоночной (column) раскладке та же настройка растягивает его
     ПО ВЫСОТЕ на всё свободное место, создавая большой пустой промежуток
     перед кнопкой "Сбросить". В колонке рост не нужен - высота и так auto. */
  .filter-search { flex: none; }
  .filter-actions { margin-left: 0; justify-content: space-between; }
}
</style>
