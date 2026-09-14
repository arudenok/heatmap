<script setup>
import { onMounted, ref } from 'vue'
import api, { extractErrorMessage } from '../services/api'
import IconBase from './IconBase.vue'

const pending = ref([])
const loading = ref(true)
const error = ref('')
const busyId = ref(null)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await api.get('/admin/tools/pending')
    pending.value = data
  } catch (e) {
    error.value = extractErrorMessage(e, 'Не удалось загрузить заявки на модерации')
  } finally {
    loading.value = false
  }
}

async function decide(tool, action) {
  busyId.value = tool.id
  try {
    await api.post(`/admin/tools/${tool.id}/${action}`)
    pending.value = pending.value.filter((t) => t.id !== tool.id)
  } catch (e) {
    error.value = extractErrorMessage(e, 'Не удалось обработать заявку')
  } finally {
    busyId.value = null
  }
}

onMounted(load)
defineExpose({ refresh: load })
</script>

<template>
  <div class="panel admin-panel">
    <div class="admin-panel-header">
      <h3><IconBase name="inbox" :size="16" /> Заявки на модерации <span class="badge badge-warn">{{ pending.length }}</span></h3>
      <button class="btn btn-ghost btn-sm" type="button" @click="load"><IconBase name="refresh" :size="13" /> Обновить</button>
    </div>

    <p v-if="error" class="error-text">{{ error }}</p>

    <div v-if="loading" class="skeleton" style="height: 60px;"></div>

    <div v-else-if="!pending.length" class="empty-state">
      <IconBase name="check" :size="24" /> Активных заявок нет
    </div>

    <div v-else class="moderation-list">
      <div v-for="tool in pending" :key="tool.id" class="moderation-row">
        <div class="moderation-info">
          <div class="moderation-title">{{ tool.name }}</div>
          <div class="moderation-desc">{{ tool.description }}</div>
          <div class="moderation-tags">
            <span v-for="r in tool.roles" :key="'r-' + r" class="tag">{{ r }}</span>
            <span class="tag">{{ tool.framework }}</span>
            <span v-for="s in tool.segments" :key="'s-' + s" class="tag">{{ s }}</span>
            <span class="tag">👤 {{ tool.ownerName }}</span>
          </div>
        </div>
        <div class="moderation-actions">
          <button
            class="btn btn-outline btn-sm"
            type="button"
            :disabled="busyId === tool.id"
            @click="decide(tool, 'reject')"
          >
            Отклонить
          </button>
          <button
            class="btn btn-primary btn-sm"
            type="button"
            :disabled="busyId === tool.id"
            @click="decide(tool, 'approve')"
          >
            Одобрить
          </button>
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
  margin-bottom: 16px;
}

.admin-panel-header h3 {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15.5px;
  margin: 0;
}

.moderation-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.moderation-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  flex-wrap: wrap;
}

.moderation-title {
  font-weight: 700;
  font-size: 14.5px;
}

.moderation-desc {
  font-size: 13px;
  color: var(--text-secondary);
  margin-top: 2px;
}

.moderation-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 8px;
}

.moderation-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.empty-state {
  display: flex;
  align-items: center;
  gap: 8px;
  justify-content: center;
  padding: 30px;
  color: var(--text-muted);
  font-size: 14px;
}
</style>
