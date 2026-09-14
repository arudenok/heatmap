<script setup>
import { onMounted, ref } from 'vue'
import api, { extractErrorMessage } from '../services/api'
import IconBase from './IconBase.vue'

const blocks = ref([])
const loading = ref(true)
const error = ref('')
const savingId = ref(null)
const drafts = ref({})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await api.get('/impact')
    blocks.value = data
    drafts.value = {}
    data.forEach((block) => block.rows.forEach((row) => (drafts.value[row.id] = row.value)))
  } catch (e) {
    error.value = extractErrorMessage(e, 'Не удалось загрузить метрики влияния')
  } finally {
    loading.value = false
  }
}

async function save(row) {
  const value = drafts.value[row.id]
  if (value === row.value) return
  savingId.value = row.id
  try {
    const { data } = await api.patch(`/admin/impact/rows/${row.id}`, { value })
    row.value = data.value
  } catch (e) {
    error.value = extractErrorMessage(e, 'Не удалось сохранить значение')
    drafts.value[row.id] = row.value
  } finally {
    savingId.value = null
  }
}

onMounted(load)
</script>

<template>
  <div class="panel admin-panel">
    <div class="admin-panel-header">
      <h3><IconBase name="gauge" :size="16" /> Метрики влияния (Habit / Process Standard)</h3>
      <button class="btn btn-ghost btn-sm" type="button" @click="load"><IconBase name="refresh" :size="13" /> Обновить</button>
    </div>

    <p class="admin-panel-hint">Значения строк отображаются на главном дашборде в разделе «Влияние на метрики».</p>

    <p v-if="error" class="error-text">{{ error }}</p>

    <div v-if="loading" class="skeleton" style="height: 60px;"></div>

    <div v-else class="impact-edit-grid">
      <div v-for="block in blocks" :key="block.code" class="impact-edit-block">
        <h4>{{ block.icon }} {{ block.title }}</h4>
        <div v-for="row in block.rows" :key="row.id" class="impact-edit-row">
          <label>{{ row.label }}</label>
          <div class="impact-edit-input">
            <input
              v-model="drafts[row.id]"
              class="input"
              type="text"
              @keyup.enter="save(row)"
              @blur="save(row)"
            />
            <IconBase v-if="savingId === row.id" name="refresh" :size="14" />
          </div>
        </div>
      </div>
    </div>
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
  margin-bottom: 6px;
}

.admin-panel-header h3 {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15.5px;
  margin: 0;
}

.admin-panel-hint {
  font-size: 13px;
  color: var(--text-muted);
  margin: 0 0 16px;
}

.impact-edit-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}

.impact-edit-block h4 {
  font-size: 14px;
  margin: 0 0 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border);
}

.impact-edit-row {
  display: grid;
  grid-template-columns: 1fr 140px;
  align-items: center;
  gap: 10px;
  padding: 6px 0;
}

.impact-edit-row label {
  font-size: 13px;
  color: var(--text-secondary);
}

.impact-edit-input {
  display: flex;
  align-items: center;
  gap: 6px;
}

.impact-edit-input .input {
  padding: 6px 10px;
  font-size: 13px;
}

@media (max-width: 800px) {
  .impact-edit-grid { grid-template-columns: 1fr; }
  .impact-edit-row { grid-template-columns: 1fr; align-items: flex-start; }
}
</style>
