<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import api from '../services/api'
import AppHeader from '../components/AppHeader.vue'
import AdminModerationPanel from '../components/AdminModerationPanel.vue'
import AdminToolsPanel from '../components/AdminToolsPanel.vue'
import AdminUsersPanel from '../components/AdminUsersPanel.vue'
import AdminImpactPanel from '../components/AdminImpactPanel.vue'
import IconBase from '../components/IconBase.vue'

const activeSection = ref('moderation')

const sections = [
  { key: 'moderation', label: 'Модерация', icon: 'inbox' },
  { key: 'tools', label: 'Инструменты', icon: 'layers' },
  { key: 'users', label: 'Пользователи', icon: 'users' },
  { key: 'impact', label: 'Метрики влияния', icon: 'gauge' }
]

// Счётчик на вкладке "Модерация" - сколько заявок ждут рассмотрения. Обновляется
// сам по себе (опрос раз в 20с + сразу после одобрения/отклонения в панели), чтобы
// админу не приходилось обновлять страницу вручную, увидев уведомление о новой заявке.
const pendingCount = ref(0)
const moderationPanelRef = ref(null)
let pollHandle = null

async function loadPendingCount() {
  try {
    const { data } = await api.get('/admin/tools/pending')
    pendingCount.value = data.length
  } catch {
    // счётчик не критичен - молча оставляем прежнее значение
  }
  // Пока открыта сама вкладка модерации - заодно обновляем в ней список заявок,
  // иначе новая заявка появится там только после ручного обновления страницы.
  if (activeSection.value === 'moderation') {
    moderationPanelRef.value?.refresh()
  }
}

function onModerationChanged(count) {
  pendingCount.value = count
}

onMounted(() => {
  loadPendingCount()
  pollHandle = setInterval(loadPendingCount, 20000)
})

onUnmounted(() => {
  clearInterval(pollHandle)
})
</script>

<template>
  <div>
    <AppHeader />

    <main class="container">
      <div class="page-heading">
        <div>
          <h1><IconBase name="shield" :size="20" /> Администрирование</h1>
          <p>Модерация заявок, управление пользователями и редактирование ключевых метрик</p>
        </div>
      </div>

      <div class="admin-tabs">
        <button
          v-for="section in sections"
          :key="section.key"
          type="button"
          class="admin-tab"
          :class="{ active: activeSection === section.key }"
          @click="activeSection = section.key"
        >
          <IconBase :name="section.icon" :size="14" /> {{ section.label }}
          <span v-if="section.key === 'moderation' && pendingCount" class="tab-badge">{{ pendingCount }}</span>
        </button>
      </div>

      <AdminModerationPanel
        v-if="activeSection === 'moderation'"
        ref="moderationPanelRef"
        @changed="onModerationChanged"
      />
      <AdminToolsPanel v-else-if="activeSection === 'tools'" />
      <AdminUsersPanel v-else-if="activeSection === 'users'" />
      <AdminImpactPanel v-else-if="activeSection === 'impact'" />
    </main>
  </div>
</template>

<style scoped>
.page-heading {
  margin-bottom: 22px;
}

.page-heading h1 {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 24px;
  margin: 0 0 4px;
}

.page-heading p {
  margin: 0;
  font-size: 13.5px;
  color: var(--text-secondary);
}

.admin-tabs {
  display: flex;
  gap: 4px;
  background: var(--surface);
  border: 1px solid var(--border);
  padding: 4px;
  border-radius: var(--radius-md);
  margin-bottom: 20px;
  width: fit-content;
}

.admin-tab {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 8px 16px;
  border-radius: var(--radius-sm);
  font-weight: 600;
  font-size: 13.5px;
  cursor: pointer;
  background: transparent;
  color: var(--text-secondary);
  border: none;
}
.admin-tab:hover:not(.active) {
  background: var(--surface-muted);
  color: var(--text-primary);
}
.admin-tab.active {
  background: var(--accent);
  color: #ffffff;
}

.tab-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--danger, #d64545);
  color: #fff;
  font-size: 10.5px;
  font-weight: 700;
  line-height: 1;
}
.admin-tab.active .tab-badge {
  background: rgba(255, 255, 255, 0.28);
  color: #fff;
}
</style>
